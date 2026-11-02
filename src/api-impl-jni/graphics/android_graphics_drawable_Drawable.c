#include <gdk/gdk.h>

#include "src/shared/test_hooks.h"

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_graphics_drawable_Drawable.h"
#include "java_paintable.h"

struct _JavaPaintable {
	GObject parent_instance;
	jobject drawable;
};

static void java_paintable_snapshot(GdkPaintable *gdk_paintable, GdkSnapshot *snapshot, double width, double height)
{
	JNIEnv *env = get_jni_env();
	JavaPaintable *paintable = JAVA_PAINTABLE(gdk_paintable);
	jclass canvas_class = (*env)->FindClass(env, "android/atl/ATLCanvas");
	jmethodID canvas_constructor = _METHOD(canvas_class, "<init>", "(JII)V");
	jobject canvas = (*env)->NewObject(env, canvas_class, canvas_constructor, _INTPTR(snapshot), (int)width, (int)height);
	(*env)->CallVoidMethod(env, paintable->drawable, handle_cache.drawable.setBounds, 0, 0, (int)width, (int)height);
	(*env)->CallVoidMethod(env, paintable->drawable, handle_cache.drawable.draw, canvas);
	exit_on_exception(env);
	(*env)->DeleteLocalRef(env, canvas);
	(*env)->DeleteLocalRef(env, canvas_class);
}

static int java_paintable_get_intrinsic_width(GdkPaintable *gdk_paintable)
{
	JNIEnv *env = get_jni_env();
	JavaPaintable *paintable = JAVA_PAINTABLE(gdk_paintable);
	jmethodID getIntrinsicWidth = _METHOD(handle_cache.drawable.class, "getIntrinsicWidth", "()I");
	int width = (*env)->CallIntMethod(env, paintable->drawable, getIntrinsicWidth);
	return width > 0 ? width : 0;
}

static int java_paintable_get_intrinsic_height(GdkPaintable *gdk_paintable)
{
	JNIEnv *env = get_jni_env();
	JavaPaintable *paintable = JAVA_PAINTABLE(gdk_paintable);
	jmethodID getIntrinsicHeight = _METHOD(handle_cache.drawable.class, "getIntrinsicHeight", "()I");
	int height = (*env)->CallIntMethod(env, paintable->drawable, getIntrinsicHeight);
	return height > 0 ? height : 0;
}

static void java_paintable_init(JavaPaintable *java_paintable)
{
}

static void java_paintable_paintable_init(GdkPaintableInterface *iface)
{
	iface->snapshot = java_paintable_snapshot;
	iface->get_intrinsic_height = java_paintable_get_intrinsic_height;
	iface->get_intrinsic_width = java_paintable_get_intrinsic_width;
}

G_DEFINE_TYPE_WITH_CODE(JavaPaintable, java_paintable, G_TYPE_OBJECT,
                        G_IMPLEMENT_INTERFACE(GDK_TYPE_PAINTABLE, java_paintable_paintable_init))

static void java_paintable_dispose(GObject *object)
{
	JavaPaintable *java_paintable = JAVA_PAINTABLE(object);
	JNIEnv *env = get_jni_env();
	if (java_paintable->drawable)
		_WEAK_UNREF(java_paintable->drawable);
	java_paintable->drawable = NULL;
	G_OBJECT_CLASS(java_paintable_parent_class)->dispose(object);
}

static void java_paintable_finalize(GObject *object)
{
	atl_test_drawable_freed(G_OBJECT_TYPE_NAME(object));
	G_OBJECT_CLASS(java_paintable_parent_class)->finalize(object);
}

static void java_paintable_class_init(JavaPaintableClass *class)
{
	G_OBJECT_CLASS(class)->dispose = java_paintable_dispose;
	G_OBJECT_CLASS(class)->finalize = java_paintable_finalize;
}

GdkPaintable *java_paintable_new(jweak drawable)
{
	JavaPaintable *paintable = g_object_new(java_paintable_get_type(), NULL);
	paintable->drawable = drawable;
	return GDK_PAINTABLE(paintable);
}

