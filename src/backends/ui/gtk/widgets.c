#include <gtk/gtk.h>

#include "src/api-impl-jni/widgets/WrapperWidget.h"
#include "src/slots/ui.h"

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
