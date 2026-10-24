#include <assert.h>
#include <stdio.h>

#include <gtk/gtk.h>

#include "../defines.h"
#include "../gtk_util.h"

#include "../views/AndroidLayout.h"
#include "../widgets/WrapperWidget.h"
#include "src/backends/ui/gtk/gtk_ui.h"
#include "src/slots/ui.h"

#include "../generated_headers/android_view_MotionEvent.h"
#include "../generated_headers/android_view_View.h"

#define JAVA_ENUM_CLASS android_view_MotionEvent
enum {
	JAVA_ENUM(ACTION_DOWN),
	JAVA_ENUM(ACTION_UP),
	JAVA_ENUM(ACTION_MOVE),
	JAVA_ENUM(ACTION_CANCEL),

	JAVA_ENUM(ACTION_POINTER_DOWN),
	JAVA_ENUM(ACTION_POINTER_UP),

	JAVA_ENUM(ACTION_HOVER_MOVE),
	JAVA_ENUM(ACTION_SCROLL),
	JAVA_ENUM(ACTION_HOVER_ENTER),
	JAVA_ENUM(ACTION_HOVER_EXIT),
};
#undef JAVA_ENUM_CLASS

#define SOURCE_CLASS_POINTER 0x2

// wrappers whose hover exit waits for the pointers to go up (composeUI apps don't want it while one is down)
static GSList *hover_exits_pending;

static void call_hover_callback(jobject this, int action, float x, float y, float raw_x, float raw_y)
{
	JNIEnv *env = get_jni_env();
	jobject motion_event = (*env)->NewObject(env, handle_cache.motion_event.class, handle_cache.motion_event.constructor_single, SOURCE_CLASS_POINTER, action, 0, x, y, raw_x, raw_y);
	(*env)->CallBooleanMethod(env, this, handle_cache.view.dispatchHoverEvent, motion_event);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	(*env)->DeleteLocalRef(env, motion_event); // GTK calls back in the base JNI frame
}

void view_flush_hover_exits(void)
{
	for (GSList *l = hover_exits_pending; l; l = l->next) {
		WrapperWidget *wrapper = l->data;
		wrapper->hover_exit_pending = FALSE;
		call_hover_callback(wrapper->jobj, ACTION_HOVER_EXIT, 0, 0, 0, 0);
		g_object_unref(wrapper);
	}
	g_clear_pointer(&hover_exits_pending, g_slist_free);
}

static void transform_coords_to_widget_relative(GtkWidget *widget, double *x, double *y)
{
	int ret;

	graphene_point_t p;
	double off_x;
	double off_y;

	GtkWidget *window = GTK_WIDGET(gtk_widget_get_native(widget));
	gtk_native_get_surface_transform(GTK_NATIVE(window), &off_x, &off_y);
	ret = gtk_widget_compute_point(window, widget, &GRAPHENE_POINT_INIT(*x - off_x, *y - off_y), &p);
	assert(ret);

	*x = p.x;
	*y = p.y;
}

#define MAGIC_SCROLL_FACTOR 32

/* Gtk4 for whatever reason doesn't report cursor position for GtkEventControllerScroll, thankfully
 * it's not too annoying to get it ourselves */
gboolean hacky_get_cursor_position(GtkWidget *widget, double *x, double *y)
{
	GdkSeat *seat;
	GdkSurface *surface;
	GdkDevice *device_pointer;

	seat = gdk_display_get_default_seat(gtk_widget_get_display(widget));

	device_pointer = gdk_seat_get_pointer(seat);
	surface = gtk_native_get_surface(gtk_widget_get_native(widget));

	return gdk_surface_get_device_position(surface,
	                                       device_pointer,
	                                       x, y, NULL);
}

static gboolean scroll_cb(GtkEventControllerScroll *controller, gdouble dx, gdouble dy, jobject this)
{
	double x;
	double y;
	double raw_x;
	double raw_y;

	JNIEnv *env = get_jni_env();
	GdkScrollUnit scroll_unit = gtk_event_controller_scroll_get_unit(controller);
	GdkEvent *event = gtk_event_controller_get_current_event(GTK_EVENT_CONTROLLER(controller));
	GtkWidget *widget = gtk_event_controller_get_widget(GTK_EVENT_CONTROLLER(controller));
	WrapperWidget *wrapper = WRAPPER_WIDGET(widget);
	hacky_get_cursor_position(widget, &raw_x, &raw_y);
	x = raw_x, y = raw_y;
	transform_coords_to_widget_relative(widget, &x, &y);

	if (scroll_unit == GDK_SCROLL_UNIT_SURFACE) {
		dx /= MAGIC_SCROLL_FACTOR;
		dy /= MAGIC_SCROLL_FACTOR;
	}
	jobject motion_event = (*env)->NewObject(env, handle_cache.motion_event.class,
	                                         handle_cache.motion_event.constructor_scroll,
	                                         SOURCE_CLASS_POINTER, ACTION_SCROLL,
	                                         gdk_event_get_time(event), x, y, raw_x, raw_y, dx, -dy);

	gboolean ret = (*env)->CallBooleanMethod(env, this, handle_cache.view.dispatchGenericMotionEvent, motion_event);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	(*env)->DeleteLocalRef(env, motion_event);

	// HACK: replay last hover event to work around scrolling issues in composeUI apps
	if (wrapper->hover_x && wrapper->hover_y)
		call_hover_callback(this, ACTION_HOVER_MOVE, wrapper->hover_x, wrapper->hover_y, wrapper->hover_x, wrapper->hover_y);

	return ret;
}

static void hover_enter_cb(GtkEventControllerMotion *controller, double x, double y, WrapperWidget *wrapper)
{
	double raw_x;
	double raw_y;
	GdkEvent *event = gtk_event_controller_get_current_event(GTK_EVENT_CONTROLLER(controller));
	gdk_event_get_position(event, &raw_x, &raw_y);
	wrapper->hover_exit_pending = FALSE;
	wrapper->hover_x = x;
	wrapper->hover_y = y;
	call_hover_callback(wrapper->jobj, ACTION_HOVER_ENTER, x, y, raw_x, raw_y);
}

static void hover_leave_cb(GtkEventControllerMotion *controller, WrapperWidget *wrapper)
{
	if (input_pointers_up()) {
		call_hover_callback(wrapper->jobj, ACTION_HOVER_EXIT, 0, 0, 0, 0);
	} else if (!wrapper->hover_exit_pending) {
		wrapper->hover_exit_pending = TRUE;
		hover_exits_pending = g_slist_prepend(hover_exits_pending, g_object_ref(wrapper));
	}
}

static void hover_motion_cb(GtkEventControllerMotion *controller, double x, double y, WrapperWidget *wrapper)
{
	double raw_x;
	double raw_y;
	GdkEvent *event = gtk_event_controller_get_current_event(GTK_EVENT_CONTROLLER(controller));
	gdk_event_get_position(event, &raw_x, &raw_y);
	wrapper->hover_x = x;
	wrapper->hover_y = y;
	call_hover_callback(wrapper->jobj, ACTION_HOVER_MOVE, x, y, raw_x, raw_y);
}

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
void view_attach_motion_controllers(WrapperWidget *wrapper, const struct ui_view_traits *traits)
{
	if (traits->generic_motion) {
		GtkEventController *controller = gtk_event_controller_scroll_new(GTK_EVENT_CONTROLLER_SCROLL_VERTICAL);

		g_signal_connect(controller, "scroll", G_CALLBACK(scroll_cb), wrapper->jobj);
		gtk_widget_add_controller(GTK_WIDGET(wrapper), controller);
	}
	if (traits->hovers) {
		GtkEventController *controller = gtk_event_controller_motion_new();

		g_signal_connect(controller, "enter", G_CALLBACK(hover_enter_cb), wrapper);
		g_signal_connect(controller, "leave", G_CALLBACK(hover_leave_cb), wrapper);
		g_signal_connect(controller, "motion", G_CALLBACK(hover_motion_cb), wrapper);
		gtk_widget_add_controller(GTK_WIDGET(wrapper), controller);
	}
}

JNIEXPORT void JNICALL Java_android_view_View_native_1drawBackground(JNIEnv *env, jobject this, jlong widget_ptr, jlong snapshot_ptr)
{
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(GTK_WIDGET(_PTR(widget_ptr))));
	GdkSnapshot *snapshot = GDK_SNAPSHOT(_PTR(snapshot_ptr));
	if (wrapper->background)
		gtk_widget_snapshot_child(&wrapper->parent_instance, wrapper->background, snapshot);
}

JNIEXPORT void JNICALL Java_android_view_View_native_1drawContent(JNIEnv *env, jobject this, jlong widget_ptr, jlong snapshot_ptr)
{
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(GTK_WIDGET(_PTR(widget_ptr))));
	GdkSnapshot *snapshot = GDK_SNAPSHOT(_PTR(snapshot_ptr));
	gtk_widget_snapshot_child(&wrapper->parent_instance, wrapper->child, snapshot);
}
