#include <math.h>

#include <gtk/gtk.h>

#include "src/api-impl-jni/graphics/AndroidPaint.h"
#include "src/api-impl-jni/graphics/java_paintable.h"
#include "src/slots/gfx.h"

/* A canvas is a GtkSnapshot: the widget's snapshot during a draw, a Bitmap's or a RenderNode's. */

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
}

void gfx_canvas_restore(struct gfx_canvas *canvas)
{
	gtk_snapshot_restore(GTK_SNAPSHOT(canvas));
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

#ifdef ATL_TEST_HOOKS
void gfx_canvas_dump(const struct gfx_canvas *canvas, FILE *out) {}
#endif
