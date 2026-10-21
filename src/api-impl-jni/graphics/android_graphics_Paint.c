#include <glib.h>
#include <gsk/gsk.h>
#include <gtk/gtk.h>

#include "../defines.h"
#include "AndroidPaint.h"
#include "pango/pango-font.h"

#include "../generated_headers/android_graphics_Paint.h"

/* text measurement waits for the gfx slot (M5); the paint state is in src/shared/ */

extern GtkWidget *window;

JNIEXPORT void JNICALL Java_android_graphics_Paint_native_1get_1text_1bounds(JNIEnv *env, jclass clazz, jlong paint_ptr, jstring text_ptr, jobject bounds)
{
	struct AndroidPaint *paint = _PTR(paint_ptr);
	PangoLayout *layout = pango_layout_new(gtk_widget_get_pango_context(window));
	pango_layout_set_font_description(layout, paint->font);
	const char *str = (*env)->GetStringUTFChars(env, text_ptr, NULL);
	pango_layout_set_text(layout, str, -1);
	(*env)->ReleaseStringUTFChars(env, text_ptr, str);
	PangoRectangle rect;
	pango_layout_get_pixel_extents(layout, NULL, &rect);
	rect.y -= (float)pango_layout_get_baseline(layout) / PANGO_SCALE;
	if (paint->alignment == PANGO_ALIGN_CENTER)
		rect.x -= rect.width / 2.f;
	else if (paint->alignment == PANGO_ALIGN_RIGHT)
		rect.x -= rect.width;
	_SET_INT_FIELD(bounds, "left", rect.x);
	_SET_INT_FIELD(bounds, "top", rect.y);
	_SET_INT_FIELD(bounds, "right", rect.x + rect.width);
	_SET_INT_FIELD(bounds, "bottom", rect.y + rect.height);
	g_object_unref(layout);
}

JNIEXPORT jfloat JNICALL Java_android_graphics_Paint_native_1get_1font_1metrics(JNIEnv *env, jclass clazz, jlong paint_ptr, jobject metrics)
{
	struct AndroidPaint *paint = _PTR(paint_ptr);
	PangoContext *context = gtk_widget_get_pango_context(window);
	PangoFontMetrics *font_metrics = pango_context_get_metrics(context, paint->font, NULL);
	float height = (float)pango_font_metrics_get_height(font_metrics) / PANGO_SCALE;
	if (metrics) {
		float ascent = (float)pango_font_metrics_get_ascent(font_metrics) / PANGO_SCALE;
		float descent = (float)pango_font_metrics_get_descent(font_metrics) / PANGO_SCALE;
		_SET_FLOAT_FIELD(metrics, "top", -ascent);
		_SET_FLOAT_FIELD(metrics, "ascent", -ascent);
		_SET_FLOAT_FIELD(metrics, "descent", descent);
		_SET_FLOAT_FIELD(metrics, "bottom", descent);
		_SET_FLOAT_FIELD(metrics, "leading", height - ascent - descent);
	}
	pango_font_metrics_unref(font_metrics);
	return height;
}

JNIEXPORT jint JNICALL Java_android_graphics_Paint_native_1get_1font_1metrics_1int(JNIEnv *env, jclass clazz, jlong paint_ptr, jobject metrics)
{
	struct AndroidPaint *paint = _PTR(paint_ptr);
	PangoContext *context = gtk_widget_get_pango_context(window);
	PangoFontMetrics *font_metrics = pango_context_get_metrics(context, paint->font, NULL);
	int height = ceil((float)pango_font_metrics_get_height(font_metrics) / PANGO_SCALE);
	if (metrics) {
		int ascent = ceil((float)pango_font_metrics_get_ascent(font_metrics) / PANGO_SCALE);
		int descent = ceil((float)pango_font_metrics_get_descent(font_metrics) / PANGO_SCALE);
		_SET_INT_FIELD(metrics, "top", -ascent);
		_SET_INT_FIELD(metrics, "ascent", -ascent);
		_SET_INT_FIELD(metrics, "descent", descent);
		_SET_INT_FIELD(metrics, "bottom", descent);
		_SET_INT_FIELD(metrics, "leading", height - ascent - descent);
	}
	pango_font_metrics_unref(font_metrics);
	return height;
}
