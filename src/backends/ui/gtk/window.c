#include <gtk/gtk.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/main-executable/back_button.h"
#include "src/shared/upcall.h"
#include "src/shared/windows.h"
#include "src/slots/ui.h"

#include "gtk_ui.h"

static GtkWidget *view_wrapper(struct ui_view *view)
{
	return gtk_widget_get_parent(GTK_WIDGET(view));
}

/* The window is the gtk host's GtkApplicationWindow holding a GtkOverlay over a GtkStack: the
 * stack has the activities' roots, the overlay the windows over the shown one, each over its dim.
 * A view handle is the inner widget of its WrapperWidget. */

static GtkOverlay *window_overlay(GtkWindow *window)
{
	return GTK_OVERLAY(gtk_window_get_child(window));
}

GtkStack *window_stack(struct ui_window *window)
{
	return GTK_STACK(gtk_overlay_get_child(window_overlay(GTK_WINDOW(window))));
}

// the shown roots, as ui_window_set_roots gave them, on the overlay
struct roots {
	struct ui_root *roots;
	size_t n;
	GPtrArray *widgets; // on the overlay in order: each root's wrapper, after its dim if it has one
};

static struct roots *get_roots(GtkOverlay *overlay)
{
	return g_object_get_data(G_OBJECT(overlay), "atl-roots");
}

static void roots_free(struct roots *roots)
{
	free(roots->roots);
	g_ptr_array_unref(roots->widgets);
	g_free(roots);
}

static gboolean place_child(GtkOverlay *overlay, GtkWidget *child, GdkRectangle *allocation, gpointer data)
{
	int width = gtk_widget_get_width(GTK_WIDGET(overlay)), height = gtk_widget_get_height(GTK_WIDGET(overlay));
	if (g_object_get_data(G_OBJECT(child), "atl-dim")) {
		*allocation = (GdkRectangle){0, 0, width, height};
		return TRUE;
	}
	struct atl_subwindow *subwindow = g_object_get_data(G_OBJECT(child), "atl-subwindow");
	if (!subwindow)
		return FALSE;
	struct atl_window_frame frame;
	windows_place(subwindow, width, height, &frame);
	if (g_object_get_data(G_OBJECT(child), "atl-panel")) // the alert's panel around the root
		frame = *windows_panel(subwindow);
	*allocation = (GdkRectangle){frame.left, frame.top, frame.width, frame.height};
	return TRUE;
}

void window_attach_stack(GtkWindow *window, GtkWidget *stack)
{
	GtkWidget *overlay = gtk_overlay_new();
	gtk_overlay_set_child(GTK_OVERLAY(overlay), stack);
	g_signal_connect(overlay, "get-child-position", G_CALLBACK(place_child), NULL);
	gtk_window_set_child(window, overlay);
}

// a root's dim, kept on its wrapper so that it stays the same widget
static GtkWidget *dim_of(GtkWidget *wrapper, float amount)
{
	GtkWidget *dim = g_object_get_data(G_OBJECT(wrapper), "atl-dim-widget");
	if (!dim) {
		dim = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 0);
		gtk_widget_add_css_class(dim, "ATL-dim");
		gtk_widget_set_can_target(dim, FALSE);
		g_object_set_data(G_OBJECT(dim), "atl-dim", GINT_TO_POINTER(TRUE));
		g_object_set_data_full(G_OBJECT(wrapper), "atl-dim-widget", g_object_ref_sink(dim), g_object_unref);
	}
	gtk_widget_set_opacity(dim, amount);
	return dim;
}

// unchanged widgets stay on the overlay: a removal would drop GTK's focus in them
void ui_window_set_roots(struct ui_window *window, const struct ui_root *list, size_t n)
{
	GtkOverlay *overlay = window_overlay(GTK_WINDOW(window));
	GPtrArray *widgets = g_ptr_array_new_with_free_func(g_object_unref);
	for (size_t i = 0; i < n; i++) {
		if (!list[i].view) // an alert: its own GtkWindow
			continue;
		GtkWidget *wrapper = view_wrapper(list[i].view);
		g_object_set_data(G_OBJECT(wrapper), "atl-subwindow", list[i].window);
		if (list[i].layout.flags & UI_WINDOW_DIM_BEHIND && list[i].layout.dim_amount > 0)
			g_ptr_array_add(widgets, g_object_ref(dim_of(wrapper, list[i].layout.dim_amount)));
		GtkWidget *panel = list[i].chrome ? dialog_panel(list[i].chrome) : NULL;
		if (panel) {
			g_object_set_data(G_OBJECT(panel), "atl-subwindow", list[i].window);
			g_object_set_data(G_OBJECT(panel), "atl-panel", GINT_TO_POINTER(TRUE));
			g_ptr_array_add(widgets, g_object_ref(panel));
		}
		g_ptr_array_add(widgets, g_object_ref(wrapper));
	}
	struct roots *old = get_roots(overlay);
	guint same = 0;
	while (old && same < old->widgets->len && same < widgets->len && g_ptr_array_index(old->widgets, same) == g_ptr_array_index(widgets, same))
		same++;
	for (guint i = old ? old->widgets->len : 0; i-- > same;) {
		GtkWidget *widget = g_ptr_array_index(old->widgets, i);
		bool stays = false;
		for (guint j = 0; j < widgets->len; j++)
			stays |= g_ptr_array_index(widgets, j) == widget;
		if (!stays && !g_object_get_data(G_OBJECT(widget), "atl-dim") && !g_object_get_data(G_OBJECT(widget), "atl-panel"))
			input_root_removed(widget);
		gtk_overlay_remove_overlay(overlay, widget);
	}
	for (guint i = same; i < widgets->len; i++)
		gtk_overlay_add_overlay(overlay, g_ptr_array_index(widgets, i));
	struct roots *roots = g_new0(struct roots, 1);
	roots->roots = n ? memcpy(malloc(n * sizeof(*list)), list, n * sizeof(*list)) : NULL;
	roots->n = n;
	roots->widgets = widgets;
	g_object_set_data_full(G_OBJECT(overlay), "atl-roots", roots, (GDestroyNotify)roots_free);
	gtk_widget_queue_allocate(GTK_WIDGET(overlay)); // a layout change places them again
}

GtkWidget *window_touch_root(GtkWindow *window, double x, double y)
{
	GtkOverlay *overlay = window_overlay(window);
	struct roots *roots = get_roots(overlay);
	const struct ui_root *target = roots ? windows_touch_target(roots->roots, roots->n, x, y) : NULL;
	if (target)
		return view_wrapper(target->view);
	return gtk_stack_get_visible_child(GTK_STACK(gtk_overlay_get_child(overlay)));
}


void ui_window_set_title(struct ui_window *window, const char *title)
{
	gtk_window_set_title(GTK_WINDOW(window), title);
}


void ui_window_clear_background(struct ui_window *window)
{
	gtk_widget_add_css_class(GTK_WIDGET(window), "ATL-no-background");
}

void ui_window_add_activity(struct ui_window *window, struct ui_view *root)
{
	input_attach_window(GTK_WINDOW(window));
	gtk_stack_add_child(window_stack(window), view_wrapper(root));
}

void ui_window_show_activity(struct ui_window *window, struct ui_view *root)
{
	gtk_stack_set_visible_child(window_stack(window), view_wrapper(root));
}

void ui_window_remove_activity(struct ui_window *window, struct ui_view *root)
{
	gtk_stack_remove(window_stack(window), view_wrapper(root));
}

void ui_window_set_back_enabled(struct ui_window *window, bool enabled)
{
	back_button_set_sensitive(enabled);
}

void ui_window_close(struct ui_window *window)
{
	if (GTK_IS_WINDOW(window))
		gtk_window_close(GTK_WINDOW(window));
}

struct atl_app *ui_window_app(struct ui_window *window)
{
	return host_app;
}

void ui_window_get_size(struct ui_window *window, int *width, int *height)
{
	gtk_window_get_default_size(GTK_WINDOW(window), width, height);
}

bool ui_window_is_multi_window(struct ui_window *window)
{
	return !gtk_window_is_maximized(GTK_WINDOW(window));
}

// on the header bar the host set as the titlebar; one provider, reloaded, so frames don't stack
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
static void set_fullscreen(GtkWindow *window, bool fullscreen)
{
	static bool applied;
	if (fullscreen == applied)
		return;
	if (fullscreen && !gtk_window_is_maximized(window)) {
		printf("blocking fullscreen request, because window is not maximized\n");
		return;
	}
	applied = fullscreen;
	if (fullscreen)
		gtk_window_fullscreen(window);
	else
		gtk_window_unfullscreen(window);
}

void ui_window_set_frame(struct ui_window *window, const struct ui_frame *frame)
{
	set_fullscreen(GTK_WINDOW(window), frame->fullscreen);
	GtkWidget *titlebar = gtk_window_get_titlebar(GTK_WINDOW(window));
	GtkCssProvider *provider = g_object_get_data(G_OBJECT(titlebar), "frame_style_provider");
	if (!provider) {
		provider = gtk_css_provider_new();
		gtk_style_context_add_provider(gtk_widget_get_style_context(titlebar), GTK_STYLE_PROVIDER(provider), GTK_STYLE_PROVIDER_PRIORITY_APPLICATION);
		g_object_set_data_full(G_OBJECT(titlebar), "frame_style_provider", provider, g_object_unref);
	}
	if (!frame->has_color) {
		gtk_css_provider_load_from_string(provider, "");
		return;
	}
	char *css = g_strdup_printf("* { background-image: none; background-color: #%06x%02x; color: %s; }", frame->color & 0xFFFFFF, frame->color >> 24, frame->light ? "rgba(0, 0, 0, 0.8)" : "white");
	gtk_css_provider_load_from_string(provider, css);
	g_free(css);
}
#pragma GCC diagnostic pop

struct ui_observation {
	struct atl_ref *observer;
	GdkFrameClock *clock;
	gulong handler;
};

static void on_layout(GdkFrameClock *clock, struct ui_observation *observation)
{
	atl_upcall_global_layout(observation->observer);
}

static void on_paint(GdkFrameClock *clock, struct ui_observation *observation)
{
	atl_upcall_pre_draw(observation->observer);
}

struct ui_observation *ui_window_observe(struct ui_window *window, struct atl_ref *observer, enum ui_observe what)
{
	struct ui_observation *observation = g_new(struct ui_observation, 1);
	observation->observer = observer;
	gtk_widget_realize(GTK_WIDGET(window)); // a dialog's window has no frame clock before
	// held: the dialog's window may go before the observer's unobserve
	observation->clock = g_object_ref(gtk_widget_get_frame_clock(GTK_WIDGET(window)));
	if (what == UI_OBSERVE_LAYOUT) {
		/* GTKs own handler is always the first, so our handler gets called after all widgets have been measured and allocated */
		observation->handler = g_signal_connect(observation->clock, "layout", G_CALLBACK(on_layout), observation);
	} else {
		/* Unlike AOSP, our hook gets invoked after all widgets have been drawn. */
		/* Switching to ::layout would fix the timing, but we would miss frames where no layout was requested. */
		/* TODO: Hook into the DecorView snapshot function to get in before widget drawing. */
		observation->handler = g_signal_connect(observation->clock, "paint", G_CALLBACK(on_paint), observation);
	}
	return observation;
}

void ui_window_unobserve(struct ui_window *window, struct ui_observation *observation)
{
	g_signal_handler_disconnect(observation->clock, observation->handler);
	g_object_unref(observation->clock);
	atl_ref_free(observation->observer);
	g_free(observation);
}
