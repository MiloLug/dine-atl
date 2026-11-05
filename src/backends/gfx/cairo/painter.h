#ifndef GFX_CAIRO_PAINTER_H
#define GFX_CAIRO_PAINTER_H

#include <cairo.h>
#include <graphene.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "src/slots/gfx.h"

/* What record's and gsk's bitmap canvases share: a cairo_t over a bitmap's words that each call
 * draws into at once. One cairo_save per Canvas save, so a clip at the base level only intersects.
 * The matrix comes with each call and never reaches cairo when singular (it would break the
 * cairo_t for good): such a draw draws nothing, such a clip clips everything. */

typedef void (*cpainter_draw_fn)(cairo_t *cr, const void *data);

struct cpainter_level {
	bool layer;
	float alpha;
	enum gfx_blend mode;
};

struct cpainter {
	struct gfx_bitmap *bitmap; // held
	cairo_surface_t *surface;
	cairo_t *cr;
	struct cpainter_level *levels;
	size_t n_levels, levels_size;
	int layers; // open layers among the levels
};

void cpainter_init(struct cpainter *painter, struct gfx_bitmap *bitmap);
void cpainter_finish(struct cpainter *painter); // restores what's open, as Skia's canvas does when it goes
void cpainter_save(struct cpainter *painter);
void cpainter_save_layer(struct cpainter *painter, const graphene_matrix_t *matrix, const struct gfx_rect *bounds, uint8_t alpha, enum gfx_blend blend);
void cpainter_restore(struct cpainter *painter);
void cpainter_clip_rect(struct cpainter *painter, const graphene_matrix_t *matrix, const struct gfx_rect *rect, enum gfx_clip_op op);
void cpainter_clip_path(struct cpainter *painter, const graphene_matrix_t *matrix, const struct gfx_path *path, bool even_odd, enum gfx_clip_op op);
/* draw() under matrix, composited in mode at cover()'s coverage; bounds: where it can draw, under
 * matrix (NULL: anywhere in the clip), so a rounding config rounds only there. */
void cpainter_draw(struct cpainter *painter, const graphene_matrix_t *matrix, enum gfx_blend mode,
                   cpainter_draw_fn draw, cpainter_draw_fn cover, const void *data, const struct gfx_rect *bounds);

cairo_matrix_t cpainter_affine(const graphene_matrix_t *m);
void cpainter_add_path(cairo_t *cr, const struct gfx_path *path);
// each premultiplied pixel of an image surface through the filter
void cpainter_filter_surface(cairo_surface_t *surface, const struct gfx_color_filter *filter);
/* What draw() draws, at the coverage cover() gives, onto the current target in mode: Skia's
 * lerp(d, mode(s, d), coverage) per pixel. */
void cpainter_draw_in_mode(cairo_t *cr, enum gfx_blend mode, cpainter_draw_fn draw, cpainter_draw_fn cover, const void *data);
/* src (NULL: all) of the bitmap into dst at alpha, then through filter (Skia's order); an ALPHA_8
 * bitmap is a mask of shader (a pattern in the current user space) at alpha, or of color (Color's
 * ARGB) without one. A bitmap drawn into the painter's own takes a snapshot first (bitmap_snapshot). */
void cpainter_draw_bitmap(cairo_t *cr, const struct gfx_bitmap *bitmap, const struct gfx_rect *src, const struct gfx_rect *dst,
                          float alpha, const struct gfx_color_filter *filter, uint32_t color, cairo_pattern_t *shader);
cairo_surface_t *cpainter_bitmap_surface(const struct gfx_bitmap *bitmap); // over its words

#endif
