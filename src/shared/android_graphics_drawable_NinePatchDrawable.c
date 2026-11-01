#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_drawable_NinePatchDrawable.h"
#include "src/slots/gfx.h"

#include "ninepatch.h"

// 0: not a valid chunk
JNIEXPORT jlong JNICALL Java_android_graphics_drawable_NinePatchDrawable_nativeCreate(JNIEnv *env, jclass class, jlong bitmap, jbyteArray chunk_array)
{
	jsize size = (*env)->GetArrayLength(env, chunk_array);
	jbyte *chunk = (*env)->GetByteArrayElements(env, chunk_array, NULL);
	struct gfx_ninepatch *ninepatch = ninepatch_parse(chunk, size);
	(*env)->ReleaseByteArrayElements(env, chunk_array, chunk, JNI_ABORT);
	return ninepatch ? _INTPTR(gfx_ninepatch_drawable_new(_PTR(bitmap), ninepatch)) : 0;
}

JNIEXPORT void JNICALL Java_android_graphics_drawable_NinePatchDrawable_nativeSetColorFilter(JNIEnv *env, jclass class, jlong drawable, jint mode, jint color)
{
	gfx_drawable_set_color_filter(_PTR(drawable), mode, color);
}
