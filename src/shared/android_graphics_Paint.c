#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_Paint.h"
#include "src/slots/gfx.h"

JNIEXPORT jlong JNICALL Java_android_graphics_Paint_native_1create(JNIEnv *env, jclass clazz)
{
	return _INTPTR(gfx_paint_new());
}

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1set_1color(JNIEnv *env, jclass clazz, jlong paint_ptr, jint color)
{
	gfx_paint_set_color(_PTR(paint_ptr), color);
}
