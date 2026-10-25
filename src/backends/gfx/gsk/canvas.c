#include <math.h>

#include <gtk/gtk.h>

#include "src/api-impl-jni/graphics/AndroidPaint.h"
#include "src/api-impl-jni/graphics/java_paintable.h"
#include "src/slots/gfx.h"

#include "canvas.h"

/* A canvas is a GtkSnapshot: the widget's snapshot during a draw, a Bitmap's or a RenderNode's. */

// the pushes each save level made, which its restore pops: one count per level, the base one first
static GArray *pushes(GtkSnapshot *snapshot)
{
	GArray *levels = g_object_get_data(G_OBJECT(snapshot), "atl-pushes");
	if (!levels) {
		levels = g_array_new(FALSE, TRUE, sizeof(guint));
		g_array_set_size(levels, 1);
		g_object_set_data_full(G_OBJECT(snapshot), "atl-pushes", levels, (GDestroyNotify)g_array_unref);
	}
	return levels;
}

void gsk_canvas_pushed(GtkSnapshot *snapshot)
{
	GArray *levels = pushes(snapshot);
	g_array_index(levels, guint, levels->len - 1)++;
}

static void push_color_filter(GtkSnapshot *snapshot, const struct AndroidPaint *paint)
{
	if (paint->use_color_filter)
		gtk_snapshot_push_color_matrix(snapshot, &paint->color_matrix, &paint->color_offset);
}

static void pop_color_filter(GtkSnapshot *snapshot, const struct AndroidPaint *paint)
{
	if (paint->use_color_filter)
		gtk_snapshot_pop(snapshot);
}

struct gfx_canvas *gfx_canvas_new(void)
{
	return (struct gfx_canvas *)gtk_snapshot_new();
}

void gfx_canvas_free(struct gfx_canvas *canvas)
{
	g_object_unref(canvas);
}

void gfx_canvas_save(struct gfx_canvas *canvas)
{
	gtk_snapshot_save(GTK_SNAPSHOT(canvas));
	g_array_set_size(pushes(GTK_SNAPSHOT(canvas)), pushes(GTK_SNAPSHOT(canvas))->len + 1);
}

void gfx_canvas_restore(struct gfx_canvas *canvas)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	GArray *levels = pushes(snapshot);
	if (levels->len > 1) {
		for (guint n = g_array_index(levels, guint, levels->len - 1); n; n--)
			gtk_snapshot_pop(snapshot);
		g_array_set_size(levels, levels->len - 1);
	}
	gtk_snapshot_restore(snapshot);
}

void gfx_canvas_clip_rect(struct gfx_canvas *canvas, float left, float top, float right, float bottom, enum gfx_clip_op op)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	graphene_rect_t rect = GRAPHENE_RECT_INIT(left, top, right - left, bottom - top);
	if (op == GFX_CLIP_DIFFERENCE) { // the rect as an inverted mask: the source until the restore's pop
		gtk_snapshot_push_mask(snapshot, GSK_MASK_MODE_INVERTED_ALPHA);
		gtk_snapshot_append_color(snapshot, &(GdkRGBA){0, 0, 0, 1}, &rect);
		gtk_snapshot_pop(snapshot);
	} else {
		gtk_snapshot_push_clip(snapshot, &rect);
	}
	gsk_canvas_pushed(snapshot);
}

void gfx_canvas_translate(struct gfx_canvas *canvas, float dx, float dy)
{
	gtk_snapshot_translate(GTK_SNAPSHOT(canvas), &GRAPHENE_POINT_INIT(dx, dy));
}

void gfx_canvas_rotate(struct gfx_canvas *canvas, float degrees)
{
	gtk_snapshot_rotate(GTK_SNAPSHOT(canvas), degrees);
}

void gfx_canvas_scale(struct gfx_canvas *canvas, float sx, float sy)
{
	gtk_snapshot_scale(GTK_SNAPSHOT(canvas), sx, sy);
}

void gfx_canvas_concat(struct gfx_canvas *canvas, const graphene_matrix_t *matrix)
{
	gtk_snapshot_transform_matrix(GTK_SNAPSHOT(canvas), matrix);
}

void gfx_canvas_draw_rect(struct gfx_canvas *canvas, float left, float top, float right, float bottom, const struct gfx_paint *gfx_paint)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	push_color_filter(snapshot, paint);
	gtk_snapshot_append_color(snapshot, &paint->color, &GRAPHENE_RECT_INIT(left, top, right - left, bottom - top));
	pop_color_filter(snapshot, paint);
}

void gfx_canvas_draw_round_rect(struct gfx_canvas *canvas, float left, float top, float right, float bottom, float rx, float ry, const struct gfx_paint *gfx_paint)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	GdkRGBA gdk_color[4];
	for (int i = 0; i < 4; i++)
		gdk_color[i] = paint->color;
	GskRoundedRect round_rect = {
		.bounds = GRAPHENE_RECT_INIT(left, top, right - left, bottom - top),
		.corner = {{rx, ry}, {rx, ry}, {rx, ry}, {rx, ry}},
	};
	float width = gsk_stroke_get_line_width(paint->gsk_stroke);
	const float widths[4] = {width, width, width, width};
	push_color_filter(snapshot, paint);
	if (paint->is_fill) {
		gtk_snapshot_push_rounded_clip(snapshot, &round_rect);
		gtk_snapshot_append_color(snapshot, gdk_color, &round_rect.bounds);
		gtk_snapshot_pop(snapshot);
	}
	if (paint->is_stroke)
		gtk_snapshot_append_border(snapshot, &round_rect, widths, gdk_color);
	pop_color_filter(snapshot, paint);
}

// a rectangle as long as the line and as wide as the stroke, rotated onto it
void gfx_canvas_draw_line(struct gfx_canvas *canvas, float x0, float y0, float x1, float y1, const struct gfx_paint *gfx_paint)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	gtk_snapshot_save(snapshot);
	gtk_snapshot_translate(snapshot, &GRAPHENE_POINT_INIT(x0, y0));
	gtk_snapshot_rotate(snapshot, atan2f(y1 - y0, x1 - x0) * 180 / M_PI);
	float length = hypotf(x1 - x0, y1 - y0);
	float stroke_width = gsk_stroke_get_line_width(paint->gsk_stroke);
	push_color_filter(snapshot, paint);
	gtk_snapshot_append_color(snapshot, &paint->color, &GRAPHENE_RECT_INIT(0, -stroke_width / 2, length, stroke_width));
	pop_color_filter(snapshot, paint);
	gtk_snapshot_restore(snapshot);
}

void gfx_canvas_draw_drawable(struct gfx_canvas *canvas, struct gfx_drawable *drawable, int width, int height)
{
	GdkPaintable *paintable = GDK_PAINTABLE(drawable);
	if (!JAVA_IS_PAINTABLE(paintable))
		gdk_paintable_snapshot(paintable, GDK_SNAPSHOT(canvas), width, height);
}

// src: the texture scaled so that src fills dst, clipped to dst
void gfx_canvas_draw_bitmap(struct gfx_canvas *canvas, struct gfx_bitmap *bitmap, const struct gfx_rect *src, const struct gfx_rect *dst, const struct gfx_paint *gfx_paint)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	GdkTexture *texture = GDK_TEXTURE(bitmap);
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	graphene_rect_t bounds = GRAPHENE_RECT_INIT(dst->left, dst->top, dst->right - dst->left, dst->bottom - dst->top);
	push_color_filter(snapshot, paint);
	if (!src) {
		gtk_snapshot_append_texture(snapshot, texture, &bounds);
	} else {
		gtk_snapshot_push_clip(snapshot, &bounds);
		float xscale = bounds.size.width / (src->right - src->left);
		float yscale = bounds.size.height / (src->bottom - src->top);
		gtk_snapshot_append_texture(snapshot, texture, &GRAPHENE_RECT_INIT(dst->left - src->left * xscale, dst->top - src->top * yscale, gdk_texture_get_width(texture) * xscale, gdk_texture_get_height(texture) * yscale));
		gtk_snapshot_pop(snapshot);
	}
	pop_color_filter(snapshot, paint);
}

#ifdef ATL_TEST_HOOKS
void gfx_canvas_dump(const struct gfx_canvas *canvas, FILE *out) {}
#endif
