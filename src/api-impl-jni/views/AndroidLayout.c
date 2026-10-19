#include <gtk/gtk.h>

#include "src/shared/upcall.h"

#include "AndroidLayout.h"

static int make_measure_spec(int layout_size, int for_size)
{
	if (layout_size >= 0)
		return layout_size | MEASURE_SPEC_EXACTLY;
	else if (for_size >= 0 && layout_size == MATCH_PARENT)
		return for_size | MEASURE_SPEC_EXACTLY;
	else if (for_size >= 0 && layout_size == WRAP_CONTENT)
		return for_size | MEASURE_SPEC_AT_MOST;
	else if (layout_size == WRAP_CONTENT)
		return MEASURE_SPEC_UNSPECIFIED;
	else
		return -1;
}

extern int snapshot_in_progress;

static void android_layout_measure(GtkLayoutManager *layout_manager, GtkWidget *widget, GtkOrientation orientation, int for_size, int *minimum, int *natural, int *minimum_baseline, int *natural_baseline)
{
	int widthMeasureSpec = 0;
	int heightMeasureSpec = 0;
	AndroidLayout *layout = ATL_ANDROID_LAYOUT(layout_manager);

	/* if we're inside a shanpshot, this must be getting called purely to make Gtk call gtk_widget_clear_resize_queued */
	if (snapshot_in_progress)
		return;

	// If the parent widget is also an AndroidLayout, the measurement will already have happened in Java
	if (!ATL_IS_ANDROID_LAYOUT(gtk_widget_get_layout_manager(gtk_widget_get_parent(gtk_widget_get_parent(widget))))) {
		widthMeasureSpec = make_measure_spec(layout->width, orientation == GTK_ORIENTATION_VERTICAL ? for_size : -1);
		heightMeasureSpec = make_measure_spec(layout->height, orientation == GTK_ORIENTATION_HORIZONTAL ? for_size : -1);

		// if layout params say match_parent, but GTK doesn't specify the dimension, fall back to old specification if available
		if (widthMeasureSpec == -1 || heightMeasureSpec == -1) {
			int old_width_spec, old_height_spec;
			atl_upcall_view_get_old_specs(layout->view, &old_width_spec, &old_height_spec);
			if (widthMeasureSpec == -1)
				widthMeasureSpec = old_width_spec;
			if (heightMeasureSpec == -1)
				heightMeasureSpec = old_height_spec;
		}
		if (widthMeasureSpec != -1 && heightMeasureSpec != -1)
			atl_upcall_view_measure(layout->view, widthMeasureSpec, heightMeasureSpec);
	}

	if (orientation == GTK_ORIENTATION_HORIZONTAL) {
		*natural = atl_upcall_view_get_measured(layout->view, false);
		*minimum = heightMeasureSpec && !widthMeasureSpec ? *natural
		                                                  : atl_upcall_view_get_suggested_min(layout->view, false);
	} else if (orientation == GTK_ORIENTATION_VERTICAL) {
		*natural = atl_upcall_view_get_measured(layout->view, true);
		*minimum = widthMeasureSpec && !heightMeasureSpec ? *natural
		                                                  : atl_upcall_view_get_suggested_min(layout->view, true);
	}
	if (*natural < *minimum)
		*natural = *minimum;

	*minimum_baseline = -1;
	*natural_baseline = -1;
}

static void android_layout_allocate(GtkLayoutManager *layout_manager, GtkWidget *widget, int width, int height, int baseline)
{
	/* if we're inside a shanpshot, this must be getting called purely to make Gtk call gtk_widget_clear_resize_queued */
	if (snapshot_in_progress)
		return;

	AndroidLayout *layout = ATL_ANDROID_LAYOUT(layout_manager);
	if (!width && !height) {
		width = layout->real_width;
		height = layout->real_height;
	}

	atl_upcall_view_layout(layout->view, width, height);
}

static GtkSizeRequestMode android_layout_get_request_mode(GtkLayoutManager *layout_manager, GtkWidget *widget)
{
	AndroidLayout *layout = ATL_ANDROID_LAYOUT(layout_manager);

	if (layout->height == WRAP_CONTENT) {
		return GTK_SIZE_REQUEST_HEIGHT_FOR_WIDTH;
	} else if (layout->width == WRAP_CONTENT) {
		return GTK_SIZE_REQUEST_WIDTH_FOR_HEIGHT;
	} else {
		return GTK_SIZE_REQUEST_CONSTANT_SIZE;
	}
}

static void android_layout_dispose(GObject *layout_manager)
{
	AndroidLayout *layout = ATL_ANDROID_LAYOUT(layout_manager);
	atl_ref_free(layout->view);
}

static void android_layout_class_init(AndroidLayoutClass *klass)
{
	klass->parent_class.measure = android_layout_measure;
	klass->parent_class.allocate = android_layout_allocate;
	klass->parent_class.get_request_mode = android_layout_get_request_mode;

	klass->parent_class.parent_class.dispose = android_layout_dispose;
}

static void android_layout_init(AndroidLayout *self) {}

G_DEFINE_TYPE(AndroidLayout, android_layout, GTK_TYPE_LAYOUT_MANAGER)

GtkLayoutManager *android_layout_new(struct atl_ref *view_ref)
{
	AndroidLayout *layout = g_object_new(android_layout_get_type(), NULL);
	layout->view = atl_ref_dup(view_ref);
	layout->width = MATCH_PARENT;
	layout->height = MATCH_PARENT;
	return &layout->parent_instance;
}

void android_layout_set_params(AndroidLayout *layout, int width, int height)
{
	layout->width = width;
	layout->height = height;
}

void widget_set_needs_allocation(GtkWidget *widget)
{
	if (ATL_IS_ANDROID_LAYOUT(gtk_widget_get_layout_manager(widget))) {
		AndroidLayout *layout = ATL_ANDROID_LAYOUT(gtk_widget_get_layout_manager(widget));
		if (!layout->needs_allocation && (layout->real_width || layout->real_height))
			gtk_widget_size_allocate(widget, &(GtkAllocation){.x = 0, .y = 0, .width = layout->real_width, .height = layout->real_height}, 0);
		layout->needs_allocation = true;
	}
}
