#include <stdlib.h>
#include <string.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_Bitmap.h"
#include "src/api-impl-jni/util.h"
#include "src/shared/pixels.h"
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

JNIEXPORT jlong JNICALL Java_android_graphics_Bitmap_native_1canvas_1new(JNIEnv *env, jclass class)
{
	return _INTPTR(gfx_canvas_new());
}

JNIEXPORT void JNICALL Java_android_graphics_Bitmap_native_1render(JNIEnv *env, jclass class, jlong bitmap, jlong canvas)
{
	gfx_bitmap_render(_PTR(bitmap), _PTR(canvas));
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

static uint16_t half(float value)
{
	union {
		float f;
		uint32_t u;
	} bits = {.f = value};
	uint32_t sign = (bits.u >> 16) & 0x8000;
	int exponent = (int)((bits.u >> 23) & 0xff) - 127 + 15;
	uint32_t mantissa = bits.u & 0x7fffff;
	if (exponent <= 0)
		return sign;
	if (exponent >= 31)
		return sign | 0x7c00;
	return sign | exponent << 10 | (mantissa + 0x1000) >> 13;
}

/* The bytes AOSP's copyPixelsToBuffer writes: Skia's layout of the config, premultiplied. */
JNIEXPORT void JNICALL Java_android_graphics_Bitmap_native_1copy_1to_1buffer(JNIEnv *env, jclass class, jlong bitmap_ptr, jobject buffer, jint config, jint row_bytes)
{
	struct gfx_bitmap *bitmap = _PTR(bitmap_ptr);
	int width = gfx_bitmap_get_width(bitmap), height = gfx_bitmap_get_height(bitmap);
	uint32_t *argb = malloc((size_t)width * height * sizeof(*argb));
	gfx_bitmap_read(bitmap, 0, 0, width, height, argb, width);
	jarray array_ref;
	jbyte *array;
	uint8_t *data = get_nio_buffer(env, buffer, &array_ref, &array);
	for (int y = 0; y < height; y++) {
		uint8_t *row = data + (size_t)y * row_bytes;
		for (int x = 0; x < width; x++) {
			uint32_t p = argb_premultiply(argb[(size_t)y * width + x]);
			uint8_t a = p >> 24, r = p >> 16, g = p >> 8, b = p;
			switch (config) {
			case GFX_BITMAP_ALPHA_8:
				row[x] = a;
				break;
			case GFX_BITMAP_RGB_565: {
				uint16_t v = (r >> 3) << 11 | (g >> 2) << 5 | b >> 3;
				memcpy(row + x * 2, &v, 2);
				break;
			}
			case GFX_BITMAP_ARGB_4444: {
				uint16_t v = (r >> 4) << 12 | (g >> 4) << 8 | (b >> 4) << 4 | a >> 4;
				memcpy(row + x * 2, &v, 2);
				break;
			}
			case GFX_BITMAP_RGBA_F16: {
				uint16_t v[4] = {half(r / 255.f), half(g / 255.f), half(b / 255.f), half(a / 255.f)};
				memcpy(row + x * 8, v, 8);
				break;
			}
			default:
				row[x * 4] = r, row[x * 4 + 1] = g, row[x * 4 + 2] = b, row[x * 4 + 3] = a;
			}
		}
	}
	release_nio_buffer(env, array_ref, array);
	free(argb);
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

JNIEXPORT void JNICALL Java_android_graphics_Bitmap_native_1recycle(JNIEnv *env, jclass class, jlong bitmap, jlong canvas)
{
	if (bitmap)
		gfx_bitmap_unref(_PTR(bitmap));
	if (canvas)
		gfx_canvas_free(_PTR(canvas));
}
