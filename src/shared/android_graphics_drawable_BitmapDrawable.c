#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_drawable_BitmapDrawable.h"
#include "src/slots/gfx.h"

JNIEXPORT jlong JNICALL Java_android_graphics_drawable_BitmapDrawable_native_1paintable(JNIEnv *env, jclass class, jlong bitmap)
{
	return _INTPTR(gfx_bitmap_drawable_new(_PTR(bitmap)));
}
