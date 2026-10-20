#include <stdio.h>
#include <stdlib.h>

#include "src/shared/upcall.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "../records/records.h"

/* Dialogs print what they show as "dialog: " lines; alerts take ATL_DIALOG_ANSWERS. */

struct ui_dialog_state {
	struct atl_ref *ref;
};

struct ui_window *ui_dialog_new(struct atl_ref *dialog_ref, struct ui_view *decor)
{
	struct ui_window *window = calloc(1, sizeof(*window));
	window->dialog = calloc(1, sizeof(*window->dialog));
	window->dialog->ref = dialog_ref;
	return window;
}

struct answer {
	struct atl_ref *ref;
	int which;
};

static void give_answer(void *data)
{
	struct answer *answer = data;
	atl_upcall_dialog_result(answer->ref, answer->which);
	free(answer);
}

static void print_labels(const char *name, const char *const *labels, int count)
{
	printf(" %s=", name);
	for (int i = 0; i < count; i++)
		printf("%s\"%s\"", i ? "," : "", labels[i]);
}

void ui_dialog_show(struct ui_window *dialog, const struct ui_alert *alert)
{
	static const char *const button_names[] = {"positive", "negative", "neutral"};

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
		if (alert->buttons[i])
			print_labels(button_names[i], &alert->buttons[i], 1);
	}
	printf("%s\n", alert->cancelable ? " cancelable" : "");
	fflush(stdout);

	int which;
	if (!atl_dialog_scripted(alert, &which))
		return;
	struct answer *answer = malloc(sizeof(*answer));
	*answer = (struct answer){dialog->dialog->ref, which};
	if (!loop_main_post(give_answer, answer)) // after Dialog.show returns, as a tap would come
		free(answer);
}

void ui_dialog_close(struct ui_window *dialog)
{
	printf("dialog: close\n");
	fflush(stdout);
}

void ui_dialog_set_cancel_on_touch_outside(struct ui_window *dialog, bool cancel) {}
