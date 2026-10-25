#include <graphene.h>
#include <gtk/gtk.h>
#include <pango/pango.h>

#include "../defines.h"
#include "AndroidPaint.h"

#include "../generated_headers/android_atl_ATLCanvas.h"

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1drawBitmap__JJIIIIJ(JNIEnv *env, jclass this_class, jlong snapshot_ptr, jlong texture_ptr, jint x, jint y, jint width, jint height, jlong paint_ptr)
{
	GdkSnapshot *snapshot = (GdkSnapshot *)_PTR(snapshot_ptr);
	GdkTexture *texture = GDK_TEXTURE(_PTR(texture_ptr));
	struct AndroidPaint *paint = _PTR(paint_ptr);
	if (paint->use_color_filter) {
		gtk_snapshot_push_color_matrix(snapshot, &paint->color_matrix, &paint->color_offset);
	}
	gtk_snapshot_append_texture(snapshot, texture, &GRAPHENE_RECT_INIT(x, y, width, height));
	if (paint->use_color_filter)
		gtk_snapshot_pop(snapshot);
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1drawBitmap__JJIIIIIIIIJ(JNIEnv *env, jclass this_class, jlong snapshot_ptr, jlong texture_ptr, jint x, jint y, jint width, jint height, jint src_x, jint src_y, jint src_width, jint src_height, jlong paint_ptr)
{
	GdkSnapshot *snapshot = (GdkSnapshot *)_PTR(snapshot_ptr);
	GdkTexture *texture = GDK_TEXTURE(_PTR(texture_ptr));
	struct AndroidPaint *paint = _PTR(paint_ptr);
	if (paint->use_color_filter) {
		gtk_snapshot_push_color_matrix(snapshot, &paint->color_matrix, &paint->color_offset);
	}
	gtk_snapshot_push_clip(snapshot, &GRAPHENE_RECT_INIT(x, y, width, height));
	float xscale = width / (float)src_width;
	float yscale = height / (float)src_height;
	float scaled_width = gdk_texture_get_width(texture) * xscale;
	float scaled_height = gdk_texture_get_height(texture) * yscale;
	gtk_snapshot_append_texture(snapshot, texture, &GRAPHENE_RECT_INIT(x - src_x * xscale, y - src_y * yscale, scaled_width, scaled_height));
	gtk_snapshot_pop(snapshot);
	if (paint->use_color_filter)
		gtk_snapshot_pop(snapshot);
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1drawPath(JNIEnv *env, jclass this_class, jlong snapshot_ptr, jlong path_ptr, jint fill_rule, jlong paint_ptr)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(_PTR(snapshot_ptr));
	GskPath *path = _PTR(path_ptr);
	struct AndroidPaint *paint = _PTR(paint_ptr);
	if (paint->use_color_filter)
		gtk_snapshot_push_color_matrix(snapshot, &paint->color_matrix, &paint->color_offset);
	if (paint->is_stroke) {
		gtk_snapshot_append_stroke(snapshot, path, paint->gsk_stroke, &paint->color);
	}
	if (paint->is_fill) {
		gtk_snapshot_append_fill(snapshot, path, fill_rule, &paint->color);
	}
	if (paint->use_color_filter)
		gtk_snapshot_pop(snapshot);
}

extern GtkWidget *window;

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1drawText(JNIEnv *env, jclass this_class, jlong snapshot_ptr, jstring text, jfloat x, jfloat y, jlong paint_ptr)
{
	GdkSnapshot *snapshot = GTK_SNAPSHOT(_PTR(snapshot_ptr));
	struct AndroidPaint *paint = _PTR(paint_ptr);
	PangoLayout *layout = pango_layout_new(gtk_widget_get_pango_context(window));
	pango_layout_set_font_description(layout, paint->font);
	const char *str = (*env)->GetStringUTFChars(env, text, NULL);
	pango_layout_set_text(layout, str, -1);
	(*env)->ReleaseStringUTFChars(env, text, str);
	PangoRectangle rect;
	pango_layout_get_pixel_extents(layout, NULL, &rect);
	y -= (float)pango_layout_get_baseline(layout) / PANGO_SCALE;
	if (paint->alignment == PANGO_ALIGN_CENTER)
		x -= rect.width / 2.f;
	else if (paint->alignment == PANGO_ALIGN_RIGHT)
		x -= rect.width;
	if (paint->use_color_filter)
		gtk_snapshot_push_color_matrix(snapshot, &paint->color_matrix, &paint->color_offset);
	gtk_snapshot_translate(snapshot, &GRAPHENE_POINT_INIT(x, y));
	gtk_snapshot_append_layout(snapshot, layout, &paint->color);
	gtk_snapshot_translate(snapshot, &GRAPHENE_POINT_INIT(-x, -y));
	if (paint->use_color_filter)
		gtk_snapshot_pop(snapshot);
	g_object_unref(layout);
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1clipRect(JNIEnv *env, jclass this_class, jlong snapshot_ptr, jfloat left, jfloat top, jfloat right, jfloat bottom)
{
	GdkSnapshot *snapshot = GTK_SNAPSHOT(_PTR(snapshot_ptr));
	gtk_snapshot_push_clip(snapshot, &GRAPHENE_RECT_INIT(left, top, right - left, bottom - top));
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1pop(JNIEnv *env, jclass this_class, jlong snapshot_ptr, jint count)
{
	GdkSnapshot *snapshot = GTK_SNAPSHOT(_PTR(snapshot_ptr));
	while (count--)
		gtk_snapshot_pop(snapshot);
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1drawRenderNode(JNIEnv *env, jclass this_class, jlong snapshot_ptr, jlong node_ptr)
{
	GdkSnapshot *snapshot = GTK_SNAPSHOT(_PTR(snapshot_ptr));
	GskRenderNode *node = _PTR(node_ptr);
	gtk_snapshot_append_node(snapshot, node);
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1clipPath(JNIEnv *env, jclass this_class, jlong snapshot_ptr, jlong path_ptr, jint fill_rule)
{
	GdkSnapshot *snapshot = GTK_SNAPSHOT(_PTR(snapshot_ptr));
	GskPath *path = _PTR(path_ptr);
	gtk_snapshot_push_mask(snapshot, GSK_MASK_MODE_ALPHA);
	gtk_snapshot_append_fill(snapshot, path, fill_rule, &(GdkRGBA){1, 1, 1, 1});
	gtk_snapshot_pop(snapshot);
}
