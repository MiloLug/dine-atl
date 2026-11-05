#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_atl_ATLTestHooks.h"
#include "src/shared/bitmap.h"
#ifdef ATL_LIBANDROID
#include "src/libandroid/bitmap.h"
#endif

static struct gfx_bitmap *bitmap_of(JNIEnv *env, jobject bitmap)
{
	return _PTR(_GET_LONG_FIELD(bitmap, "native_bitmap"));
}

static int row_bytes(const struct gfx_bitmap *bitmap)
{
	return bitmap->width * bitmap_bytes_per_pixel(bitmap->config);
}

// a lock stays held until unlockPixels
JNIEXPORT jbyteArray JNICALL Java_android_atl_ATLTestHooks_lockPixels(JNIEnv *env, jclass class, jobject bitmap_object)
{
	void *pixels;
#ifdef ATL_LIBANDROID
	if (AndroidBitmap_lockPixels(env, bitmap_object, &pixels))
		return NULL;
	struct gfx_bitmap *bitmap = bitmap_of(env, bitmap_object);
#else
	struct gfx_bitmap *bitmap = bitmap_of(env, bitmap_object);
	if (!bitmap)
		return NULL;
	pixels = malloc((size_t)row_bytes(bitmap) * bitmap->height ?: 1);
	bitmap_pack(bitmap, pixels, row_bytes(bitmap));
#endif
	size_t size = (size_t)row_bytes(bitmap) * bitmap->height;
	jbyteArray array = (*env)->NewByteArray(env, size);
	(*env)->SetByteArrayRegion(env, array, 0, size, pixels);
#ifndef ATL_LIBANDROID
	free(pixels);
#endif
	return array;
}

JNIEXPORT void JNICALL Java_android_atl_ATLTestHooks_unlockPixels(JNIEnv *env, jclass class, jobject bitmap_object, jbyteArray pixels)
{
#ifdef ATL_LIBANDROID
	uint8_t *buffer = _PTR(_GET_LONG_FIELD(bitmap_object, "bytes"));
	if (buffer) // sized by the lock, not the bitmap, which may be recycled since
		(*env)->GetByteArrayRegion(env, pixels, 0, (*env)->GetArrayLength(env, pixels), (jbyte *)buffer);
	AndroidBitmap_unlockPixels(env, bitmap_object);
#else
	struct gfx_bitmap *bitmap = bitmap_of(env, bitmap_object);
	jbyte *bytes = (*env)->GetByteArrayElements(env, pixels, NULL);
	bitmap_unpack(bitmap, (const uint8_t *)bytes, row_bytes(bitmap));
	(*env)->ReleaseByteArrayElements(env, pixels, bytes, JNI_ABORT);
#endif
}

JNIEXPORT jlong JNICALL Java_android_atl_ATLTestHooks_nativeNewViewCanvas(JNIEnv *env, jclass class)
{
	return _INTPTR(gfx_canvas_new());
}

JNIEXPORT jboolean JNICALL Java_android_atl_ATLTestHooks_nativeDrawViewCanvas(JNIEnv *env, jclass class, jlong canvas, jobject target)
{
	return gfx_test_render_view(_PTR(canvas), bitmap_of(env, target));
}
