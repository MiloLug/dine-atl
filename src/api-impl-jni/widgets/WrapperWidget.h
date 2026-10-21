#ifndef WRAPPER_WIDGET_H
#define WRAPPER_WIDGET_H

#include <gtk/gtk.h>
#include <jni.h>

#include "src/slots/ui.h"

G_DECLARE_FINAL_TYPE(WrapperWidget, wrapper_widget, WRAPPER, WIDGET, GtkWidget)

struct _WrapperWidget {
	GtkWidget parent_instance;
	GtkWidget *child;
	GtkWidget *background;
	JavaVM *jvm;
	jobject jobj;
	jobject canvas;
	jmethodID draw_method;
	jmethodID measure_method;
	jmethodID computeScroll_method;
	int layout_width;
	int layout_height;
	int real_width;
	int real_height;
	gboolean needs_allocation;
	gboolean hover_exit_pending;
	int hover_x;
	int hover_y;
};

struct _WrapperWidgetClass {
	GtkWidgetClass parent_class;
};


GtkWidget *wrapper_widget_new(void);
void wrapper_widget_set_child(WrapperWidget *parent, GtkWidget *child);
void wrapper_widget_set_jobject(WrapperWidget *wrapper, JNIEnv *env, jobject jobj);
/* Takes over the weak reference. */
void wrapper_widget_setup(WrapperWidget *wrapper, struct atl_ref *view_ref, const struct ui_view_traits *traits);
void wrapper_widget_queue_draw(WrapperWidget *wrapper);
void wrapper_widget_set_layout_params(WrapperWidget *wrapper, int width, int height);
void wrapper_widget_set_background(WrapperWidget *wrapper, GdkPaintable *paintable);
void wrapper_widget_register_invalidation_listener(WrapperWidget *wrapper);

void view_attach_motion_controllers(WrapperWidget *wrapper, const struct ui_view_traits *traits);
/* The last pointer went up: hover exits held back while it was down go out. */
void view_flush_hover_exits(void);

#endif
