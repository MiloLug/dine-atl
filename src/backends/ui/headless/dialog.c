#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/test_hooks.h"
#include "src/shared/upcall.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "../records/records.h"
#include "input.h"

/* Alerts print what they show as "dialog: " lines and take ATL_DIALOG_ANSWERS. */

static const char *const button_names[] = {"positive", "negative", "neutral"};

struct ui_dialog_state {
	struct atl_ref *ref;
	struct ui_window *next; // every alert's, for headless_answer
	unsigned shown_at;      // the show count at its last show
	bool shown;
	bool answer_posted; // a listener's dismiss() and show() would post a second one
	struct ui_alert alert; // the shown one, as ui_dialog_set_button changed it; owns its strings
	struct ui_chrome chrome; // the last ui_dialog_chrome's
};

static struct ui_window *dialogs; // Android main threads: one per app, so shared under a lock
static pthread_mutex_t dialogs_lock = PTHREAD_MUTEX_INITIALIZER;
static unsigned show_count;

struct ui_window *ui_dialog_new(struct atl_app *app, struct atl_ref *dialog_ref)
{
	struct ui_window *window = calloc(1, sizeof(*window));
	window->app = app;
	window->dialog = calloc(1, sizeof(*window->dialog));
	window->dialog->ref = dialog_ref;
	pthread_mutex_lock(&dialogs_lock);
	window->dialog->next = dialogs;
	dialogs = window;
	pthread_mutex_unlock(&dialogs_lock);
	return window;
}

static void post_answer(struct ui_window *dialog);

// as a tap on a button or a row: a row's check moves first
static void answer(struct ui_window *dialog, int which)
{
	struct ui_dialog_state *state = dialog->dialog;
	struct ui_alert *alert = &state->alert;
	if (which >= 0 && alert->check != UI_ALERT_CHECK_NONE) {
		bool *checked = (bool *)alert->checked;
		bool now = alert->check == UI_ALERT_CHECK_MULTI ? !checked[which] : true;
		if (alert->check == UI_ALERT_CHECK_SINGLE)
			memset(checked, 0, alert->n_items * sizeof(*checked));
		checked[which] = now;
		printf("dialog: row %d %s\n", which, now ? "checked" : "unchecked");
		fflush(stdout);
	}
	if (atl_upcall_dialog_result(state->ref, which) && state->shown)
		post_answer(dialog);
}

// after Dialog.show or the result's listener returned, as a tap would come
static void give_answer(void *data)
{
	struct ui_window *dialog = data;
	struct ui_dialog_state *state = dialog->dialog;
	state->answer_posted = false;
	int which;
	if (!state->shown || !atl_dialog_scripted(&state->alert, &which))
		return;
	answer(dialog, which);
}

static void post_answer(struct ui_window *dialog)
{
	if (!dialog->dialog->answer_posted)
		dialog->dialog->answer_posted = loop_main_post(atl_app_loop(dialog->app), give_answer, dialog);
}

static void print_labels(const char *name, const char *const *labels, int count)
{
	printf(" %s=", name);
	for (int i = 0; i < count; i++)
		printf("%s\"%s\"", i ? "," : "", labels[i]);
}

static const char *const check_names[] = {"", "single", "multi"};
static const char *const progress_names[] = {"", "spinner", "bar"};

void ui_dialog_show(struct ui_window *dialog, const struct ui_alert *alert)
{
	printf("dialog: show");
	if (alert->panel)
		printf(" panel%s", alert->light ? " light" : "");
	if (alert->title)
		printf(" title=\"%s\"", alert->title);
	if (alert->message)
		printf(" message=\"%s\"", alert->message);
	if (alert->items)
		print_labels("items", alert->items, alert->n_items);
	if (alert->check != UI_ALERT_CHECK_NONE) {
		printf(" check=%s checked=", check_names[alert->check]);
		bool any = false;
		for (int i = 0; i < alert->n_items; i++) {
			if (alert->checked[i]) {
				printf("%s%d", any ? "," : "", i);
				any = true;
			}
		}
		if (!any)
			printf("none");
	}
	if (alert->progress != UI_ALERT_PROGRESS_NONE) {
		printf(" progress=%s", progress_names[alert->progress]);
		if (alert->indeterminate)
			printf(" indeterminate");
		else
			printf(" %d/%d", alert->value, alert->max);
	}
	for (int i = 0; i < 3; i++) {
		if (alert->buttons[i]) {
			print_labels(button_names[i], &alert->buttons[i], 1);
			if (alert->disabled[i])
				printf(" (disabled)");
		}
	}
	printf("%s\n", alert->cancelable ? " cancelable" : "");
	fflush(stdout);

	struct ui_dialog_state *state = dialog->dialog;
	records_alert_clear(&state->alert);
	records_alert_copy(&state->alert, alert);
	if (!state->shown) { // new content keeps its place
		pthread_mutex_lock(&dialogs_lock);
		state->shown_at = ++show_count;
		pthread_mutex_unlock(&dialogs_lock);
	}
	state->shown = true;
	post_answer(dialog);
}

struct posted_answer {
	struct atl_app *app;
	char *label;
};

// a button, then a row, by its label; false: none
static bool label_answer(const struct ui_alert *alert, const char *label, int *which)
{
	for (int i = 0; i < 3; i++) {
		if (alert->buttons[i] && !alert->disabled[i] && !strcmp(alert->buttons[i], label)) {
			*which = -1 - i;
			return true;
		}
	}
	for (int i = 0; i < alert->n_items; i++) {
		if (!strcmp(alert->items[i], label)) {
			*which = i;
			return true;
		}
	}
	return false;
}

static void answer_post(void *data)
{
	struct posted_answer *posted = data;
	struct ui_window *top = NULL;
	pthread_mutex_lock(&dialogs_lock);
	for (struct ui_window *dialog = dialogs; dialog; dialog = dialog->dialog->next) {
		struct ui_dialog_state *state = dialog->dialog;
		if (dialog->app == posted->app && state->shown && (!top || state->shown_at > top->dialog->shown_at))
			top = dialog;
	}
	pthread_mutex_unlock(&dialogs_lock);
	int which;
	if (top && label_answer(&top->dialog->alert, posted->label, &which)) {
		printf("answer: %s\n", posted->label);
		fflush(stdout);
		answer(top, which);
	} else {
		printf("answer: no %s\n", posted->label);
		fflush(stdout);
	}
	free(posted->label);
	free(posted);
}

void headless_answer(struct atl_app *app, const char *label)
{
	struct posted_answer *posted = malloc(sizeof(*posted));
	*posted = (struct posted_answer){app, strdup(label)};
	if (!loop_main_post(atl_app_loop(app), answer_post, posted)) {
		free(posted->label);
		free(posted);
	}
}

/* Fixed metrics, dp: an alert's width and margins, a line of title or message, a row, the button
 * bar, the progress; the panel stays clear of the screen's edges by the margin. */
#define CHROME_WIDTH    280
#define CHROME_MARGIN   24
#define CHROME_PADDING  16
#define CHROME_LINE     20
#define CHROME_ROW      44
#define CHROME_BUTTONS  44
#define CHROME_PROGRESS 24

void ui_dialog_chrome(struct ui_window *dialog, int avail_width, int avail_height, struct ui_chrome *chrome)
{
	const struct ui_alert *alert = &dialog->dialog->alert;
	float dp = atl_screen_get()->density_dpi / 160.f;
	int buttons = alert->buttons[0] || alert->buttons[1] || alert->buttons[2];
	int top = CHROME_PADDING + (alert->title ? CHROME_LINE : 0) + (alert->message ? CHROME_LINE : 0)
	          + (alert->progress ? CHROME_PROGRESS : 0) + (alert->check ? alert->n_items * CHROME_ROW : 0);
	int width = CHROME_WIDTH * dp, most = avail_width - 2 * CHROME_MARGIN * dp;
	*chrome = (struct ui_chrome){
		.width = width < most ? width : most,
		.max_height = avail_height - 2 * CHROME_MARGIN * dp,
		.left = CHROME_PADDING * dp,
		.top = top * dp,
		.right = CHROME_PADDING * dp,
		.bottom = (buttons ? CHROME_BUTTONS : CHROME_PADDING) * dp,
	};
	int room = chrome->max_height - chrome->bottom, cap = room - (alert->content ? room / 2 : 0);
	if (chrome->top > cap) // the rows past it scroll; Android content keeps half
		chrome->top = cap;
	struct ui_chrome *last = &dialog->dialog->chrome;
	if (memcmp(last, chrome, sizeof(*chrome))) {
		*last = *chrome;
		printf("dialog: chrome width=%d left=%d top=%d right=%d bottom=%d\n", chrome->width, chrome->left, chrome->top, chrome->right, chrome->bottom);
		fflush(stdout);
	}
}

void ui_dialog_set_progress(struct ui_window *dialog, int value, int max, bool indeterminate)
{
	struct ui_alert *alert = &dialog->dialog->alert;
	alert->value = value;
	alert->max = max;
	alert->indeterminate = indeterminate;
	if (indeterminate)
		printf("dialog: progress indeterminate\n");
	else
		printf("dialog: progress %d/%d\n", value, max);
	fflush(stdout);
}

void ui_dialog_set_button(struct ui_window *dialog, int which, const char *label, bool enabled)
{
	int i = -1 - which;
	struct ui_alert *alert = &dialog->dialog->alert;
	free((char *)alert->buttons[i]);
	alert->buttons[i] = label ? strdup(label) : NULL;
	alert->disabled[i] = !enabled;
	if (label)
		printf("dialog: button %s=\"%s\"%s\n", button_names[i], label, enabled ? "" : " disabled");
	else
		printf("dialog: button %s hidden\n", button_names[i]);
	fflush(stdout);
}

void ui_dialog_close(struct ui_window *dialog)
{
	dialog->dialog->shown = false;
	printf("dialog: close\n");
	fflush(stdout);
}

void ui_dialog_free(struct ui_window *dialog)
{
	pthread_mutex_lock(&dialogs_lock);
	for (struct ui_window **link = &dialogs; *link; link = &(*link)->dialog->next) {
		if (*link == dialog) {
			*link = dialog->dialog->next;
			break;
		}
	}
	pthread_mutex_unlock(&dialogs_lock);
	records_observations_orphan(dialog);
	atl_ref_free(dialog->dialog->ref);
	records_alert_clear(&dialog->dialog->alert);
	free(dialog->dialog);
	free(dialog);
}

void ui_dialog_set_cancel_on_touch_outside(struct ui_window *dialog, bool cancel) {}

void records_roots_changed(struct ui_window *window) {}
