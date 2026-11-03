#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_drawable_Drawable.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/gfx.h"

JNIEXPORT jlong JNICALL Java_android_graphics_drawable_Drawable_native_1constructor(JNIEnv *env, jobject this)
{
	if (handle_cache.drawable.draw == _METHOD_OF(this, "draw", "(Landroid/graphics/Canvas;)V"))
		return 0;
	return _INTPTR(gfx_drawable_new((struct atl_ref *)_WEAK_REF(this)));
}

JNIEXPORT void JNICALL Java_android_graphics_drawable_Drawable_native_1ref(JNIEnv *env, jobject this, jlong paintable_ptr)
{
	gfx_drawable_ref(_PTR(paintable_ptr));
}

JNIEXPORT void JNICALL Java_android_graphics_drawable_Drawable_native_1unref(JNIEnv *env, jobject this, jlong paintable_ptr)
{
	gfx_drawable_unref(_PTR(paintable_ptr));
}

struct gfx_color_filter atl_color_filter(JNIEnv *env, jint mode, jint color, jfloatArray matrix); // android_graphics_Paint.c

JNIEXPORT void JNICALL Java_android_graphics_drawable_Drawable_native_1setColorFilter(JNIEnv *env, jclass class, jlong paintable_ptr, jint mode, jint color, jfloatArray matrix)
{
	struct gfx_color_filter filter = atl_color_filter(env, mode, color, matrix);
	gfx_drawable_set_color_filter(_PTR(paintable_ptr), &filter);
}

JNIEXPORT void JNICALL Java_android_graphics_drawable_Drawable_native_1invalidate(JNIEnv *env, jobject this, jlong paintable_ptr)
{
	if (paintable_ptr)
		gfx_drawable_invalidate(_PTR(paintable_ptr));
}

// paintable 0: the Drawable has no native one (the records backends make none for a Drawable
// without its own draw())
JNIEXPORT void JNICALL Java_android_graphics_drawable_Drawable_native_1draw(JNIEnv *env, jobject this, jlong paintable_ptr, jlong canvas_ptr, jint width, jint height)
{
	if (paintable_ptr && canvas_ptr)
		gfx_canvas_draw_drawable(_PTR(canvas_ptr), _PTR(paintable_ptr), width, height);
}
