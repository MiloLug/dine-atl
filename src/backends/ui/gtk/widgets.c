#include <gtk/gtk.h>

#include "src/api-impl-jni/widgets/WrapperWidget.h"
#include "src/shared/input.h"
#include "src/slots/ui.h"

#include "gtk_ui.h"

/* ImageView.ScaleType.nativeInt */
#define SCALE_TYPE_FIT_XY        1
#define SCALE_TYPE_CENTER        5
#define SCALE_TYPE_CENTER_CROP   6
#define SCALE_TYPE_CENTER_INSIDE 7

struct ui_view *ui_image_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits)
{
	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *image = gtk_picture_new();
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), image);
	wrapper_widget_setup(WRAPPER_WIDGET(wrapper), view_ref, traits);
	return (struct ui_view *)image;
}

// the paintable has its own size
void ui_image_set_drawable(struct ui_view *view, struct gfx_drawable *drawable, int width, int height)
{
	gtk_picture_set_paintable(GTK_PICTURE(view), drawable ? GDK_PAINTABLE(drawable) : NULL);
}

void ui_image_set_scale_type(struct ui_view *view, int scale_type)
{
	GtkPicture *picture = GTK_PICTURE(view);
	/* TODO: somehow handle all the types */
	switch (scale_type) {
		case SCALE_TYPE_FIT_XY:
			gtk_picture_set_content_fit(picture, GTK_CONTENT_FIT_FILL);
			break;
		case SCALE_TYPE_CENTER:
			/* should probably let it overflow instead */
			gtk_picture_set_content_fit(picture, GTK_CONTENT_FIT_SCALE_DOWN);
			break;
		case SCALE_TYPE_CENTER_CROP:
			gtk_picture_set_content_fit(picture, GTK_CONTENT_FIT_COVER);
			break;
		case SCALE_TYPE_CENTER_INSIDE:
			gtk_picture_set_content_fit(picture, GTK_CONTENT_FIT_CONTAIN);
			break;
	}
}

/* A scroll node is a GtkScrolledWindow whose viewport holds the child's wrapper; GTK scrolls and
 * reports. A request the range can't take yet (before allocation) waits for the range to grow. */
struct scroll_request {
	int x, y;
	bool pending;
};

static void scroll_apply(GtkScrolledWindow *scrolled_window, struct scroll_request *request)
{
	GtkAdjustment *h = gtk_scrolled_window_get_hadjustment(scrolled_window);
	GtkAdjustment *v = gtk_scrolled_window_get_vadjustment(scrolled_window);
	gtk_adjustment_set_value(h, request->x);
	gtk_adjustment_set_value(v, request->y);
	request->pending = (int)gtk_adjustment_get_value(h) != request->x || (int)gtk_adjustment_get_value(v) != request->y;
}

static void on_scrolled(GtkAdjustment *adjustment, GtkScrolledWindow *scrolled_window)
{
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(GTK_WIDGET(scrolled_window)));
	atl_upcall_scrolled((struct atl_ref *)wrapper->jobj, gtk_adjustment_get_value(gtk_scrolled_window_get_hadjustment(scrolled_window)),
	                    gtk_adjustment_get_value(gtk_scrolled_window_get_vadjustment(scrolled_window)));
}

static void on_range_changed(GtkAdjustment *adjustment, GtkScrolledWindow *scrolled_window)
{
	struct scroll_request *request = g_object_get_data(G_OBJECT(scrolled_window), "atl-scroll-request");
	if (request->pending)
		scroll_apply(scrolled_window, request);
}

struct ui_view *ui_scroll_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits, bool horizontal)
{
	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *scrolled_window = gtk_scrolled_window_new();
	GtkWidget *viewport = gtk_viewport_new(NULL, NULL);
	if (horizontal) {
		gtk_scrolled_window_set_policy(GTK_SCROLLED_WINDOW(scrolled_window), GTK_POLICY_AUTOMATIC, GTK_POLICY_NEVER);
		gtk_scrollable_set_hscroll_policy(GTK_SCROLLABLE(viewport), GTK_SCROLL_NATURAL);
	} else {
		gtk_scrolled_window_set_policy(GTK_SCROLLED_WINDOW(scrolled_window), GTK_POLICY_NEVER, GTK_POLICY_AUTOMATIC);
		gtk_scrollable_set_vscroll_policy(GTK_SCROLLABLE(viewport), GTK_SCROLL_NATURAL);
	}
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), scrolled_window);
	gtk_scrolled_window_set_child(GTK_SCROLLED_WINDOW(scrolled_window), viewport);
	wrapper_widget_setup(WRAPPER_WIDGET(wrapper), view_ref, traits);
	gtk_widget_set_name(scrolled_window, class_name);
	g_object_set_data_full(G_OBJECT(scrolled_window), "atl-scroll-request", g_new0(struct scroll_request, 1), g_free);
	GtkAdjustment *adjustments[] = {gtk_scrolled_window_get_hadjustment(GTK_SCROLLED_WINDOW(scrolled_window)),
	                                gtk_scrolled_window_get_vadjustment(GTK_SCROLLED_WINDOW(scrolled_window))};
	for (int i = 0; i < 2; i++) {
		g_signal_connect(adjustments[i], "value-changed", G_CALLBACK(on_scrolled), scrolled_window);
		g_signal_connect(adjustments[i], "changed", G_CALLBACK(on_range_changed), scrolled_window);
	}
	input_take_native(gtk_scrolled_window_get_hscrollbar(GTK_SCROLLED_WINDOW(scrolled_window)));
	input_take_native(gtk_scrolled_window_get_vscrollbar(GTK_SCROLLED_WINDOW(scrolled_window)));
	return (struct ui_view *)scrolled_window;
}

// GTK animates nothing here; views other than scroll nodes keep the computeScroll shift
void ui_view_scroll_to(struct ui_view *view, int x, int y, bool animated)
{
	if (!GTK_IS_SCROLLED_WINDOW(view))
		return;
	struct scroll_request *request = g_object_get_data(G_OBJECT(view), "atl-scroll-request");
	request->x = x;
	request->y = y;
	scroll_apply(GTK_SCROLLED_WINDOW(view), request);
}

struct ui_view *ui_progress_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits)
{
	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *box = gtk_box_new(GTK_ORIENTATION_VERTICAL, 0);
	GtkWidget *progress_bar = gtk_progress_bar_new();
	GtkWidget *spinner = gtk_spinner_new();
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), box);
	gtk_box_append(GTK_BOX(box), progress_bar);
	gtk_box_append(GTK_BOX(box), spinner);
	gtk_widget_set_name(box, "ProgressBar");
	gtk_widget_set_visible(spinner, FALSE);
	wrapper_widget_setup(WRAPPER_WIDGET(wrapper), view_ref, traits);
	return (struct ui_view *)box;
}

void ui_progress_set_fraction(struct ui_view *view, float fraction)
{
	gtk_progress_bar_set_fraction(GTK_PROGRESS_BAR(gtk_widget_get_first_child(GTK_WIDGET(view))), fraction);
}

void ui_progress_set_indeterminate(struct ui_view *view, bool indeterminate)
{
	GtkWidget *box = GTK_WIDGET(view);
	GtkWidget *progress_bar = gtk_widget_get_first_child(box);
	GtkWidget *spinner = gtk_widget_get_last_child(box);
	gtk_spinner_set_spinning(GTK_SPINNER(spinner), indeterminate);
	gtk_widget_set_visible(progress_bar, !indeterminate);
	gtk_widget_set_visible(spinner, indeterminate);
}

/* A button's handle is the wrapper's child: a GtkButton or GtkToggleButton holding the text box, or
 * a box holding a GtkCheckButton or GtkSwitch beside the text box. Java dispatches the touches and
 * keys, so GTK's widgets neither take them nor the focus; they only show Java's state. */
struct ui_view *ui_button_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits, enum ui_button kind)
{
	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *handle;
	GtkWidget *control;
	switch (kind) {
		case UI_BUTTON_PUSH:
		case UI_BUTTON_TOGGLE:
			handle = control = kind == UI_BUTTON_PUSH ? gtk_button_new() : gtk_toggle_button_new();
			gtk_button_set_child(GTK_BUTTON(handle), text_box_new(handle, class_name, ""));
			// centered, where Android's button style puts its gravity
			gtk_label_set_xalign(text_get_label(handle), .5f);
			gtk_label_set_justify(text_get_label(handle), GTK_JUSTIFY_CENTER);
			break;
		case UI_BUTTON_CHECK:
		case UI_BUTTON_RADIO:
			handle = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 0);
			control = gtk_check_button_new();
			// a check button in a group draws as a radio; RadioGroup's Java keeps one checked
			if (kind == UI_BUTTON_RADIO) {
				GtkWidget *partner = g_object_ref_sink(gtk_check_button_new());
				gtk_check_button_set_group(GTK_CHECK_BUTTON(control), GTK_CHECK_BUTTON(partner));
				g_object_set_data_full(G_OBJECT(control), "atl-radio-partner", partner, g_object_unref);
			}
			gtk_box_append(GTK_BOX(handle), control);
			gtk_box_append(GTK_BOX(handle), text_box_new(handle, class_name, ""));
			break;
		case UI_BUTTON_SWITCH:
		default:
			handle = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 0);
			control = gtk_switch_new();
			gtk_widget_set_valign(control, GTK_ALIGN_CENTER);
			gtk_box_append(GTK_BOX(handle), text_box_new(handle, class_name, ""));
			gtk_box_append(GTK_BOX(handle), control);
			break;
	}
	gtk_widget_set_can_target(control, FALSE);
	gtk_widget_set_focusable(control, FALSE);
	g_object_set_data(G_OBJECT(handle), "atl-button-control", control);
	g_object_set_data(G_OBJECT(handle), "atl-button-kind", GINT_TO_POINTER(kind + 1));
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), handle);
	wrapper_widget_setup(WRAPPER_WIDGET(wrapper), view_ref, traits);
	return (struct ui_view *)handle;
}

void ui_button_set_state(struct ui_view *view, bool pressed, bool checked)
{
	GtkWidget *control = g_object_get_data(G_OBJECT(view), "atl-button-control");
	if (pressed)
		gtk_widget_set_state_flags(control, GTK_STATE_FLAG_ACTIVE, FALSE);
	else
		gtk_widget_unset_state_flags(control, GTK_STATE_FLAG_ACTIVE);
	if (GTK_IS_TOGGLE_BUTTON(control)) {
		gtk_toggle_button_set_active(GTK_TOGGLE_BUTTON(control), checked);
	} else if (GTK_IS_CHECK_BUTTON(control)) {
		gtk_check_button_set_active(GTK_CHECK_BUTTON(control), checked);
	} else if (GTK_IS_SWITCH(control)) {
		gtk_switch_set_active(GTK_SWITCH(control), checked);
	}
}

void button_set_has_background(GtkWidget *handle, bool has_background)
{
	enum ui_button kind = GPOINTER_TO_INT(g_object_get_data(G_OBJECT(handle), "atl-button-kind")) - 1;
	if (kind != UI_BUTTON_PUSH && kind != UI_BUTTON_TOGGLE)
		return;
	if (has_background)
		gtk_widget_add_css_class(handle, "ATL-no-border");
	else
		gtk_widget_remove_css_class(handle, "ATL-no-border");
}
