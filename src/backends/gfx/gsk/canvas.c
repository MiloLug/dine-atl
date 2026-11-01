#include <math.h>

#include <gtk/gtk.h>

#include "src/api-impl-jni/graphics/AndroidPaint.h"
#include "src/api-impl-jni/graphics/java_paintable.h"
#include "src/shared/path.h"
#include "src/shared/shader.h"
#include "src/slots/gfx.h"

#include "canvas.h"
#include "text.h"

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

// a push the restore of the current save level pops
static void gsk_canvas_pushed(GtkSnapshot *snapshot)
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

// fills the snapshot's fill or stroke push: the local matrix applies in it, the paint's alpha over it
static void append_shader(GtkSnapshot *snapshot, const graphene_rect_t *shape, const struct gfx_shader *shader, float alpha)
{
	graphene_matrix_t inverse;
	graphene_rect_t bounds;
	if (!graphene_matrix_inverse(&shader->matrix, &inverse))
		return;
	graphene_matrix_transform_bounds(&inverse, shape, &bounds);
	float t_min, t_max;
	shader_t_range(shader, &(struct gfx_rect){bounds.origin.x, bounds.origin.y, bounds.origin.x + bounds.size.width, bounds.origin.y + bounds.size.height}, &t_min, &t_max);
	int k0, k1;
	uint32_t *colors;
	float *positions;
	size_t n = shader_stops(shader, t_min, t_max, &k0, &k1, &colors, &positions);
	GskColorStop *stops = g_new(GskColorStop, n);
	for (size_t i = 0; i < n; i++)
		stops[i] = (GskColorStop){positions[i], {((colors[i] >> 16) & 0xff) / 255.f, ((colors[i] >> 8) & 0xff) / 255.f, (colors[i] & 0xff) / 255.f, (colors[i] >> 24) / 255.f * alpha}};
	gtk_snapshot_save(snapshot);
	gtk_snapshot_transform_matrix(snapshot, &shader->matrix);
	graphene_point_t center = GRAPHENE_POINT_INIT(shader->x0, shader->y0);
	float dx = shader->x1 - shader->x0, dy = shader->y1 - shader->y0;
	if (shader->kind == SHADER_LINEAR && (dx || dy))
		gtk_snapshot_append_linear_gradient(snapshot, &bounds, &GRAPHENE_POINT_INIT(shader->x0 + dx * k0, shader->y0 + dy * k0),
		                                    &GRAPHENE_POINT_INIT(shader->x0 + dx * k1, shader->y0 + dy * k1), stops, n);
	else if (shader->kind == SHADER_RADIAL && shader->radius > 0)
		gtk_snapshot_append_radial_gradient(snapshot, &bounds, &center, shader->radius * k1, shader->radius * k1, (float)k0 / k1, 1, stops, n);
	else if (shader->kind == SHADER_SWEEP)
		gtk_snapshot_append_conic_gradient(snapshot, &bounds, &center, 90, stops, n); // GSK's 0 is at the top
	else // no length: the last color
		gtk_snapshot_append_color(snapshot, &stops[n - 1].color, &bounds);
	gtk_snapshot_restore(snapshot);
	g_free(stops);
	free(positions);
	free(colors);
}

// the shape with the paint's shader, filled and stroked as fill and stroke say
static void shade(GtkSnapshot *snapshot, GskPath *path, GskFillRule rule, bool fill, bool stroke, const struct AndroidPaint *paint)
{
	graphene_rect_t bounds;
	push_color_filter(snapshot, paint);
	if (fill && gsk_path_get_bounds(path, &bounds)) {
		gtk_snapshot_push_fill(snapshot, path, rule);
		append_shader(snapshot, &bounds, paint->shader, paint->color.alpha);
		gtk_snapshot_pop(snapshot);
	}
	if (stroke && gsk_path_get_stroke_bounds(path, paint->gsk_stroke, &bounds)) {
		gtk_snapshot_push_stroke(snapshot, path, paint->gsk_stroke);
		append_shader(snapshot, &bounds, paint->shader, paint->color.alpha);
		gtk_snapshot_pop(snapshot);
	}
	pop_color_filter(snapshot, paint);
}

static void shade_rect(GtkSnapshot *snapshot, const GskRoundedRect *rect, const struct AndroidPaint *paint)
{
	GskPathBuilder *builder = gsk_path_builder_new();
	gsk_path_builder_add_rounded_rect(builder, rect);
	GskPath *path = gsk_path_builder_free_to_path(builder);
	shade(snapshot, path, GSK_FILL_RULE_WINDING, paint->is_fill, paint->is_stroke, paint);
	gsk_path_unref(path);
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

// GSK has no layer blend mode: SRC_OVER
void gfx_canvas_save_layer(struct gfx_canvas *canvas, const struct gfx_rect *bounds, uint8_t alpha, int blend)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	gfx_canvas_save(canvas);
	if (bounds) {
		gtk_snapshot_push_clip(snapshot, &GRAPHENE_RECT_INIT(bounds->left, bounds->top, bounds->right - bounds->left, bounds->bottom - bounds->top));
		gsk_canvas_pushed(snapshot);
	}
	gtk_snapshot_push_opacity(snapshot, alpha / 255.);
	gsk_canvas_pushed(snapshot);
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
	if (paint->shader) {
		GskRoundedRect rect;
		shade_rect(snapshot, gsk_rounded_rect_init_from_rect(&rect, &GRAPHENE_RECT_INIT(left, top, right - left, bottom - top), 0), paint);
		return;
	}
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
	if (paint->shader) {
		shade_rect(snapshot, &round_rect, paint);
		return;
	}
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
	if (paint->shader) {
		GskPathBuilder *builder = gsk_path_builder_new();
		gsk_path_builder_move_to(builder, x0, y0);
		gsk_path_builder_line_to(builder, x1, y1);
		GskPath *path = gsk_path_builder_free_to_path(builder);
		shade(snapshot, path, GSK_FILL_RULE_WINDING, false, true, paint);
		gsk_path_unref(path);
		return;
	}
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

static void draw_gsk_path(GtkSnapshot *snapshot, GskPath *path, GskFillRule rule, const struct AndroidPaint *paint)
{
	if (paint->shader) {
		shade(snapshot, path, rule, paint->is_fill, paint->is_stroke, paint);
		return;
	}
	push_color_filter(snapshot, paint);
	if (paint->is_fill)
		gtk_snapshot_append_fill(snapshot, path, rule, &paint->color);
	if (paint->is_stroke)
		gtk_snapshot_append_stroke(snapshot, path, paint->gsk_stroke, &paint->color);
	pop_color_filter(snapshot, paint);
}

void gfx_canvas_draw_path(struct gfx_canvas *canvas, struct gfx_path *path, bool even_odd, const struct gfx_paint *gfx_paint)
{
	GskPath *gsk_path = gsk_path_from_gfx(path);
	draw_gsk_path(GTK_SNAPSHOT(canvas), gsk_path, even_odd ? GSK_FILL_RULE_EVEN_ODD : GSK_FILL_RULE_WINDING, (const struct AndroidPaint *)gfx_paint);
	gsk_path_unref(gsk_path);
}

void gfx_canvas_clip_path(struct gfx_canvas *canvas, struct gfx_path *path, bool even_odd, enum gfx_clip_op op)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	GskPath *gsk_path = gsk_path_from_gfx(path);
	gtk_snapshot_push_mask(snapshot, op == GFX_CLIP_DIFFERENCE ? GSK_MASK_MODE_INVERTED_ALPHA : GSK_MASK_MODE_ALPHA);
	gtk_snapshot_append_fill(snapshot, gsk_path, even_odd ? GSK_FILL_RULE_EVEN_ODD : GSK_FILL_RULE_WINDING, &(GdkRGBA){1, 1, 1, 1});
	gtk_snapshot_pop(snapshot);
	gsk_canvas_pushed(snapshot);
	gsk_path_unref(gsk_path);
}

void gfx_canvas_draw_arc(struct gfx_canvas *canvas, float left, float top, float right, float bottom, float start, float sweep, bool use_center, const struct gfx_paint *gfx_paint)
{
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	struct gfx_path *arc = path_new();
	path_add_draw_arc(arc, left, top, right, bottom, start, sweep, use_center);
	GskPath *gsk_path = gsk_path_from_gfx(arc);
	draw_gsk_path(GTK_SNAPSHOT(canvas), gsk_path, GSK_FILL_RULE_WINDING, paint);
	gsk_path_unref(gsk_path);
	path_unref(arc);
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

// filled whatever the paint's style: GSK has no stroked text
void gfx_canvas_draw_text(struct gfx_canvas *canvas, const uint16_t *text, size_t len, float x, float y, const struct gfx_paint *gfx_paint)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	PangoLayout *layout = gsk_text_layout(gfx_paint, text, len, NULL);
	PangoRectangle logical;
	pango_layout_get_extents(layout, NULL, &logical);
	y -= (float)pango_layout_get_baseline(layout) / PANGO_SCALE;
	if (paint->alignment == PANGO_ALIGN_CENTER)
		x -= (float)logical.width / PANGO_SCALE / 2;
	else if (paint->alignment == PANGO_ALIGN_RIGHT)
		x -= (float)logical.width / PANGO_SCALE;
	push_color_filter(snapshot, paint);
	gtk_snapshot_save(snapshot);
	gtk_snapshot_translate(snapshot, &GRAPHENE_POINT_INIT(x, y));
	gtk_snapshot_append_layout(snapshot, layout, &paint->color);
	gtk_snapshot_restore(snapshot);
	pop_color_filter(snapshot, paint);
	g_object_unref(layout);
}

#ifdef ATL_TEST_HOOKS
void gfx_canvas_dump(const struct gfx_canvas *canvas, FILE *out) {}
#endif
