#include <jni.h>
#include <stdio.h>
#include <stdlib.h>

// FIXME: put the header in a common place
#include "../api-impl-jni/defines.h"
#include "../shared/bitmap.h"
#include "bitmap.h"

#define ANDROID_BITMAP_RESULT_SUCCESS 0
#define ANDROID_BITMAP_RESULT_JNI_EXCEPTION -2

/* app threads that never return to Java keep every local ref they make */
static int config_int(JNIEnv *env, jobject bitmap, const char *field)
{
	jobject config = _GET_OBJ_FIELD(bitmap, "config", "Landroid/graphics/Bitmap$Config;");
	int value = _GET_INT_FIELD(config, field);
	(*env)->DeleteLocalRef(env, config);
	return value;
}

static struct gfx_bitmap *native_of(JNIEnv *env, jobject bitmap)
{
	return _PTR((*env)->CallLongMethod(env, bitmap, _METHOD_OF(bitmap, "getNativeInstance", "()J")));
}

int AndroidBitmap_getInfo(JNIEnv *env, jobject bitmap, struct AndroidBitmapInfo *info)
{
	info->width = _GET_INT_FIELD(bitmap, "width");
	info->height = _GET_INT_FIELD(bitmap, "height");
	info->stride = _GET_INT_FIELD(bitmap, "stride");
	info->format = config_int(env, bitmap, "android_memory_format");
	return ANDROID_BITMAP_RESULT_SUCCESS;
}

// a copy in the config's NDK format until unlockPixels writes it back
int AndroidBitmap_lockPixels(JNIEnv *env, jobject bitmap, void **pixels)
{
	struct gfx_bitmap *native = native_of(env, bitmap);
	if (!native) // recycled
		return ANDROID_BITMAP_RESULT_JNI_EXCEPTION;
	int stride = _GET_INT_FIELD(bitmap, "stride");
	uint8_t *buffer = malloc((size_t)stride * native->height);
	bitmap_pack(native, buffer, stride);
	_SET_LONG_FIELD(bitmap, "bytes", _INTPTR(buffer));
	*pixels = buffer;
	return ANDROID_BITMAP_RESULT_SUCCESS;
}

int AndroidBitmap_unlockPixels(JNIEnv *env, jobject bitmap)
{
	uint8_t *buffer = _PTR(_GET_LONG_FIELD(bitmap, "bytes"));
	if (!buffer) {
		fprintf(stderr, "AndroidBitmap_unlockPixels: no pixels; was AndroidBitmap_lockPixels called?\n");
		return -1; // ANDROID_BITMAP_RESULT_BAD_PARAMETER
	}
	struct gfx_bitmap *native = native_of(env, bitmap);
	if (native) // else recycled while locked
		bitmap_unpack(native, buffer, _GET_INT_FIELD(bitmap, "stride"));
	free(buffer);
	_SET_LONG_FIELD(bitmap, "bytes", 0);
	return native ? ANDROID_BITMAP_RESULT_SUCCESS : ANDROID_BITMAP_RESULT_JNI_EXCEPTION;
}
