#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/test_hooks.h"
#include "src/shared/upcall.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "../records/records.h"

/* Dialogs print what they show as "dialog: " lines; alerts take ATL_DIALOG_ANSWERS. */

static const char *const button_names[] = {"positive", "negative", "neutral"};

struct ui_dialog_state {
	struct atl_ref *ref;
	bool shown;
	bool answer_posted; // a listener's dismiss() and show() would post a second one
	struct ui_alert alert; // the shown one, as ui_dialog_set_button changed it; owns its strings
};

struct ui_window *ui_dialog_new(struct atl_ref *dialog_ref, struct ui_view *decor)
{
	struct ui_window *window = calloc(1, sizeof(*window));
	window->dialog = calloc(1, sizeof(*window->dialog));
	window->dialog->ref = dialog_ref;
	return window;
}

static char *dup(const char *chars)
{
	return chars ? strdup(chars) : NULL;
}

static void alert_clear(struct ui_alert *alert)
{
	free((char *)alert->title);
	free((char *)alert->message);
	for (int i = 0; i < alert->n_items; i++)
		free((char *)alert->items[i]);
	free((char **)alert->items);
	for (int i = 0; i < 3; i++)
		free((char *)alert->buttons[i]);
	memset(alert, 0, sizeof(*alert));
}

static void alert_copy(struct ui_alert *copy, const struct ui_alert *alert)
{
	*copy = *alert;
	copy->title = dup(alert->title);
	copy->message = dup(alert->message);
	if (alert->items) {
		char **items = calloc(alert->n_items, sizeof(*items));
		for (int i = 0; i < alert->n_items; i++)
			items[i] = dup(alert->items[i]);
		copy->items = (const char *const *)items;
	}
	for (int i = 0; i < 3; i++)
		copy->buttons[i] = dup(alert->buttons[i]);
}

static void post_answer(struct ui_window *dialog);

// after Dialog.show or the result's listener returned, as a tap would come
static void give_answer(void *data)
{
	struct ui_window *dialog = data;
	struct ui_dialog_state *state = dialog->dialog;
	state->answer_posted = false;
	int which;
	if (!state->shown || !atl_dialog_scripted(&state->alert, &which))
		return;
	if (atl_upcall_dialog_result(state->ref, which) && state->shown)
		post_answer(dialog);
}

static void post_answer(struct ui_window *dialog)
{
	if (!dialog->dialog->answer_posted)
		dialog->dialog->answer_posted = loop_main_post(give_answer, dialog);
}

static void print_labels(const char *name, const char *const *labels, int count)
{
	printf(" %s=", name);
	for (int i = 0; i < count; i++)
		printf("%s\"%s\"", i ? "," : "", labels[i]);
}

void ui_dialog_show(struct ui_window *dialog, const struct ui_alert *alert)
{
	if (!alert) {
		printf("dialog: show content view\n");
		fflush(stdout);
		return;
	}
	printf("dialog: show");
	if (alert->title)
		printf(" title=\"%s\"", alert->title);
	if (alert->message)
		printf(" message=\"%s\"", alert->message);
	if (alert->items)
		print_labels("items", alert->items, alert->n_items);
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
	alert_clear(&state->alert);
	alert_copy(&state->alert, alert);
	state->shown = true;
	post_answer(dialog);
}

void ui_dialog_set_button(struct ui_window *dialog, int which, const char *label, bool enabled)
{
	int i = -1 - which;
	struct ui_alert *alert = &dialog->dialog->alert;
	free((char *)alert->buttons[i]);
	alert->buttons[i] = dup(label);
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
	records_observations_orphan(dialog);
	atl_ref_free(dialog->dialog->ref);
	alert_clear(&dialog->dialog->alert);
	free(dialog->dialog);
	free(dialog);
}

void ui_dialog_set_cancel_on_touch_outside(struct ui_window *dialog, bool cancel) {}
