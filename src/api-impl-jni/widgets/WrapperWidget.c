#include <gtk/gtk.h>

#include "../defines.h"
#include "../gtk_util.h"
#include "src/shared/view.h"

#include "../generated_headers/android_view_View.h"

#include "WrapperWidget.h"
#include "src/api-impl-jni/views/AndroidLayout.h"

G_DEFINE_TYPE(WrapperWidget, wrapper_widget, GTK_TYPE_WIDGET)

typedef enum { ATL_ID = 1,
	       ATL_ID_NAME,
	       ATL_CLASS_NAME,
	       ATL_SUPER_CLASS_NAMES,
	       N_PROPERTIES } WrapperWidgetProperty;
static GParamSpec *wrapper_widget_properties[N_PROPERTIES] = {
	NULL,
};

static void wrapper_widget_set_property(GObject *object, guint property_id, const GValue *value, GParamSpec *pspec)
{
	switch ((WrapperWidgetProperty)property_id) {
		default:
			G_OBJECT_WARN_INVALID_PROPERTY_ID(object, property_id, pspec);
			break;
	}
}

static void wrapper_widget_get_property(GObject *object, guint property_id, GValue *value, GParamSpec *pspec)
{
	WrapperWidget *self = WRAPPER_WIDGET(object);

	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 8); // GTK calls it from the base JNI frame

	jobject jobj = self->jobj;
	jclass class = _CLASS(jobj);

	switch ((WrapperWidgetProperty)property_id) {
		case ATL_ID: {
			jint id_jint = (*env)->CallIntMethod(env, jobj, handle_cache.view.getId);
			if ((*env)->ExceptionCheck(env))
				(*env)->ExceptionDescribe(env);

			g_value_take_string(value, g_markup_printf_escaped("0x%08x", id_jint));
			break;
		}

		case ATL_ID_NAME: {
			jstring id_name_jstring = (*env)->CallObjectMethod(env, jobj, handle_cache.view.getIdName);
			if ((*env)->ExceptionCheck(env))
				(*env)->ExceptionDescribe(env);

			const char *id_name = (*env)->GetStringUTFChars(env, id_name_jstring, NULL);
			g_value_set_string(value, id_name);

			(*env)->ReleaseStringUTFChars(env, id_name_jstring, id_name);
			break;
		}

		case ATL_CLASS_NAME: {
			jstring class_name_jstring = (*env)->CallObjectMethod(env, class, _METHOD_OF(class, "getName", "()Ljava/lang/String;"));
			if ((*env)->ExceptionCheck(env))
				(*env)->ExceptionDescribe(env);

			const char *class_name = (*env)->GetStringUTFChars(env, class_name_jstring, NULL);
			g_value_set_string(value, class_name);

			(*env)->ReleaseStringUTFChars(env, class_name_jstring, class_name);
			break;
		}

		case ATL_SUPER_CLASS_NAMES: {
			jstring super_classes_names_obj = (*env)->CallObjectMethod(env, jobj, handle_cache.view.getAllSuperClasses);
			if ((*env)->ExceptionCheck(env))
				(*env)->ExceptionDescribe(env);

			const char *super_classes_names = (*env)->GetStringUTFChars(env, super_classes_names_obj, NULL);
			g_value_set_string(value, super_classes_names);

			(*env)->ReleaseStringUTFChars(env, super_classes_names_obj, super_classes_names);
			break;
		}

		default:
			G_OBJECT_WARN_INVALID_PROPERTY_ID(object, property_id, pspec);
			break;
	}
	(*env)->PopLocalFrame(env, NULL);
}

static void wrapper_widget_init(WrapperWidget *wrapper_widget)
{
}

static void wrapper_widget_dispose(GObject *wrapper_widget)
{
	GtkWidget *widget = GTK_WIDGET(wrapper_widget);
	GtkWidget *child = gtk_widget_get_first_child(widget);
	while (child) {
		GtkWidget *_child = gtk_widget_get_next_sibling(child);
		gtk_widget_unparent(child);
		child = _child;
	}
	WrapperWidget *wrapper = WRAPPER_WIDGET(wrapper_widget);
	if (wrapper->jvm) {
		JNIEnv *env;
		(*wrapper->jvm)->GetEnv(wrapper->jvm, (void **)&env, JNI_VERSION_1_6);
		if (wrapper->jobj)
			_WEAK_UNREF(wrapper->jobj);
		if (wrapper->canvas)
			_UNREF(wrapper->canvas);
	}
	G_OBJECT_CLASS(wrapper_widget_parent_class)->dispose(wrapper_widget);
}

GtkSizeRequestMode wrapper_widget_get_request_mode(GtkWidget *widget)
{
	WrapperWidget *wrapper = WRAPPER_WIDGET(widget);
	return gtk_widget_get_request_mode(wrapper->child);
}

void wrapper_widget_measure(GtkWidget *widget, GtkOrientation orientation, int for_size, int *minimum, int *natural, int *minimum_baseline, int *natural_baseline)
{
	WrapperWidget *wrapper = WRAPPER_WIDGET(widget);
	gtk_widget_measure(wrapper->child, orientation, for_size, minimum, natural, minimum_baseline, natural_baseline);
	if (orientation == GTK_ORIENTATION_HORIZONTAL && (wrapper->layout_width > 0)) {
		*minimum = *natural = wrapper->layout_width;
	} else if (orientation == GTK_ORIENTATION_VERTICAL && (wrapper->layout_height > 0)) {
		*minimum = *natural = wrapper->layout_height;
	}
}

void wrapper_widget_allocate(GtkWidget *widget, int width, int height, int baseline)
{
	WrapperWidget *wrapper = WRAPPER_WIDGET(widget);
	if (!width && !height) {
		width = wrapper->real_width;
		height = wrapper->real_height;
	}
	GtkAllocation allocation = {
		.x = 0,
		.y = 0,
		.width = width,
		.height = height,
	};

	if (wrapper->computeScroll_method) {
		// The child needs to know its size before calling computeScroll, so we allocate it twice.
		// second allocate will not trigger onLayout, because of unchanged size
		gtk_widget_size_allocate(wrapper->child, &allocation, baseline);

		JNIEnv *env;
		(*wrapper->jvm)->GetEnv(wrapper->jvm, (void **)&env, JNI_VERSION_1_6);
		(*env)->CallVoidMethod(env, wrapper->jobj, wrapper->computeScroll_method);
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
		allocation.x = -(*env)->CallIntMethod(env, wrapper->jobj, handle_cache.view.getScrollX);
		allocation.y = -(*env)->CallIntMethod(env, wrapper->jobj, handle_cache.view.getScrollY);
	}

	if (ATL_IS_ANDROID_LAYOUT(gtk_widget_get_layout_manager(wrapper->child))) {
		AndroidLayout *layout = ATL_ANDROID_LAYOUT(gtk_widget_get_layout_manager(wrapper->child));
		if (layout->real_width != width || layout->real_height != height) {
			layout->real_width = width;
			layout->real_height = height;
			if (!layout->needs_allocation)
				atl_safe_gtk_widget_queue_allocate(wrapper->child);
		}
		if (layout->needs_allocation)
			gtk_widget_size_allocate(wrapper->child, &allocation, baseline);
		else
			gtk_widget_size_allocate(wrapper->child, &(GtkAllocation){.x = allocation.x, .y = allocation.y}, baseline);
	} else {
		gtk_widget_size_allocate(wrapper->child, &allocation, baseline);
	}
	if (wrapper->background)
		gtk_widget_size_allocate(wrapper->background, &allocation, baseline);
}

/* this is used to avoid queing layout changes in the middle of snapshotting */
int snapshot_in_progress = 0;
static jfieldID canvas_handle_field;
static void wrapper_widget_snapshot(GtkWidget *widget, GdkSnapshot *snapshot)
{
	snapshot_in_progress++;

	WrapperWidget *wrapper = WRAPPER_WIDGET(widget);
	if (wrapper->real_height > 0 && wrapper->real_width > 0) {
		gtk_snapshot_push_clip(snapshot, &GRAPHENE_RECT_INIT(0, 0, wrapper->real_width, wrapper->real_height));
	}
	if (wrapper->draw_method) {
		JNIEnv *env = get_jni_env();
		(*env)->SetLongField(env, wrapper->canvas, canvas_handle_field, _INTPTR(snapshot));
		(*env)->CallVoidMethod(env, wrapper->jobj, wrapper->draw_method, wrapper->canvas);
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
	} else {
		GtkWidget *widget = &wrapper->parent_instance;
		GtkWidget *child = gtk_widget_get_first_child(widget);
		while (child) {
			gtk_widget_snapshot_child(widget, child, snapshot);
			child = gtk_widget_get_next_sibling(child);
		}
	}
	if (wrapper->real_height > 0 && wrapper->real_width > 0) {
		gtk_snapshot_pop(snapshot);
	}

	snapshot_in_progress--;
}

static void wrapper_widget_class_init(WrapperWidgetClass *class)
{
	GObjectClass *object_class = G_OBJECT_CLASS(class);
	GtkWidgetClass *widget_class = GTK_WIDGET_CLASS(class);

	object_class->dispose = wrapper_widget_dispose;

	widget_class->get_request_mode = wrapper_widget_get_request_mode;
	widget_class->measure = wrapper_widget_measure;
	widget_class->size_allocate = wrapper_widget_allocate;
	widget_class->snapshot = wrapper_widget_snapshot;

	object_class->set_property = wrapper_widget_set_property;
	object_class->get_property = wrapper_widget_get_property;

	// According to testing, these properties are not evaluated till we open the GtkInspector
	wrapper_widget_properties[ATL_ID] = g_param_spec_string("ATL-id", "ATL: ID", "ID of the component", "", G_PARAM_READABLE);
	wrapper_widget_properties[ATL_ID_NAME] = g_param_spec_string("ATL-id-name", "ATL: ID name", "Name of the ID of the component", "", G_PARAM_READABLE);
	wrapper_widget_properties[ATL_CLASS_NAME] = g_param_spec_string("ATL-class-name", "ATL: Class name", "Name of the class of the component", "", G_PARAM_READABLE);
	wrapper_widget_properties[ATL_SUPER_CLASS_NAMES] = g_param_spec_string("ATL-superclasses-names", "ATL: Super classes names", "Names of all the superclasses of the component class", "", G_PARAM_READABLE);

	g_object_class_install_properties(object_class, N_PROPERTIES, wrapper_widget_properties);
}

GtkWidget *wrapper_widget_new(void)
{
	return g_object_new(wrapper_widget_get_type(), NULL);
}

void wrapper_widget_set_child(WrapperWidget *parent, GtkWidget *child) // TODO: make sure there can only be one child
{
	gtk_widget_insert_before(child, GTK_WIDGET(parent), NULL);
	parent->child = child;
}

static guint queue_queue_redraw(GtkWidget *widget)
{
	gtk_widget_queue_draw(widget);
	g_object_unref(widget);
	return G_SOURCE_REMOVE;
}

void wrapper_widget_queue_draw(WrapperWidget *wrapper)
{
	if (wrapper->draw_method) {
		/* schedule the call to gtk_widget_queue_draw for a future event loop pass in case we're currently inside the snapshot */
		/* GTK+ uses G_PRIORITY_HIGH_IDLE + 10 for resizing operations, and G_PRIORITY_HIGH_IDLE + 20 for redrawing operations. */
		g_idle_add_full(G_PRIORITY_HIGH_IDLE + 20, G_SOURCE_FUNC(queue_queue_redraw), g_object_ref(wrapper), NULL);
	}

	if (wrapper->child)
		gtk_widget_queue_draw(wrapper->child);
	if (wrapper->computeScroll_method) {
		atl_safe_gtk_widget_queue_allocate(GTK_WIDGET(wrapper));
	}
}

void wrapper_widget_setup(WrapperWidget *wrapper, struct atl_ref *view_ref, const struct ui_view_traits *traits)
{
	JNIEnv *env = get_jni_env();
	jobject jobj = (jobject)view_ref;
	JavaVM *jvm;
	(*env)->GetJavaVM(env, &jvm);
	wrapper->jvm = jvm;
	wrapper->jobj = jobj;
	if (traits->draws) {
		wrapper->draw_method = _METHOD_OF(jobj, "draw", "(Landroid/graphics/Canvas;)V");
		jclass canvas_class = (*env)->FindClass(env, "android/atl/ATLCanvas");
		jmethodID canvas_constructor = _METHOD(canvas_class, "<init>", "(J)V");
		wrapper->canvas = _REF((*env)->NewObject(env, canvas_class, canvas_constructor, 0));
		canvas_handle_field = _FIELD_ID(canvas_class, "handle", "J");
		(*env)->DeleteLocalRef(env, canvas_class);
	}

	if (traits->scrolls) {
		wrapper->computeScroll_method = _METHOD_OF(jobj, "computeScroll", "()V");
	}
}

void wrapper_widget_set_jobject(WrapperWidget *wrapper, JNIEnv *env, jobject jobj)
{
	struct ui_view_traits traits;
	atl_view_traits_get(env, jobj, &traits);
	wrapper_widget_setup(wrapper, (struct atl_ref *)_WEAK_REF(jobj), &traits);
}

void wrapper_widget_set_layout_params(WrapperWidget *wrapper, int width, int height)
{
	wrapper->layout_width = width;
	wrapper->layout_height = height;
}

void wrapper_widget_set_background(WrapperWidget *wrapper, GdkPaintable *paintable)
{
	if (!paintable) {
		if (wrapper->background) {
			gtk_widget_unparent(wrapper->background);
			wrapper->background = NULL;
		}
		return;
	}
	if (!wrapper->background) {
		wrapper->background = gtk_picture_new();
		gtk_widget_insert_after(wrapper->background, GTK_WIDGET(wrapper), NULL);
	}
	gtk_picture_set_paintable(GTK_PICTURE(wrapper->background), paintable);
}

// ComposeUI layouts need to know when to redraw native widgets. There is no way in GTK to get
// notified about invalidations on specific widgets, but we get that global invalidation from the
// frame clock. Just flag it as invalidated on every global invalidation. The redraw will be a
// no-op in GTK anyway if redraw was not needed for the specific widget.
static void on_paint(GdkFrameClock *self, WrapperWidget *wrapper)
{
	JNIEnv *env = get_jni_env();
	(*env)->CallVoidMethod(env, wrapper->jobj, handle_cache.view.propagateInvalidation);
}

static void on_realize(GtkWidget *wrapper, gpointer user_data)
{
	GdkFrameClock *clock = gtk_widget_get_frame_clock(wrapper);
	g_signal_connect(clock, "paint", G_CALLBACK(on_paint), wrapper);
}

static void on_unrealize(GtkWidget *wrapper, gpointer user_data)
{
	GdkFrameClock *clock = gtk_widget_get_frame_clock(wrapper);
	g_signal_handlers_disconnect_by_data(clock, wrapper);
}

void wrapper_widget_register_invalidation_listener(WrapperWidget *wrapper)
{
	g_signal_connect(wrapper, "realize", G_CALLBACK(on_realize), NULL);
	g_signal_connect(wrapper, "unrealize", G_CALLBACK(on_unrealize), NULL);
}
