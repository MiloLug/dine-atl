#ifndef BITMAP_H
#define BITMAP_H

#include <jni.h>
#include <stdint.h>

// the NDK's AndroidBitmapFormat, as Bitmap.Config's android_memory_format gives it
enum {
	ANDROID_BITMAP_FORMAT_RGBA_8888 = 1,
	ANDROID_BITMAP_FORMAT_RGB_565 = 4,
	ANDROID_BITMAP_FORMAT_RGBA_4444 = 7,
	ANDROID_BITMAP_FORMAT_A_8 = 8,
	ANDROID_BITMAP_FORMAT_RGBA_F16 = 9,
	ANDROID_BITMAP_FORMAT_RGBA_1010102 = 10,
};

struct AndroidBitmapInfo {
	uint32_t width;
	uint32_t height;
	uint32_t stride;
	int32_t format;
	uint32_t flags;
};

int AndroidBitmap_getInfo(JNIEnv *env, jobject bitmap,
                          struct AndroidBitmapInfo *info);
int AndroidBitmap_lockPixels(JNIEnv *env, jobject bitmap, void **pixels);
int AndroidBitmap_unlockPixels(JNIEnv *env, jobject bitmap);

#endif
