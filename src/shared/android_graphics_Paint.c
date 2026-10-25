#include <math.h>
#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_Paint.h"
#include "src/slots/gfx.h"

#include "text.h"

JNIEXPORT jlong JNICALL Java_android_graphics_Paint_native_1create(JNIEnv *env, jclass clazz)
{
	return _INTPTR(gfx_paint_new());
}

JNIEXPORT jlong JNICALL Java_android_graphics_Paint_native_1clone(JNIEnv *env, jclass clazz, jlong paint_ptr)
{
	return _INTPTR(gfx_paint_clone(_PTR(paint_ptr)));
}

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1recycle(JNIEnv *env, jclass clazz, jlong paint_ptr)
{
	gfx_paint_free(_PTR(paint_ptr));
}

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1set_1color(JNIEnv *env, jclass clazz, jlong paint_ptr, jint color)
{
	gfx_paint_set_color(_PTR(paint_ptr), color);
}

JNIEXPORT jint JNICALL Java_android_graphics_Paint_native_1get_1color(JNIEnv *env, jclass clazz, jlong paint_ptr)
{
	return gfx_paint_get_color(_PTR(paint_ptr));
}

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1set_1alpha(JNIEnv *env, jclass clazz, jlong paint_ptr, jint alpha)
{
	gfx_paint_set_alpha(_PTR(paint_ptr), alpha & 0xFF);
}

JNIEXPORT jint JNICALL Java_android_graphics_Paint_native_1get_1alpha(JNIEnv *env, jclass clazz, jlong paint_ptr)
{
	return gfx_paint_get_alpha(_PTR(paint_ptr));
}

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1set_1style(JNIEnv *env, jclass clazz, jlong paint_ptr, jint style)
{
	gfx_paint_set_style(_PTR(paint_ptr), style);
}

JNIEXPORT jint JNICALL Java_android_graphics_Paint_native_1get_1style(JNIEnv *env, jclass clazz, jlong paint_ptr)
{
	return gfx_paint_get_style(_PTR(paint_ptr));
}

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1set_1stroke_1width(JNIEnv *env, jclass clazz, jlong paint_ptr, jfloat width)
{
	gfx_paint_set_stroke_width(_PTR(paint_ptr), width);
}

JNIEXPORT jfloat JNICALL Java_android_graphics_Paint_native_1get_1stroke_1width(JNIEnv *env, jclass clazz, jlong paint_ptr)
{
	return gfx_paint_get_stroke_width(_PTR(paint_ptr));
}

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1set_1stroke_1cap(JNIEnv *env, jclass clazz, jlong paint_ptr, jint cap)
{
	gfx_paint_set_stroke_cap(_PTR(paint_ptr), cap);
}

JNIEXPORT jint JNICALL Java_android_graphics_Paint_native_1get_1stroke_1cap(JNIEnv *env, jclass clazz, jlong paint_ptr)
{
	return gfx_paint_get_stroke_cap(_PTR(paint_ptr));
}

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1set_1stroke_1join(JNIEnv *env, jclass clazz, jlong paint_ptr, jint join)
{
	gfx_paint_set_stroke_join(_PTR(paint_ptr), join);
}

JNIEXPORT jint JNICALL Java_android_graphics_Paint_native_1get_1stroke_1join(JNIEnv *env, jclass clazz, jlong paint_ptr)
{
	return gfx_paint_get_stroke_join(_PTR(paint_ptr));
}

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1set_1text_1size(JNIEnv *env, jclass clazz, jlong paint_ptr, jfloat size)
{
	gfx_paint_set_text_size(_PTR(paint_ptr), size);
}

JNIEXPORT jfloat JNICALL Java_android_graphics_Paint_native_1get_1text_1size(JNIEnv *env, jclass clazz, jlong paint_ptr)
{
	return gfx_paint_get_text_size(_PTR(paint_ptr));
}

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1set_1text_1align(JNIEnv *env, jclass clazz, jlong paint_ptr, jint align)
{
	gfx_paint_set_text_align(_PTR(paint_ptr), align);
}

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1set_1color_1filter(JNIEnv *env, jclass clazz, jlong paint_ptr, jint mode, jint color)
{
	gfx_paint_set_color_filter(_PTR(paint_ptr), mode, color);
}

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1set_1typeface(JNIEnv *env, jclass clazz, jlong paint_ptr, jint family, jint style)
{
	gfx_paint_set_typeface(_PTR(paint_ptr), family, style);
}

// Java checked the range
static uint16_t *units(JNIEnv *env, jstring text, jint start, jint end)
{
	uint16_t *buffer = malloc(((end - start) ?: 1) * sizeof(*buffer));
	(*env)->GetStringRegion(env, text, start, end - start, buffer);
	return buffer;
}

JNIEXPORT jfloat JNICALL Java_android_graphics_Paint_native_1measure_1text(JNIEnv *env, jclass clazz, jlong paint_ptr, jstring text, jint start, jint end)
{
	uint16_t *buffer = units(env, text, start, end);
	float width = gfx_text_measure(_PTR(paint_ptr), buffer, end - start);
	free(buffer);
	return width;
}

// a cluster's width goes to its first unit, the rest get 0 (a pair's low surrogate)
JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1get_1text_1widths(JNIEnv *env, jclass clazz, jlong paint_ptr, jstring text, jint start, jint end, jfloatArray widths_array)
{
	size_t len = end - start;
	uint16_t *buffer = units(env, text, start, end);
	float *x = malloc((len + 1) * sizeof(*x)), *widths = malloc((len ?: 1) * sizeof(*widths));
	gfx_text_carets(_PTR(paint_ptr), buffer, len, x);
	for (size_t i = 0; i < len;) {
		size_t next = i + 1;
		while (next < len && x[next] == x[i])
			widths[next++] = 0;
		widths[i] = x[next] - x[i];
		i = next;
	}
	(*env)->SetFloatArrayRegion(env, widths_array, 0, len, widths);
	free(widths);
	free(x);
	free(buffer);
}

// rounded outward, as Skia's roundOut
JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1get_1text_1bounds(JNIEnv *env, jclass clazz, jlong paint_ptr, jstring text, jint start, jint end, jobject bounds)
{
	uint16_t *buffer = units(env, text, start, end);
	struct gfx_rect rect;
	gfx_text_bounds(_PTR(paint_ptr), buffer, end - start, &rect);
	free(buffer);
	_SET_INT_FIELD(bounds, "left", floorf(rect.left));
	_SET_INT_FIELD(bounds, "top", floorf(rect.top));
	_SET_INT_FIELD(bounds, "right", ceilf(rect.right));
	_SET_INT_FIELD(bounds, "bottom", ceilf(rect.bottom));
}

// the recommended line spacing
JNIEXPORT jfloat JNICALL Java_android_graphics_Paint_native_1get_1font_1metrics(JNIEnv *env, jclass clazz, jlong paint_ptr, jobject metrics)
{
	struct gfx_font_metrics font;
	gfx_text_font_metrics(_PTR(paint_ptr), &font);
	if (metrics) {
		_SET_FLOAT_FIELD(metrics, "top", font.top);
		_SET_FLOAT_FIELD(metrics, "ascent", font.ascent);
		_SET_FLOAT_FIELD(metrics, "descent", font.descent);
		_SET_FLOAT_FIELD(metrics, "bottom", font.bottom);
		_SET_FLOAT_FIELD(metrics, "leading", font.leading);
	}
	return font.descent - font.ascent + font.leading;
}

JNIEXPORT jint JNICALL Java_android_graphics_Paint_native_1get_1font_1metrics_1int(JNIEnv *env, jclass clazz, jlong paint_ptr, jobject metrics)
{
	struct atl_font_metrics_int font;
	atl_font_metrics_int(_PTR(paint_ptr), &font);
	if (metrics) {
		_SET_INT_FIELD(metrics, "top", font.top);
		_SET_INT_FIELD(metrics, "ascent", font.ascent);
		_SET_INT_FIELD(metrics, "descent", font.descent);
		_SET_INT_FIELD(metrics, "bottom", font.bottom);
		_SET_INT_FIELD(metrics, "leading", font.leading);
	}
	return font.descent - font.ascent + font.leading;
}
