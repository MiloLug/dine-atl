#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_Bitmap.h"
#include "src/slots/gfx.h"

JNIEXPORT jlong JNICALL Java_android_graphics_Bitmap_native_1new(JNIEnv *env, jclass class, jint width, jint height, jint config)
{
	return _INTPTR(gfx_bitmap_new(width, height, config));
}

JNIEXPORT jint JNICALL Java_android_graphics_Bitmap_native_1get_1width(JNIEnv *env, jclass class, jlong bitmap)
{
	return gfx_bitmap_get_width(_PTR(bitmap));
}

JNIEXPORT jint JNICALL Java_android_graphics_Bitmap_native_1get_1height(JNIEnv *env, jclass class, jlong bitmap)
{
	return gfx_bitmap_get_height(_PTR(bitmap));
}

JNIEXPORT jlong JNICALL Java_android_graphics_Bitmap_native_1ref_1texture(JNIEnv *env, jclass class, jlong bitmap)
{
	gfx_bitmap_ref(_PTR(bitmap));
	return bitmap;
}

// gsk: the snapshot a Canvas(Bitmap) draws into, a gfx canvas there too
JNIEXPORT void JNICALL Java_android_graphics_Bitmap_native_1recycle(JNIEnv *env, jclass class, jlong bitmap, jlong snapshot)
{
	if (bitmap)
		gfx_bitmap_unref(_PTR(bitmap));
	if (snapshot)
		gfx_canvas_free(_PTR(snapshot));
}
