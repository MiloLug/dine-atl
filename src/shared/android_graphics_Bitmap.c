#include <stdlib.h>
#include <string.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_Bitmap.h"
#include "src/api-impl-jni/util.h"
#include "src/shared/bitmap.h"
#include "src/slots/gfx.h"

JNIEXPORT jlong JNICALL Java_android_graphics_Bitmap_native_1new(JNIEnv *env, jclass class, jint width, jint height, jint config)
{
	return _INTPTR(gfx_bitmap_new(width, height, config));
}

JNIEXPORT jlong JNICALL Java_android_graphics_Bitmap_native_1copy(JNIEnv *env, jclass class, jlong bitmap, jint config)
{
	return _INTPTR(gfx_bitmap_copy(_PTR(bitmap), config));
}

JNIEXPORT jint JNICALL Java_android_graphics_Bitmap_native_1get_1width(JNIEnv *env, jclass class, jlong bitmap)
{
	return gfx_bitmap_get_width(_PTR(bitmap));
}

JNIEXPORT jint JNICALL Java_android_graphics_Bitmap_native_1get_1height(JNIEnv *env, jclass class, jlong bitmap)
{
	return gfx_bitmap_get_height(_PTR(bitmap));
}

JNIEXPORT jlong JNICALL Java_android_graphics_Bitmap_native_1canvas_1new(JNIEnv *env, jclass class, jlong bitmap)
{
	return _INTPTR(gfx_canvas_new_bitmap(_PTR(bitmap)));
}

JNIEXPORT void JNICALL Java_android_graphics_Bitmap_native_1canvas_1free(JNIEnv *env, jclass class, jlong canvas)
{
	gfx_canvas_free(_PTR(canvas));
}

JNIEXPORT void JNICALL Java_android_graphics_Bitmap_native_1erase(JNIEnv *env, jclass class, jlong bitmap, jint color)
{
	gfx_bitmap_erase(_PTR(bitmap), color);
}

// Bitmap checked the rect and the array's bounds
JNIEXPORT void JNICALL Java_android_graphics_Bitmap_native_1get_1pixels(JNIEnv *env, jclass class, jlong bitmap, jintArray pixels, jint offset, jint stride, jint x, jint y, jint width, jint height)
{
	jint *array = (*env)->GetIntArrayElements(env, pixels, NULL);
	gfx_bitmap_read(_PTR(bitmap), x, y, width, height, (uint32_t *)(array + offset), stride);
	(*env)->ReleaseIntArrayElements(env, pixels, array, 0);
}

JNIEXPORT void JNICALL Java_android_graphics_Bitmap_native_1set_1pixels(JNIEnv *env, jclass class, jlong bitmap, jintArray pixels, jint offset, jint stride, jint x, jint y, jint width, jint height)
{
	jint *array = (*env)->GetIntArrayElements(env, pixels, NULL);
	gfx_bitmap_write(_PTR(bitmap), x, y, width, height, (const uint32_t *)(array + offset), stride);
	(*env)->ReleaseIntArrayElements(env, pixels, array, JNI_ABORT);
}

JNIEXPORT jint JNICALL Java_android_graphics_Bitmap_native_1get_1pixel(JNIEnv *env, jclass class, jlong bitmap, jint x, jint y)
{
	uint32_t argb;
	gfx_bitmap_read(_PTR(bitmap), x, y, 1, 1, &argb, 1);
	return argb;
}

JNIEXPORT void JNICALL Java_android_graphics_Bitmap_native_1set_1pixel(JNIEnv *env, jclass class, jlong bitmap, jint x, jint y, jint color)
{
	uint32_t argb = color;
	gfx_bitmap_write(_PTR(bitmap), x, y, 1, 1, &argb, 1);
}

// AOSP's copyPixelsToBuffer: the config's own layout, as the NDK's
JNIEXPORT void JNICALL Java_android_graphics_Bitmap_native_1copy_1to_1buffer(JNIEnv *env, jclass class, jlong bitmap, jobject buffer, jint row_bytes)
{
	jarray array_ref;
	jbyte *array;
	uint8_t *data = get_nio_buffer(env, buffer, &array_ref, &array);
	bitmap_pack(_PTR(bitmap), data, row_bytes);
	release_nio_buffer(env, array_ref, array);
}

JNIEXPORT jbyteArray JNICALL Java_android_graphics_Bitmap_native_1save_1to_1png(JNIEnv *env, jclass class, jlong bitmap)
{
	size_t size;
	void *png = gfx_bitmap_encode_png(_PTR(bitmap), &size);
	if (!png)
		return NULL;
	jbyteArray result = (*env)->NewByteArray(env, size);
	(*env)->SetByteArrayRegion(env, result, 0, size, png);
	free(png);
	return result;
}

JNIEXPORT void JNICALL Java_android_graphics_Bitmap_native_1recycle(JNIEnv *env, jclass class, jlong bitmap)
{
	gfx_bitmap_unref(_PTR(bitmap));
}
