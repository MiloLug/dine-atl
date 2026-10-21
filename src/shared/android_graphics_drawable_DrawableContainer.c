#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_drawable_DrawableContainer.h"
#include "src/slots/gfx.h"

JNIEXPORT jlong JNICALL Java_android_graphics_drawable_DrawableContainer_native_1constructor(JNIEnv *env, jobject this)
{
	return _INTPTR(gfx_container_new());
}

JNIEXPORT void JNICALL Java_android_graphics_drawable_DrawableContainer_native_1selectChild(JNIEnv *env, jobject this, jlong ptr, jlong child_ptr)
{
	gfx_container_select(_PTR(ptr), _PTR(child_ptr));
}
