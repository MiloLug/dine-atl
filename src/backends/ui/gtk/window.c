#include <gtk/gtk.h>
#include <stdio.h>

#include "src/main-executable/back_button.h"
#include "src/shared/upcall.h"
#include "src/slots/ui.h"

#include "gtk_ui.h"

/* The window is the gtk host's GtkApplicationWindow holding a GtkStack; a view handle is the inner
 * widget of its WrapperWidget. */

GtkStack *window_stack(struct ui_window *window)
{
	return GTK_STACK(gtk_window_get_child(GTK_WINDOW(window)));
}

static GtkWidget *view_wrapper(struct ui_view *view)
{
	return gtk_widget_get_parent(GTK_WIDGET(view));
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
