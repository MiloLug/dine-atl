#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_drawable_Drawable.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/gfx.h"

#include "paint.h"

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
