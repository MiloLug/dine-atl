#include <gtk/gtk.h>

#include <math.h>

#include "src/api-impl-jni/views/AndroidLayout.h"
#include "src/api-impl-jni/widgets/WrapperWidget.h"
#include "src/main-executable/back_button.h"
#include "src/slots/ui.h"

#include "gtk_ui.h"

/* The window is the gtk host's GtkApplicationWindow holding a GtkStack; a view handle is the inner
 * widget of its WrapperWidget. */

static GtkStack *window_stack(struct ui_window *window)
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

static void dump_view(FILE *out, GtkWidget *wrapper, GtkWidget *parent, int depth)
{
	WrapperWidget *wrapper_widget = WRAPPER_WIDGET(wrapper);
	GtkWidget *inner = wrapper_widget->child;
	const char *text_class = g_object_get_data(G_OBJECT(inner), "atl-class-name");
	graphene_rect_t bounds;
	if (!gtk_widget_compute_bounds(wrapper, parent, &bounds))
		graphene_rect_init(&bounds, 0, 0, 0, 0);
	// wrappers laid out from Java get allocated 0x0 and keep their size in real_width and real_height
	long width = wrapper_widget->real_width ?: lroundf(bounds.size.width);
	long height = wrapper_widget->real_height ?: lroundf(bounds.size.height);

	fprintf(out, "viewtree: %*s%s %ld,%ld %ldx%ld", depth * 2, "", text_class ?: gtk_widget_get_name(inner),
	        lroundf(bounds.origin.x), lroundf(bounds.origin.y), width, height);
	if (text_class)
		fprintf(out, " \"%s\"", gtk_label_get_text(text_box_get_label(inner)));
	fputc('\n', out);

	if (!ATL_IS_ANDROID_LAYOUT(gtk_widget_get_layout_manager(inner)))
		return;
	for (GtkWidget *child = gtk_widget_get_first_child(inner); child; child = gtk_widget_get_next_sibling(child)) {
		if (WRAPPER_IS_WIDGET(child))
			dump_view(out, child, wrapper, depth + 1);
	}
}

void ui_window_dump(struct ui_window *window, FILE *out)
{
	GtkWidget *root = gtk_stack_get_visible_child(window_stack(window));
	if (root)
		dump_view(out, root, root, 0);
	fprintf(out, "viewtree: end\n");
	fflush(out);
}
