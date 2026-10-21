#include <gtk/gtk.h>

#include "src/api-impl-jni/gtk_util.h"
#include "src/api-impl-jni/views/AndroidLayout.h"
#include "src/api-impl-jni/widgets/WrapperWidget.h"
#include "src/slots/ui.h"

/* A view handle is the inner widget of a WrapperWidget: a JavaWidget laid out by AndroidLayout, or
 * a toolkit widget. */

#define GRAVITY_TOP               (1 << 5) //0x30
#define GRAVITY_BOTTOM            (1 << 6) //0x50
#define GRAVITY_LEFT              (1 << 1) //0x3
#define GRAVITY_RIGHT             (1 << 2) //0x5

#define GRAVITY_CENTER_VERTICAL   0x10
#define GRAVITY_CENTER_HORIZONTAL 0x01

#define GRAVITY_CENTER            (GRAVITY_CENTER_VERTICAL | GRAVITY_CENTER_HORIZONTAL)

#define MATCH_PARENT              (-1)

/** JavaWidget:
 * Minimal gtk widget class which does nothing.
 * Drawing will be overwritten by WrapperWidget.
 * If it holds children, they will be laid out by AndroidLayout
 */
struct _JavaWidget {
	GtkWidget parent_instance;
};
G_DECLARE_FINAL_TYPE(JavaWidget, java_widget, JAVA, WIDGET, GtkWidget)
static void java_widget_dispose(GObject *java_widget)
{
	GtkWidget *widget = GTK_WIDGET(java_widget);
	GtkWidget *child = gtk_widget_get_first_child(widget);
	while (child) {
		GtkWidget *_child = gtk_widget_get_next_sibling(child);
		gtk_widget_unparent(child);
		child = _child;
	}
}
static void java_widget_init(JavaWidget *java_widget) {}
static void java_widget_class_init(JavaWidgetClass *class)
{
	G_OBJECT_CLASS(class)->dispose = java_widget_dispose;
}
G_DEFINE_TYPE(JavaWidget, java_widget, GTK_TYPE_WIDGET)

struct ui_view *ui_view_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits)
{
	WrapperWidget *wrapper = g_object_ref(WRAPPER_WIDGET(wrapper_widget_new()));
	GtkWidget *widget = g_object_new(java_widget_get_type(), NULL);
	gtk_widget_set_layout_manager(widget, android_layout_new(view_ref));
	wrapper_widget_set_child(wrapper, widget);
	wrapper_widget_setup(wrapper, view_ref, traits);
	gtk_widget_set_name(widget, class_name);
	view_attach_motion_controllers(wrapper, traits);
	return (struct ui_view *)widget;
}

void ui_view_add_child(struct ui_view *parent_view, struct ui_view *child, int index)
{
	GtkWidget *parent = GTK_WIDGET(parent_view);
	if (GTK_IS_SCROLLED_WINDOW(parent)) { // a scroll node: its viewport holds the one child
		gtk_viewport_set_child(GTK_VIEWPORT(gtk_scrolled_window_get_child(GTK_SCROLLED_WINDOW(parent))), gtk_widget_get_parent(GTK_WIDGET(child)));
		return;
	}
	GtkWidget *iter = gtk_widget_get_first_child(parent);
	for (int i = 0; i < index; i++) {
		iter = gtk_widget_get_next_sibling(iter);
		if (iter == NULL)
			break;
	}

	gtk_widget_insert_before(gtk_widget_get_parent(GTK_WIDGET(child)), parent, iter);
}

void ui_view_remove_child(struct ui_view *parent, struct ui_view *child)
{
	GtkWidget *wrapper = gtk_widget_get_parent(GTK_WIDGET(child));
	if (GTK_IS_SCROLLED_WINDOW(parent)) {
		GtkViewport *viewport = GTK_VIEWPORT(gtk_scrolled_window_get_child(GTK_SCROLLED_WINDOW(parent)));
		if (gtk_viewport_get_child(viewport) == wrapper)
			gtk_viewport_set_child(viewport, NULL);
		return;
	}
	if (gtk_widget_get_parent(wrapper) == GTK_WIDGET(parent))
		gtk_widget_unparent(wrapper);
}

// GTK focus follows Java's into a widget that edits text, and leaves one Java's left
void ui_view_set_focused(struct ui_view *view, bool focused)
{
	GtkWidget *widget = GTK_WIDGET(view);
	GtkRoot *root = gtk_widget_get_root(widget);
	if (!root)
		return;
	if (focused && gtk_widget_get_focusable(widget)) {
		gtk_widget_grab_focus(widget);
		return;
	}
	GtkWidget *focus = gtk_root_get_focus(root);
	if (!focused && focus && (focus == widget || gtk_widget_is_ancestor(focus, widget)))
		gtk_root_set_focus(root, NULL);
}

// drops the ref ui_view_new took for Java; a parent holds its own
void ui_view_free(struct ui_view *view)
{
	g_object_unref(gtk_widget_get_parent(GTK_WIDGET(view)));
}

void ui_view_set_layout_params(struct ui_view *view, const struct ui_layout_params *params)
{
	GtkWidget *widget = gtk_widget_get_parent(GTK_WIDGET(view));
	int width = params->width;
	int height = params->height;
	int gravity = params->gravity;
	float weight = params->weight;

	GtkAlign halign = GTK_ALIGN_FILL;
	GtkAlign valign = GTK_ALIGN_FILL;
	gboolean hexpand = FALSE;
	gboolean vexpand = FALSE;

	if (gravity != -1) {
		if (gravity & GRAVITY_BOTTOM)
			valign = GTK_ALIGN_END;
		else if (gravity & GRAVITY_TOP)
			valign = GTK_ALIGN_START;
		else
			valign = GTK_ALIGN_FILL;

		if (gravity & GRAVITY_RIGHT)
			halign = GTK_ALIGN_END;
		else if (gravity & GRAVITY_LEFT)
			halign = GTK_ALIGN_START;
		else
			halign = GTK_ALIGN_FILL;

		if (gravity == GRAVITY_CENTER) {
			valign = GTK_ALIGN_CENTER; // GTK_ALIGN_CENTER doesn't seem to be the right one?
			halign = GTK_ALIGN_CENTER; // ditto (GTK_ALIGN_CENTER)
			hexpand = TRUE;            // haxx or not?
			vexpand = TRUE;            // seems to be the deciding factor for whether to expand, guess I should try on android
		}
	}

	if (weight > 0.f) {
		hexpand = TRUE;
		vexpand = TRUE;
	}

	if (width == MATCH_PARENT) {
		hexpand = true;
		halign = GTK_ALIGN_FILL;
	}
	if (height == MATCH_PARENT) {
		vexpand = true;
		valign = GTK_ALIGN_FILL;
	}

	gtk_widget_set_hexpand(widget, hexpand);
	gtk_widget_set_vexpand(widget, vexpand);
	gtk_widget_set_halign(widget, halign);
	gtk_widget_set_valign(widget, valign);

	if (width > 0)
		g_object_set(G_OBJECT(widget), "width-request", width, NULL);
	if (height > 0)
		g_object_set(G_OBJECT(widget), "height-request", height, NULL);

	GtkWidget *parent = gtk_widget_get_parent(widget);
	// if parent is Java widget, it will handle the margins by itself
	if (parent && !ATL_IS_ANDROID_LAYOUT(gtk_widget_get_layout_manager(parent))) {
		gtk_widget_set_margin_start(widget, params->margin_left);
		gtk_widget_set_margin_top(widget, params->margin_top);
		gtk_widget_set_margin_end(widget, params->margin_right);
		gtk_widget_set_margin_bottom(widget, params->margin_bottom);
	}

	GtkLayoutManager *layout_manager = gtk_widget_get_layout_manager(WRAPPER_WIDGET(widget)->child);
	if (ATL_IS_ANDROID_LAYOUT(layout_manager))
		android_layout_set_params(ATL_ANDROID_LAYOUT(layout_manager), width, height);

	wrapper_widget_set_layout_params(WRAPPER_WIDGET(widget), width, height);

	atl_ensure_widget_snapshotability(widget);
}

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
void ui_view_set_padding(struct ui_view *view, int left, int top, int right, int bottom)
{
	GtkWidget *widget = GTK_WIDGET(view);
	if (ATL_IS_ANDROID_LAYOUT(gtk_widget_get_layout_manager(widget))) {
		return;
	}

	GtkStyleContext *style_context = gtk_widget_get_style_context(widget);
	GtkCssProvider *old_provider = g_object_get_data(G_OBJECT(widget), "padding_style_provider");
	if (old_provider)
		gtk_style_context_remove_provider(style_context, GTK_STYLE_PROVIDER(old_provider));
	GtkCssProvider *css_provider = gtk_css_provider_new();

	char *css_string = g_markup_printf_escaped("*{ padding-left: %dpx; padding-top: %dpx; padding-right: %dpx; padding-bottom: %dpx; }", left, top, right, bottom);
	gtk_css_provider_load_from_string(css_provider, css_string);
	g_free(css_string);

	gtk_style_context_add_provider(style_context, GTK_STYLE_PROVIDER(css_provider), GTK_STYLE_PROVIDER_PRIORITY_APPLICATION);
	g_object_set_data(G_OBJECT(widget), "padding_style_provider", css_provider);
}

void ui_view_measure(struct ui_view *view, int width_spec, int height_spec, int *out_width, int *out_height)
{
	int width = -1;
	int height = -1;
	GtkWidget *widget = gtk_widget_get_parent(GTK_WIDGET(view));
	int width_spec_size = width_spec & ~MEASURE_SPEC_MASK;
	int height_spec_size = height_spec & ~MEASURE_SPEC_MASK;
	int width_spec_mode = width_spec & MEASURE_SPEC_MASK;
	int height_spec_mode = height_spec & MEASURE_SPEC_MASK;
	GtkSizeRequestMode request_mode;

	if (width_spec_mode == MEASURE_SPEC_EXACTLY) {
		width = width_spec_size;
		request_mode = GTK_SIZE_REQUEST_HEIGHT_FOR_WIDTH;
	}
	if (height_spec_mode == MEASURE_SPEC_EXACTLY) {
		height = height_spec_size;
		request_mode = GTK_SIZE_REQUEST_WIDTH_FOR_HEIGHT;
	}
	if (width == -1 && height == -1)
		request_mode = gtk_widget_get_request_mode(widget);
	if (width == -1 && request_mode == GTK_SIZE_REQUEST_HEIGHT_FOR_WIDTH) {
		gtk_widget_measure(widget, GTK_ORIENTATION_HORIZONTAL, height, NULL, &width, NULL, NULL);
		if (width_spec_mode == MEASURE_SPEC_AT_MOST && width > width_spec_size)
			width = width_spec_size;
	}
	if (height == -1) {
		gtk_widget_measure(widget, GTK_ORIENTATION_VERTICAL, width, NULL, &height, NULL, NULL);
		if (height_spec_mode == MEASURE_SPEC_AT_MOST && height > height_spec_size)
			height = height_spec_size;
	}
	if (width == -1) {
		gtk_widget_measure(widget, GTK_ORIENTATION_HORIZONTAL, height, NULL, &width, NULL, NULL);
		if (width_spec_mode == MEASURE_SPEC_AT_MOST && width > width_spec_size)
			width = width_spec_size;
	}

	*out_width = width;
	*out_height = height;
}

void ui_view_layout(struct ui_view *view, int l, int t, int r, int b, float translation_x, float translation_y)
{
	GtkWidget *widget = gtk_widget_get_parent(GTK_WIDGET(view));

	GtkAllocation allocation = {
		.x = l,
		.y = t,
	};
	int width = r - l;
	int height = b - t;
	WrapperWidget *wrapper = WRAPPER_WIDGET(widget);
	if (wrapper->real_width != width || wrapper->real_height != height) {
		wrapper->real_width = width;
		wrapper->real_height = height;
		if (!wrapper->needs_allocation)
			gtk_widget_queue_allocate(widget);
	}
	if (wrapper->needs_allocation) {
		allocation.width = width;
		allocation.height = height;
	}

	allocation.x += translation_x;
	allocation.y += translation_y;

	gtk_widget_size_allocate(widget, &allocation, -1);
}

void ui_view_request_layout(struct ui_view *view)
{
	atl_safe_gtk_widget_queue_resize(GTK_WIDGET(view));
}

void ui_view_set_visibility(struct ui_view *view, int visibility, float alpha, bool enabled)
{
	GtkWidget *widget = GTK_WIDGET(view);
	GtkWidget *wrapper = gtk_widget_get_parent(widget);

	atl_safe_gtk_widget_set_visible(wrapper, visibility != UI_VIEW_GONE);
	gtk_widget_set_opacity(wrapper, (visibility != UI_VIEW_INVISIBLE) * alpha);
	if (!JAVA_IS_WIDGET(widget))
		gtk_widget_set_sensitive(wrapper, enabled && visibility != UI_VIEW_INVISIBLE && alpha != 0.0f);
}

void ui_view_children_moved(struct ui_view *view)
{
	atl_safe_gtk_widget_queue_allocate(GTK_WIDGET(view));
}

void ui_view_invalidate(struct ui_view *view)
{
	wrapper_widget_queue_draw(WRAPPER_WIDGET(gtk_widget_get_parent(GTK_WIDGET(view))));
}

/* we kinda need per-widget css */
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
void ui_view_set_background_drawable(struct ui_view *view, struct gfx_drawable *drawable)
{
	GtkWidget *widget = GTK_WIDGET(view);
	GdkPaintable *paintable = drawable ? GDK_PAINTABLE(drawable) : NULL;
	wrapper_widget_set_background(WRAPPER_WIDGET(gtk_widget_get_parent(widget)), paintable);
	GtkStyleContext *style_context = gtk_widget_get_style_context(widget);

	GtkCssProvider *old_provider = g_object_get_data(G_OBJECT(widget), "background_style_provider");
	if (old_provider)
		gtk_style_context_remove_provider(style_context, GTK_STYLE_PROVIDER(old_provider));

	GtkCssProvider *css_provider = gtk_css_provider_new();

	char *css_string = g_markup_printf_escaped("* { background-image: none; background-color: #00000000; }");
	gtk_css_provider_load_from_string(css_provider, css_string);
	g_free(css_string);

	gtk_style_context_add_provider(style_context, GTK_STYLE_PROVIDER(css_provider), GTK_STYLE_PROVIDER_PRIORITY_APPLICATION);
	g_object_set_data(G_OBJECT(widget), "background_style_provider", css_provider);
	if (paintable)
		widget_set_needs_allocation(widget);
}
#pragma GCC diagnostic pop

void ui_view_set_background_color(struct ui_view *view, uint32_t color)
{
	GtkWidget *widget = GTK_WIDGET(view);
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(widget));
	wrapper_widget_set_background(wrapper, NULL);

	GtkStyleContext *style_context = gtk_widget_get_style_context(widget);

	GtkCssProvider *old_provider = g_object_get_data(G_OBJECT(widget), "background_style_provider");
	if (old_provider)
		gtk_style_context_remove_provider(style_context, GTK_STYLE_PROVIDER(old_provider));

	GtkCssProvider *css_provider = gtk_css_provider_new();

	char *css_string = g_markup_printf_escaped("* { background-image: none; background-color: #%06x%02x; }", color & 0xFFFFFF, (color >> 24) & 0xFF);
	gtk_css_provider_load_from_string(css_provider, css_string);
	g_free(css_string);

	gtk_style_context_add_provider(style_context, GTK_STYLE_PROVIDER(css_provider), GTK_STYLE_PROVIDER_PRIORITY_APPLICATION);
	g_object_set_data(G_OBJECT(widget), "background_style_provider", css_provider);
	if (((color >> 24) & 0xFF) != 0)
		widget_set_needs_allocation(widget);
}

