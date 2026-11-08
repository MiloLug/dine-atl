#include <gtk/gtk.h>
#include <stdio.h>

#include "src/slots/ui.h"

/* A Toast as an OSD label on the app window's overlay, placed by GTK from the gravity's alignment
 * and margins; it takes no input. */

#define GRAVITY_PULL_BEFORE 0x2
#define GRAVITY_PULL_AFTER  0x4
#define GRAVITY_Y_SHIFT     4

static GtkAlign align(int axis)
{
	switch (axis & (GRAVITY_PULL_BEFORE | GRAVITY_PULL_AFTER)) {
	case GRAVITY_PULL_BEFORE:
		return GTK_ALIGN_START;
	case GRAVITY_PULL_AFTER:
		return GTK_ALIGN_END;
	case GRAVITY_PULL_BEFORE | GRAVITY_PULL_AFTER:
		return GTK_ALIGN_FILL;
	default:
		return GTK_ALIGN_CENTER;
	}
}

void ui_toast_show(struct ui_window *window, const char *text, int gravity, int x, int y)
{
	GtkOverlay *overlay = GTK_OVERLAY(gtk_window_get_child(GTK_WINDOW(window)));
	GtkWidget *label = g_object_get_data(G_OBJECT(overlay), "atl-toast");
	if (!label) {
		label = gtk_label_new(NULL);
		gtk_widget_add_css_class(label, "osd");
		gtk_widget_add_css_class(label, "ATL-toast");
		gtk_widget_set_can_target(label, FALSE);
		gtk_overlay_add_overlay(overlay, label);
		g_object_set_data(G_OBJECT(overlay), "atl-toast", label);
	}
	gtk_label_set_text(GTK_LABEL(label), text);
	gtk_widget_set_halign(label, align(gravity));
	gtk_widget_set_valign(label, align(gravity >> GRAVITY_Y_SHIFT));
	gtk_widget_set_margin_start(label, x);
	gtk_widget_set_margin_end(label, x);
	gtk_widget_set_margin_top(label, y);
	gtk_widget_set_margin_bottom(label, y);
	gtk_widget_set_visible(label, TRUE);
#ifdef ATL_TEST_HOOKS
	printf("toast: shown \"%s\"\n", text);
	fflush(stdout);
#endif
}

void toast_raise(GtkOverlay *overlay)
{
	GtkWidget *label = g_object_get_data(G_OBJECT(overlay), "atl-toast");
	if (!label)
		return;
	g_object_ref(label);
	gtk_overlay_remove_overlay(overlay, label);
	gtk_overlay_add_overlay(overlay, label);
	g_object_unref(label);
}

void ui_toast_hide(struct ui_window *window)
{
	GtkOverlay *overlay = GTK_OVERLAY(gtk_window_get_child(GTK_WINDOW(window)));
	GtkWidget *label = g_object_get_data(G_OBJECT(overlay), "atl-toast");
	if (label)
		gtk_widget_set_visible(label, FALSE);
}
