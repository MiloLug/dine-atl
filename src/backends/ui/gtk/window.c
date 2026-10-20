#include <gtk/gtk.h>

#include "src/main-executable/back_button.h"
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

// read back by View.native_get_window
void ui_window_set_java(struct ui_window *window, struct atl_ref *window_ref)
{
	g_object_set_data(G_OBJECT(window), "jobject", window_ref);
}

void ui_window_clear_background(struct ui_window *window)
{
	gtk_widget_add_css_class(GTK_WIDGET(window), "ATL-no-background");
}

void ui_window_add_activity(struct ui_window *window, struct ui_view *root)
{
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

// on the header bar the host set as the titlebar; one provider, reloaded, so frames don't stack
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
void ui_window_set_frame(struct ui_window *window, const struct ui_frame *frame)
{
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
