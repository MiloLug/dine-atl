#include <gtk/gtk.h>
#include <stdlib.h>

#include "src/shared/test_hooks.h"
#include "src/shared/upcall.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "gtk_ui.h"

/* A dialog is a GtkWindow over the app window. Java decides when it closes: close-request and a
 * tap outside become cancel results. ATL_DIALOG_ANSWERS answers alerts through their widgets, as
 * they are when the answer comes. */

extern GtkWidget *window; // the gtk host's app window

struct dialog_state {
	struct atl_ref *ref;
	GtkWindow *dialog;
	bool shown;
	bool answer_posted; // a listener's dismiss() and show() would post a second one
	GtkWidget *decor;                  // the decor view's wrapper
	GtkEventController *touch_outside; // on the app window while set
	GtkWidget *buttons[3];             // the shown alert's, as in ui_alert; hidden without a label
	GtkListBox *items;
};

static struct dialog_state *get_state(struct ui_window *dialog)
{
	return g_object_get_data(G_OBJECT(dialog), "atl-dialog");
}

static void post_answer(struct dialog_state *state);

static void result(struct dialog_state *state, int which)
{
	if (atl_upcall_dialog_result(state->ref, which) && state->shown)
		post_answer(state);
}

static gboolean on_close_request(GtkWindow *dialog, struct dialog_state *state)
{
	result(state, UI_DIALOG_CANCEL);
	return TRUE;
}

struct ui_window *ui_dialog_new(struct atl_ref *dialog_ref, struct ui_view *decor)
{
	GtkWidget *dialog = gtk_window_new();
	gtk_window_set_transient_for(GTK_WINDOW(dialog), GTK_WINDOW(window));

	struct dialog_state *state = g_new0(struct dialog_state, 1);
	state->ref = dialog_ref;
	state->dialog = GTK_WINDOW(dialog);
	// kept while an alert replaces it as the window's child
	state->decor = g_object_ref(gtk_widget_get_parent(GTK_WIDGET(decor)));
	g_object_set_data(G_OBJECT(dialog), "atl-dialog", state);

	gtk_window_set_child(GTK_WINDOW(dialog), state->decor);
	g_signal_connect(dialog, "close-request", G_CALLBACK(on_close_request), state);
	input_attach_window(GTK_WINDOW(dialog));
	return (struct ui_window *)dialog;
}

static void on_button_clicked(GtkButton *button, struct dialog_state *state)
{
	result(state, GPOINTER_TO_INT(g_object_get_data(G_OBJECT(button), "which")));
}

static void on_row_activated(GtkListBox *list, GtkListBoxRow *row, struct dialog_state *state)
{
	result(state, gtk_list_box_row_get_index(row));
}

static GtkWidget *alert_content(struct dialog_state *state, const struct ui_alert *alert)
{
	GtkWidget *box = gtk_box_new(GTK_ORIENTATION_VERTICAL, 1);
	if (alert->message) {
		GtkWidget *label = gtk_label_new(alert->message);
		gtk_label_set_wrap(GTK_LABEL(label), TRUE);
		gtk_label_set_max_width_chars(GTK_LABEL(label), 50);
		gtk_box_append(GTK_BOX(box), label);
	}
	state->items = NULL;
	if (alert->items) {
		GtkWidget *list = gtk_list_box_new();
		state->items = GTK_LIST_BOX(list);
		gtk_list_box_set_activate_on_single_click(GTK_LIST_BOX(list), TRUE);
		for (int i = 0; i < alert->n_items; i++)
			gtk_list_box_append(GTK_LIST_BOX(list), gtk_label_new(alert->items[i]));
		g_signal_connect(list, "row-activated", G_CALLBACK(on_row_activated), state);
		gtk_box_append(GTK_BOX(box), list);
	}
	GtkWidget *buttons = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 1);
	gtk_widget_set_halign(buttons, GTK_ALIGN_END);
	for (int i = 2; i >= 0; i--) { // Android's order: neutral, negative, positive
		GtkWidget *button = state->buttons[i] = gtk_button_new_with_label(alert->buttons[i] ?: "");
		gtk_widget_set_visible(button, alert->buttons[i] != NULL);
		gtk_widget_set_sensitive(button, !alert->disabled[i]);
		g_object_set_data(G_OBJECT(button), "which", GINT_TO_POINTER(-1 - i));
		g_signal_connect(button, "clicked", G_CALLBACK(on_button_clicked), state);
		gtk_box_append(GTK_BOX(buttons), button);
	}
	gtk_box_append(GTK_BOX(box), buttons);
	return box;
}

// the alert as its widgets show it now
static void get_alert(struct dialog_state *state, struct ui_alert *alert, const char **items, int max_items)
{
	*alert = (struct ui_alert){.title = gtk_window_get_title(state->dialog), .items = items};
	for (int i = 0; i < 3; i++) {
		if (gtk_widget_get_visible(state->buttons[i]))
			alert->buttons[i] = gtk_button_get_label(GTK_BUTTON(state->buttons[i]));
		alert->disabled[i] = !gtk_widget_get_sensitive(state->buttons[i]);
	}
	for (GtkListBoxRow *row; state->items && alert->n_items < max_items && (row = gtk_list_box_get_row_at_index(state->items, alert->n_items));)
		items[alert->n_items++] = gtk_label_get_text(GTK_LABEL(gtk_list_box_row_get_child(row)));
}

static void give_answer(void *data)
{
	struct dialog_state *state = data;
	state->answer_posted = false;
	if (!state->shown || !state->buttons[0])
		return;
	const char *items[64];
	struct ui_alert alert;
	get_alert(state, &alert, items, G_N_ELEMENTS(items));
	int which;
	if (!atl_dialog_scripted(&alert, &which))
		return;
	if (which == UI_DIALOG_CANCEL)
		gtk_window_close(state->dialog);
	else if (which < 0)
		gtk_widget_activate(state->buttons[-1 - which]);
	else
		gtk_widget_activate(GTK_WIDGET(gtk_list_box_get_row_at_index(state->items, which)));
}

// after Dialog.show or the result's listener returned, as a tap would come
static void post_answer(struct dialog_state *state)
{
	if (!state->answer_posted)
		state->answer_posted = loop_main_post(give_answer, state);
}

void ui_dialog_show(struct ui_window *dialog, const struct ui_alert *alert)
{
	struct dialog_state *state = get_state(dialog);
	if (alert) {
		if (alert->title)
			gtk_window_set_title(GTK_WINDOW(dialog), alert->title);
		gtk_window_set_child(GTK_WINDOW(dialog), alert_content(state, alert));
	}
	gtk_window_present(GTK_WINDOW(dialog));
	state->shown = true;
	if (alert)
		post_answer(state);
}

void ui_dialog_set_button(struct ui_window *dialog, int which, const char *label, bool enabled)
{
	GtkWidget *button = get_state(dialog)->buttons[-1 - which];
	if (!button) // a dialog with a content view
		return;
	gtk_button_set_label(GTK_BUTTON(button), label ?: "");
	gtk_widget_set_visible(button, label != NULL);
	gtk_widget_set_sensitive(button, enabled);
}

static void remove_touch_outside(struct dialog_state *state)
{
	if (!state->touch_outside)
		return;
	gtk_widget_remove_controller(window, state->touch_outside);
	state->touch_outside = NULL;
}

void ui_dialog_close(struct ui_window *dialog)
{
	get_state(dialog)->shown = false;
	remove_touch_outside(get_state(dialog));
	// gtk_window_close would emit close-request, which is a cancel
	gtk_widget_set_visible(GTK_WIDGET(dialog), FALSE);
}

void ui_dialog_free(struct ui_window *dialog)
{
	struct dialog_state *state = get_state(dialog);
	remove_touch_outside(state);
	gtk_window_destroy(GTK_WINDOW(dialog)); // before state goes: its signal handlers get it

	g_object_unref(state->decor);
	atl_ref_free(state->ref);
	g_free(state);
}

static gboolean on_touch_outside(GtkEventControllerLegacy *controller, GdkEvent *event, struct ui_window *dialog)
{
	GdkEventType event_type = gdk_event_get_event_type(event);
	if ((event_type != GDK_BUTTON_PRESS && event_type != GDK_TOUCH_BEGIN) || !gtk_widget_get_visible(GTK_WIDGET(dialog)))
		return FALSE;
	result(get_state(dialog), UI_DIALOG_CANCEL);
	return TRUE;
}

void ui_dialog_set_cancel_on_touch_outside(struct ui_window *dialog, bool cancel)
{
	struct dialog_state *state = get_state(dialog);
	remove_touch_outside(state);
	if (!cancel)
		return;

	GtkEventController *controller = GTK_EVENT_CONTROLLER(gtk_event_controller_legacy_new());
	/* capture phase on the toplevel runs before the view tree's own touch controllers */
	gtk_event_controller_set_propagation_phase(controller, GTK_PHASE_CAPTURE);
	g_signal_connect(controller, "event", G_CALLBACK(on_touch_outside), dialog);
	gtk_widget_add_controller(window, controller);
	state->touch_outside = controller;
}
