#include <gtk/gtk.h>
#include <math.h>
#include <stdlib.h>

#include "src/api-impl-jni/graphics/AndroidPaint.h"
#include "src/backends/gfx/cairo/painter.h"
#include "src/shared/bitmap.h"

#include "canvas.h"

/* A Canvas(Bitmap) on gsk: what a draw puts down is built as GSK nodes by canvas.c's own code, and
 * cairo draws them into the bitmap's words. Those nodes are self-contained (filters and composes
 * isolate), so GSK's cairo drawing is exact for them; a blend onto the pixels below is cairo's
 * (cpainter_draw_in_mode), since GSK's copy and paste never read a cairo target. */

struct gsk_painter {
	struct cpainter cairo;
	graphene_matrix_t matrix;
	graphene_matrix_t *saved; // the matrix per save
	size_t n_saved, saved_size;
};

static void painter_free(gpointer data)
{
	struct gsk_painter *painter = data;
	cpainter_finish(&painter->cairo);
	free(painter->saved);
	g_free(painter);
}

struct gfx_canvas *gfx_canvas_new_bitmap(struct gfx_bitmap *bitmap)
{
	GtkSnapshot *snapshot = gtk_snapshot_new();
	struct gsk_painter *painter = g_new0(struct gsk_painter, 1);
	cpainter_init(&painter->cairo, bitmap);
	graphene_matrix_init_identity(&painter->matrix);
	g_object_set_data_full(G_OBJECT(snapshot), "atl-painter", painter, painter_free);
	return (struct gfx_canvas *)snapshot;
}

struct gsk_painter *gsk_painter_of(GtkSnapshot *snapshot)
{
	return g_object_get_data(G_OBJECT(snapshot), "atl-painter");
}

static void push_matrix(struct gsk_painter *painter)
{
	if (painter->n_saved == painter->saved_size) {
		painter->saved_size = painter->saved_size ? painter->saved_size * 2 : 4;
		painter->saved = realloc(painter->saved, painter->saved_size * sizeof(*painter->saved));
	}
	painter->saved[painter->n_saved++] = painter->matrix;
}

void gsk_painter_save(struct gsk_painter *painter)
{
	push_matrix(painter);
	cpainter_save(&painter->cairo);
}

void gsk_painter_save_layer(struct gsk_painter *painter, const struct gfx_rect *bounds, uint8_t alpha, enum gfx_blend blend)
{
	push_matrix(painter);
	cpainter_save_layer(&painter->cairo, &painter->matrix, bounds, alpha, blend);
}

void gsk_painter_restore(struct gsk_painter *painter)
{
	if (!painter->n_saved)
		return;
	cpainter_restore(&painter->cairo);
	painter->matrix = painter->saved[--painter->n_saved];
}

// row-vector form: matrix applies first, then the current one
void gsk_painter_concat(struct gsk_painter *painter, const graphene_matrix_t *matrix)
{
	graphene_matrix_multiply(matrix, &painter->matrix, &painter->matrix);
}

void gsk_painter_clip_rect(struct gsk_painter *painter, const struct gfx_rect *rect, enum gfx_clip_op op)
{
	cpainter_clip_rect(&painter->cairo, &painter->matrix, rect, op);
}

void gsk_painter_clip_path(struct gsk_painter *painter, const struct gfx_path *path, bool even_odd, enum gfx_clip_op op)
{
	cpainter_clip_path(&painter->cairo, &painter->matrix, path, even_odd, op);
}

static struct gfx_rect rect_of(const graphene_rect_t *r)
{
	return (struct gfx_rect){r->origin.x, r->origin.y, r->origin.x + r->size.width, r->origin.y + r->size.height};
}

struct nodes {
	GskRenderNode *content, *coverage; // NULL: nothing
};

static void draw_content(cairo_t *cr, const void *data)
{
	const struct nodes *nodes = data;
	if (nodes->content)
		gsk_render_node_draw(nodes->content, cr);
}

static void draw_coverage(cairo_t *cr, const void *data)
{
	const struct nodes *nodes = data;
	if (nodes->coverage)
		gsk_render_node_draw(nodes->coverage, cr);
}

static GskRenderNode *part_node(const struct gsk_draw *draw, enum gsk_part part)
{
	GtkSnapshot *scratch = gtk_snapshot_new();
	draw->fn(scratch, draw, part);
	return gtk_snapshot_free_to_node(scratch);
}

void gsk_painter_composite(struct gsk_painter *painter, enum gfx_blend mode, const struct gsk_draw *draw)
{
	if (mode == GFX_BLEND_DST)
		return;
	struct nodes nodes = {part_node(draw, GSK_CONTENT), mode == GFX_BLEND_SRC_OVER ? NULL : part_node(draw, GSK_COVERAGE)};
	struct gfx_rect bounds = rect_of(draw->bounds);
	cpainter_draw(&painter->cairo, &painter->matrix, mode, draw_content, draw_coverage, &nodes, &bounds);
	if (nodes.content)
		gsk_render_node_unref(nodes.content);
	if (nodes.coverage)
		gsk_render_node_unref(nodes.coverage);
}

void gsk_painter_draw_node(struct gsk_painter *painter, GskRenderNode *node)
{
	graphene_rect_t node_bounds;
	gsk_render_node_get_bounds(node, &node_bounds);
	struct gfx_rect bounds = rect_of(&node_bounds);
	struct nodes nodes = {node, NULL};
	cpainter_draw(&painter->cairo, &painter->matrix, GFX_BLEND_SRC_OVER, draw_content, draw_coverage, &nodes, &bounds);
}

struct bitmap_op {
	const struct gfx_bitmap *bitmap;
	struct gfx_rect src, dst;
	float alpha;
	const struct gfx_color_filter *filter;
	uint32_t color;
};

static void draw_bitmap_op(cairo_t *cr, const void *data)
{
	const struct bitmap_op *op = data;
	cpainter_draw_bitmap(cr, op->bitmap, &op->src, &op->dst, op->alpha, op->filter, op->color, NULL);
}

static void cover_bitmap_op(cairo_t *cr, const void *data)
{
	const struct bitmap_op *op = data;
	cairo_rectangle(cr, op->dst.left, op->dst.top, op->dst.right - op->dst.left, op->dst.bottom - op->dst.top);
	cairo_set_source_rgb(cr, 1, 1, 1);
	cairo_fill(cr);
}

static uint32_t argb_of(const GdkRGBA *c)
{
	return (uint32_t)lroundf(c->alpha * 255) << 24 | (uint32_t)lroundf(c->red * 255) << 16 | (uint32_t)lroundf(c->green * 255) << 8 | (uint32_t)lroundf(c->blue * 255);
}

// straight from the source's words; one drawn into itself from a copy
void gsk_painter_draw_bitmap(struct gsk_painter *painter, struct gfx_bitmap *bitmap, const struct gfx_rect *src, const struct gfx_rect *dst,
                             const struct AndroidPaint *paint)
{
	struct gfx_bitmap *own = bitmap == painter->cairo.bitmap ? bitmap_snapshot(bitmap) : NULL;
	struct bitmap_op op = {own ? own : bitmap, src ? *src : (struct gfx_rect){0, 0, bitmap->width, bitmap->height}, *dst,
	                       paint->color.alpha, &paint->filter, argb_of(&paint->color)};
	cpainter_draw(&painter->cairo, &painter->matrix, paint->blend, draw_bitmap_op, cover_bitmap_op, &op, dst);
	if (own)
		gfx_bitmap_unref(own);
}

void gsk_painter_draw_paintable(struct gsk_painter *painter, GdkPaintable *paintable, int width, int height)
{
	struct gfx_bitmap *bitmap = gsk_bitmap_drawable_bitmap(paintable);
	if (bitmap) {
		struct AndroidPaint *filter = (struct AndroidPaint *)gsk_bitmap_drawable_filter(paintable);
		struct AndroidPaint paint = *filter;
		paint.color = (GdkRGBA){0, 0, 0, 1};
		paint.blend = GFX_BLEND_SRC_OVER;
		gsk_painter_draw_bitmap(painter, bitmap, NULL, &(struct gfx_rect){0, 0, width, height}, &paint);
		return;
	}
	GtkSnapshot *scratch = gtk_snapshot_new();
	gdk_paintable_snapshot(paintable, GDK_SNAPSHOT(scratch), width, height);
	GskRenderNode *node = gtk_snapshot_free_to_node(scratch);
	if (node) {
		gsk_painter_draw_node(painter, node);
		gsk_render_node_unref(node);
	}
}
