#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_drawable_Drawable.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/gfx.h"
#include "src/slots/loop.h"

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

static void drawable_unref(void *drawable)
{
	gfx_drawable_unref(drawable);
}

// finalize calls it on the FinalizerDaemon thread; after loop_main_quit the drawable stays allocated
JNIEXPORT void JNICALL Java_android_graphics_drawable_Drawable_native_1unref(JNIEnv *env, jobject this, jlong paintable_ptr)
{
	loop_main_post(drawable_unref, _PTR(paintable_ptr));
}

JNIEXPORT void JNICALL Java_android_graphics_drawable_Drawable_native_1invalidate(JNIEnv *env, jobject this, jlong paintable_ptr)
{
	if (paintable_ptr)
		gfx_drawable_invalidate(_PTR(paintable_ptr));
}
