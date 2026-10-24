#include <gtk/gtk.h>

#include <math.h>

#include "src/api-impl-jni/views/AndroidLayout.h"
#include "src/api-impl-jni/widgets/WrapperWidget.h"
#include "src/slots/ui.h"

#include "gtk_ui.h"

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
		fprintf(out, " \"%s\"", gtk_label_get_text(text_get_label(inner)));
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
