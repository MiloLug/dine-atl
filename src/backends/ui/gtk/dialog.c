#include <gtk/gtk.h>
#include <stdlib.h>

#include "src/shared/alert.h"
#include "src/shared/test_hooks.h"
#include "src/shared/upcall.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "gtk_ui.h"

/* A plain alert is a GtkWindow over the app window; a panel is a box in the app window's overlay
 * around its window's root (window.c). Java decides when it closes: close-request and a tap outside
 * become cancel results. ATL_DIALOG_ANSWERS answers the alert as it is when the answer comes. */

extern GtkWidget *window; // the gtk host's app window

struct dialog_state {
	struct atl_app *app;
	struct atl_ref *ref;
	GtkWindow *dialog;
	bool shown;
	bool answer_posted; // a listener's dismiss() and show() would post a second one
	struct ui_alert alert; // the shown one
	GtkEventController *touch_outside; // on the app window while set
	GtkWidget *buttons[3];             // the shown alert's, as in ui_alert; hidden without a label
	GtkListBox *items;
	// a panel's: the box, its parts above and below the root, its rows' checks
	GtkWidget *panel, *header, *footer;
	GtkScrolledWindow *scroller; // the message or the rows, scrolling when the window can't hold them
	GtkWidget *scrolled;         // the scroller's message or rows
	GtkWidget **checks;
	GtkWidget *progress;
	enum ui_alert_progress progress_style;
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

struct ui_window *ui_dialog_new(struct atl_app *app, struct atl_ref *dialog_ref)
{
	GtkWidget *dialog = gtk_window_new();
	gtk_window_set_transient_for(GTK_WINDOW(dialog), GTK_WINDOW(window));

	struct dialog_state *state = g_new0(struct dialog_state, 1);
	state->app = app;
	state->ref = dialog_ref;
	state->dialog = GTK_WINDOW(dialog);
	g_object_set_data(G_OBJECT(dialog), "atl-dialog", state);

	g_signal_connect(dialog, "close-request", G_CALLBACK(on_close_request), state);
	input_attach_window(GTK_WINDOW(dialog));
	return (struct ui_window *)dialog;
}

static void on_button_clicked(GtkButton *button, struct dialog_state *state)
{
	result(state, GPOINTER_TO_INT(g_object_get_data(G_OBJECT(button), "which")));
}

// a row's check moves, then Java hears it
static void on_row_activated(GtkListBox *list, GtkListBoxRow *row, struct dialog_state *state)
{
	int index = gtk_list_box_row_get_index(row);
	if (state->checks && index < state->alert.n_items)
		gtk_check_button_set_active(GTK_CHECK_BUTTON(state->checks[index]), atl_alert_tap_row(&state->alert, index));
	result(state, index);
}

static GtkWidget *rows_new(struct dialog_state *state, const struct ui_alert *alert)
{
	GtkWidget *list = gtk_list_box_new();
	state->items = GTK_LIST_BOX(list);
	gtk_list_box_set_activate_on_single_click(GTK_LIST_BOX(list), TRUE);
	g_free(state->checks);
	state->checks = alert->check != UI_ALERT_CHECK_NONE ? g_new0(GtkWidget *, alert->n_items ? alert->n_items : 1) : NULL;
	for (int i = 0; i < alert->n_items; i++) {
		if (alert->check == UI_ALERT_CHECK_NONE) {
			gtk_list_box_append(GTK_LIST_BOX(list), gtk_label_new(alert->items[i]));
			continue;
		}
		GtkWidget *check = state->checks[i] = gtk_check_button_new_with_label(alert->items[i]);
		gtk_check_button_set_active(GTK_CHECK_BUTTON(check), alert->checked[i]);
		gtk_widget_set_can_target(check, FALSE); // the row's activation moves it
		if (alert->check == UI_ALERT_CHECK_SINGLE && i)
			gtk_check_button_set_group(GTK_CHECK_BUTTON(check), GTK_CHECK_BUTTON(state->checks[0]));
		gtk_list_box_append(GTK_LIST_BOX(list), check);
	}
	g_signal_connect(list, "row-activated", G_CALLBACK(on_row_activated), state);
	return list;
}

static GtkWidget *buttons_new(struct dialog_state *state, const struct ui_alert *alert)
{
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
	return buttons;
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
	if (alert->items)
		gtk_box_append(GTK_BOX(box), rows_new(state, alert));
	gtk_box_append(GTK_BOX(box), buttons_new(state, alert));
	return box;
}

#define PANEL_PADDING 16 // px around the panel's parts
#define PANEL_WIDTH   400
#define PANEL_MARGIN  24

// an indeterminate bar spins: a pulse would move once
static GtkWidget *progress_new(enum ui_alert_progress style, bool indeterminate)
{
	if (style == UI_ALERT_PROGRESS_BAR && !indeterminate)
		return gtk_progress_bar_new();
	GtkWidget *spinner = gtk_spinner_new();
	gtk_spinner_start(GTK_SPINNER(spinner));
	return spinner;
}

static void set_progress(struct dialog_state *state, int value, int max, bool indeterminate)
{
	bool spins = state->progress_style == UI_ALERT_PROGRESS_SPINNER || indeterminate;
	if (spins != GTK_IS_SPINNER(state->progress)) {
		GtkWidget *old = state->progress;
		state->progress = progress_new(state->progress_style, indeterminate);
		gtk_box_insert_child_after(GTK_BOX(state->header), state->progress, old);
		gtk_box_remove(GTK_BOX(state->header), old);
	}
	if (!spins)
		gtk_progress_bar_set_fraction(GTK_PROGRESS_BAR(state->progress), max > 0 ? (double)value / max : 0);
}

/* The panel: the title, the message or the rows, and the progress above the root, the buttons below
 * it; the root's wrapper lies over the space between (window.c places both). */
static void panel_fill(struct dialog_state *state, const struct ui_alert *alert)
{
	if (!state->panel) {
		state->panel = g_object_ref_sink(gtk_box_new(GTK_ORIENTATION_VERTICAL, 0));
		gtk_widget_add_css_class(state->panel, "ATL-panel");
		input_take_native(state->panel); // its buttons and rows are GTK's
	}
	// the Android theme's lightness, not the desktop's: the content draws in the theme's colors
	gtk_widget_remove_css_class(state->panel, alert->light ? "ATL-panel-dark" : "ATL-panel-light");
	gtk_widget_add_css_class(state->panel, alert->light ? "ATL-panel-light" : "ATL-panel-dark");
	for (GtkWidget *child; (child = gtk_widget_get_first_child(state->panel));)
		gtk_box_remove(GTK_BOX(state->panel), child);
	GtkWidget *header = state->header = gtk_box_new(GTK_ORIENTATION_VERTICAL, 6);
	gtk_widget_set_margin_start(header, PANEL_PADDING);
	gtk_widget_set_margin_end(header, PANEL_PADDING);
	gtk_widget_set_margin_top(header, PANEL_PADDING);
	if (alert->title) {
		GtkWidget *title = gtk_label_new(alert->title);
		gtk_widget_add_css_class(title, "ATL-panel-title");
		gtk_label_set_wrap(GTK_LABEL(title), TRUE);
		gtk_label_set_lines(GTK_LABEL(title), 2); // AOSP's DialogTitle
		gtk_label_set_ellipsize(GTK_LABEL(title), PANGO_ELLIPSIZE_END);
		gtk_box_append(GTK_BOX(header), title);
	}
	state->items = NULL;
	state->scroller = NULL;
	state->scrolled = NULL;
	if (alert->message) {
		state->scrolled = gtk_label_new(alert->message);
		gtk_label_set_wrap(GTK_LABEL(state->scrolled), TRUE);
		gtk_label_set_xalign(GTK_LABEL(state->scrolled), 0);
	} else if (alert->items) {
		state->scrolled = rows_new(state, alert);
	}
	if (state->scrolled) {
		GtkWidget *scroller = gtk_scrolled_window_new();
		state->scroller = GTK_SCROLLED_WINDOW(scroller);
		gtk_scrolled_window_set_policy(state->scroller, GTK_POLICY_NEVER, GTK_POLICY_AUTOMATIC);
		gtk_scrolled_window_set_propagate_natural_height(state->scroller, TRUE);
		gtk_scrolled_window_set_child(state->scroller, state->scrolled);
		gtk_box_append(GTK_BOX(header), scroller);
	}
	state->progress = NULL;
	state->progress_style = alert->progress;
	if (alert->progress != UI_ALERT_PROGRESS_NONE)
		state->progress = progress_new(alert->progress, alert->indeterminate);
	if (state->progress) {
		gtk_box_append(GTK_BOX(header), state->progress);
		set_progress(state, alert->value, alert->max, alert->indeterminate);
	}
	GtkWidget *middle = gtk_box_new(GTK_ORIENTATION_VERTICAL, 0);
	gtk_widget_set_vexpand(middle, TRUE);
	GtkWidget *footer = state->footer = buttons_new(state, alert);
	gtk_widget_set_margin_start(footer, PANEL_PADDING);
	gtk_widget_set_margin_end(footer, PANEL_PADDING);
	gtk_widget_set_margin_bottom(footer, PANEL_PADDING);
	gtk_box_append(GTK_BOX(state->panel), header);
	gtk_box_append(GTK_BOX(state->panel), middle);
	gtk_box_append(GTK_BOX(state->panel), footer);
}

GtkWidget *dialog_panel(struct ui_window *dialog)
{
	return get_state(dialog)->panel;
}

void ui_dialog_chrome(struct ui_window *dialog, int avail_width, int avail_height, struct ui_chrome *chrome)
{
	struct dialog_state *state = get_state(dialog);
	int width = MIN(PANEL_WIDTH, avail_width - 2 * PANEL_MARGIN);
	int header = 0, footer = 0, max_height = avail_height - 2 * PANEL_MARGIN;
	if (state->panel) {
		gtk_widget_measure(state->footer, GTK_ORIENTATION_VERTICAL, width, NULL, &footer, NULL, NULL);
		gtk_widget_measure(state->header, GTK_ORIENTATION_VERTICAL, width, NULL, &header, NULL, NULL);
		if (state->scroller) { // the message or the rows get what the title and the progress leave
			int cap = atl_alert_header_cap(max_height - 2 * PANEL_PADDING, footer, state->alert.content);
			int shown, full;
			gtk_widget_measure(GTK_WIDGET(state->scroller), GTK_ORIENTATION_VERTICAL, width, NULL, &shown, NULL, NULL);
			gtk_widget_measure(state->scrolled, GTK_ORIENTATION_VERTICAL, width - 2 * PANEL_PADDING, NULL, &full, NULL, NULL);
			int rest = header - shown, fits = MIN(full, MAX(cap - rest, 0));
			int max = fits < full ? fits : -1;
			if (gtk_scrolled_window_get_max_content_height(state->scroller) != max) // a change queues a resize
				gtk_scrolled_window_set_max_content_height(state->scroller, max);
			header = rest + fits;
		}
	}
	*chrome = (struct ui_chrome){
		.width = width,
		.max_height = max_height,
		.left = PANEL_PADDING,
		.top = header + PANEL_PADDING,
		.right = PANEL_PADDING,
		.bottom = footer + PANEL_PADDING,
	};
}

void ui_dialog_set_progress(struct ui_window *dialog, int value, int max, bool indeterminate)
{
	struct dialog_state *state = get_state(dialog);
	atl_alert_set_progress(&state->alert, value, max, indeterminate);
	if (state->progress)
		set_progress(state, value, max, indeterminate);
}

static void give_answer(void *data)
{
	struct dialog_state *state = data;
	state->answer_posted = false;
	if (!state->shown || !state->buttons[0])
		return;
	int which;
	if (!atl_dialog_scripted(&state->alert, &which))
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
		state->answer_posted = loop_main_post(atl_app_loop(state->app), give_answer, state);
}

void ui_dialog_show(struct ui_window *dialog, const struct ui_alert *alert)
{
	struct dialog_state *state = get_state(dialog);
	atl_alert_clear(&state->alert);
	atl_alert_copy(&state->alert, alert);
	if (alert->panel) { // window.c shows it with its root
		panel_fill(state, alert);
		state->shown = true;
		post_answer(state);
		return;
	}
	if (alert->title)
		gtk_window_set_title(GTK_WINDOW(dialog), alert->title);
	gtk_window_set_child(GTK_WINDOW(dialog), alert_content(state, alert));
	gtk_window_present(GTK_WINDOW(dialog));
	state->shown = true;
	post_answer(state);
}

void ui_dialog_set_button(struct ui_window *dialog, int which, const char *label, bool enabled)
{
	atl_alert_set_button(&get_state(dialog)->alert, which, label, enabled);
	GtkWidget *button = get_state(dialog)->buttons[-1 - which];
	if (!button) // not shown yet
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
	if (state->panel) {
		input_return_native(state->panel);
		g_object_unref(state->panel);
	}

	atl_ref_free(state->ref);
	atl_alert_clear(&state->alert);
	g_free(state->checks);
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
