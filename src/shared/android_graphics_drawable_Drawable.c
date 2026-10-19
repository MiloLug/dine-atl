#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_drawable_Drawable.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/gfx.h"

JNIEXPORT jlong JNICALL Java_android_graphics_drawable_Drawable_native_1constructor(JNIEnv *env, jobject this)
{
	if (handle_cache.drawable.draw == _METHOD(_CLASS(this), "draw", "(Landroid/graphics/Canvas;)V"))
		return 0;
	return _INTPTR(gfx_drawable_new((struct atl_ref *)_WEAK_REF(this)));
}
