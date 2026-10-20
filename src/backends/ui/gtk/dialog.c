#include <gtk/gtk.h>

#include "src/shared/upcall.h"
#include "src/slots/ui.h"

/* A dialog is a GtkWindow over the app window. Java decides when it closes: close-request and a
 * tap outside become cancel results. */

extern GtkWidget *window; // the gtk host's app window

struct dialog_state {
	struct atl_ref *ref;
	GtkWidget *decor;                  // the decor view's wrapper
	GtkEventController *touch_outside; // on the app window while set
};

static struct dialog_state *get_state(struct ui_window *dialog)
{
	return g_object_get_data(G_OBJECT(dialog), "atl-dialog");
}

static gboolean on_close_request(GtkWindow *dialog, struct dialog_state *state)
{
	atl_upcall_dialog_result(state->ref, UI_DIALOG_CANCEL);
	return TRUE;
}

struct ui_window *ui_dialog_new(struct atl_ref *dialog_ref, struct ui_view *decor)
{
	GtkWidget *dialog = gtk_window_new();
	gtk_window_set_transient_for(GTK_WINDOW(dialog), GTK_WINDOW(window));

	struct dialog_state *state = g_new0(struct dialog_state, 1);
	state->ref = dialog_ref;
	// kept while an alert replaces it as the window's child
	state->decor = g_object_ref(gtk_widget_get_parent(GTK_WIDGET(decor)));
	g_object_set_data(G_OBJECT(dialog), "atl-dialog", state);

	gtk_window_set_child(GTK_WINDOW(dialog), state->decor);
	g_signal_connect(dialog, "close-request", G_CALLBACK(on_close_request), state);
	return (struct ui_window *)dialog;
}

static void on_button_clicked(GtkButton *button, struct dialog_state *state)
{
	atl_upcall_dialog_result(state->ref, GPOINTER_TO_INT(g_object_get_data(G_OBJECT(button), "which")));
}

static void on_row_activated(GtkListBox *list, GtkListBoxRow *row, struct dialog_state *state)
{
	atl_upcall_dialog_result(state->ref, gtk_list_box_row_get_index(row));
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
	if (alert->items) {
		GtkWidget *list = gtk_list_box_new();
		gtk_list_box_set_activate_on_single_click(GTK_LIST_BOX(list), TRUE);
		for (int i = 0; i < alert->n_items; i++)
			gtk_list_box_append(GTK_LIST_BOX(list), gtk_label_new(alert->items[i]));
		g_signal_connect(list, "row-activated", G_CALLBACK(on_row_activated), state);
		gtk_box_append(GTK_BOX(box), list);
	}
	GtkWidget *buttons = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 1);
	gtk_widget_set_halign(buttons, GTK_ALIGN_END);
	for (int i = 2; i >= 0; i--) { // Android's order: neutral, negative, positive
		if (!alert->buttons[i])
			continue;
		GtkWidget *button = gtk_button_new_with_label(alert->buttons[i]);
		g_object_set_data(G_OBJECT(button), "which", GINT_TO_POINTER(-1 - i));
		g_signal_connect(button, "clicked", G_CALLBACK(on_button_clicked), state);
		gtk_box_append(GTK_BOX(buttons), button);
	}
	gtk_box_append(GTK_BOX(box), buttons);
	return box;
}

void ui_dialog_show(struct ui_window *dialog, const struct ui_alert *alert)
{
	if (alert) {
		if (alert->title)
			gtk_window_set_title(GTK_WINDOW(dialog), alert->title);
		gtk_window_set_child(GTK_WINDOW(dialog), alert_content(get_state(dialog), alert));
	}
	gtk_window_present(GTK_WINDOW(dialog));
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
	remove_touch_outside(get_state(dialog));
	// gtk_window_close would emit close-request, which is a cancel
	gtk_widget_set_visible(GTK_WIDGET(dialog), FALSE);
}

static gboolean on_touch_outside(GtkEventControllerLegacy *controller, GdkEvent *event, struct ui_window *dialog)
{
	GdkEventType event_type = gdk_event_get_event_type(event);
	if ((event_type != GDK_BUTTON_PRESS && event_type != GDK_TOUCH_BEGIN) || !gtk_widget_get_visible(GTK_WIDGET(dialog)))
		return FALSE;
	atl_upcall_dialog_result(get_state(dialog)->ref, UI_DIALOG_CANCEL);
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
