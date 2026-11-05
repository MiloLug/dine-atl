#ifndef GFX_GSK_CANVAS_H
#define GFX_GSK_CANVAS_H

#include <gtk/gtk.h>

struct AndroidPaint;
struct gfx_paint;
struct gfx_path;
struct gfx_bitmap;
#include "src/slots/gfx.h"

enum gsk_part {
	GSK_CONTENT,  // what it draws
	GSK_COVERAGE, // where: Skia's coverage (a shape's, a bitmap's rectangle) as alpha in opaque white
	GSK_SOURCE,   // its colors over all of bounds, the coverage left out
};
struct gsk_draw;
typedef void (*gsk_draw_fn)(GtkSnapshot *snapshot, const struct gsk_draw *draw, enum gsk_part part);
// a draw with paint that bounds hold: fn appends the part asked for
struct gsk_draw {
	gsk_draw_fn fn;
	const void *data;
	const struct AndroidPaint *paint;
	const graphene_rect_t *bounds;
};

GskPath *gsk_path_from_gfx(const struct gfx_path *path);
/* draw's part (content or source) through filter's color filter (a paint's or a drawable's) */
void gsk_filtered(GtkSnapshot *snapshot, const struct AndroidPaint *filter, const struct gsk_draw *draw, enum gsk_part part);
/* A bitmap drawable's color filter and bitmap; NULL for any other paintable */
struct gfx_paint *gsk_bitmap_drawable_filter(GdkPaintable *paintable);
struct gfx_bitmap *gsk_bitmap_drawable_bitmap(GdkPaintable *paintable);

/* A Canvas(Bitmap)'s (painter.c): its GtkSnapshot only carries the painter, which draws each call
 * into the bitmap at once, under its own matrix and with cairo's save stack. */
struct gsk_painter;
struct gsk_painter *gsk_painter_of(GtkSnapshot *snapshot); // NULL: a recording canvas
void gsk_painter_save(struct gsk_painter *painter);
void gsk_painter_save_layer(struct gsk_painter *painter, const struct gfx_rect *bounds, uint8_t alpha, enum gfx_blend blend);
void gsk_painter_restore(struct gsk_painter *painter);
void gsk_painter_concat(struct gsk_painter *painter, const graphene_matrix_t *matrix);
void gsk_painter_clip_rect(struct gsk_painter *painter, const struct gfx_rect *rect, enum gfx_clip_op op);
void gsk_painter_clip_path(struct gsk_painter *painter, const struct gfx_path *path, bool even_odd, enum gfx_clip_op op);
/* draw's content (and coverage, for a mode but SRC_OVER) as GSK nodes, drawn by cairo in mode */
void gsk_painter_composite(struct gsk_painter *painter, enum gfx_blend mode, const struct gsk_draw *draw);
void gsk_painter_draw_bitmap(struct gsk_painter *painter, struct gfx_bitmap *bitmap, const struct gfx_rect *src, const struct gfx_rect *dst,
                             const struct AndroidPaint *paint);
void gsk_painter_draw_node(struct gsk_painter *painter, GskRenderNode *node);
void gsk_painter_draw_paintable(struct gsk_painter *painter, GdkPaintable *paintable, int width, int height);

#endif
