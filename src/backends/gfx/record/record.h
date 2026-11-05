#ifndef GFX_RECORD_H
#define GFX_RECORD_H

#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>

#include <graphene.h>

#include "src/slots/gfx.h"

/* Plain records: paint state, drawables and display lists (canvas.c). */

struct gfx_paint {
	uint32_t color;
	enum gfx_paint_style style;
	float stroke_width;
	int stroke_cap;
	int stroke_join;
	float text_size;
	int text_align;
	enum gfx_font_family font_family;
	int font_style; // GFX_FONT_BOLD, GFX_FONT_ITALIC
	struct gfx_color_filter filter;
	enum gfx_blend blend;
	struct gfx_shader *shader; // held: a copy of the struct takes a ref (record_paint_copy)
};

struct gfx_drawable {
	struct atl_ref *drawable_ref;  // one whose class draws: Java's draw() records it
	struct gfx_bitmap *bitmap;     // a BitmapDrawable's or NinePatchDrawable's, held
	struct gfx_ninepatch *ninepatch; // a NinePatchDrawable's, owned
	struct gfx_color_filter filter; // over the bitmap
	atomic_int refs;
	struct gfx_drawable *selected; // a container's child (neither of the above), held
};

/* A display list of plain data: each op holds a copy of its paint and the matrix it was drawn
 * under, since Paint and Matrix free their native state from finalize. A finished list
 * doesn't change; coregraphics replays it on another thread, so its refs drop on any thread. */

enum op_kind {
	OP_RECT,       // left, top, right, bottom
	OP_ROUND_RECT, // left, top, right, bottom, rx, ry
	OP_LINE,       // x0, y0, x1, y1
	OP_BITMAP,     // src left, top, right, bottom, dst left, top, right, bottom
	OP_CLIP,       // left, top, right, bottom, enum gfx_clip_op
	OP_UNCLIP,     // how many: the clips of the level a restore ends; no paint
	OP_TEXT,       // x, y of the baseline's start
	OP_PATH,       // even-odd
	OP_CLIP_PATH,  // even-odd, enum gfx_clip_op
	OP_ARC,        // left, top, right, bottom, start, sweep, use center
	OP_LAYER,      // left, top, right, bottom, has bounds, alpha, enum gfx_blend
	OP_UNLAYER,    // ends the level's OP_LAYER, after its OP_UNCLIP; no paint
};

struct op {
	graphene_matrix_t matrix;
	struct gfx_paint paint;
	enum op_kind kind;
	float args[8];
	struct gfx_bitmap *bitmap; // OP_BITMAP's, held
	uint16_t *text;            // OP_TEXT's, owned
	size_t text_len;
	struct gfx_path *path; // OP_PATH's and OP_CLIP_PATH's, held
	void *cache;           // coregraphics: OP_BITMAP's filtered image, of cache_generation's pixels
	unsigned cache_generation;
};

struct canvas_state {
	graphene_matrix_t matrix;
	int clips;  // OP_CLIPs and OP_CLIP_PATHs since the save
	bool layer; // the save was a saveLayer: its OP_LAYER came first
};

struct gfx_canvas {
	atomic_int refs;
	struct canvas_state state;
	struct canvas_state *saved; // save()'s stack
	size_t n_saved, saved_size;
	struct op *ops;
	size_t n_ops, ops_size;
	struct painter *painter; // a Canvas(Bitmap)'s: ops draw into the bitmap as they come, ops stays empty
};

/* A Canvas(Bitmap)'s drawing, per backend (record: painter.c, cairo; coregraphics: painter.c):
 * each op drawn at once, its paint, path, bitmap and text only borrowed for the call. One
 * painter_save or painter_save_layer per save; clips stay until their level's painter_restore. */
struct painter;
struct painter *painter_new(struct gfx_bitmap *bitmap);
void painter_free(struct painter *painter); // restores what's open
void painter_save(struct painter *painter);
void painter_save_layer(struct painter *painter, const struct op *op); // OP_LAYER's
void painter_restore(struct painter *painter);
void painter_clip(struct painter *painter, const struct op *op); // OP_CLIP's or OP_CLIP_PATH's
void painter_draw(struct painter *painter, const struct op *op);
// what a draw op can change, under its matrix, its stroke included; false: unknown (text)
bool record_op_bounds(const struct op *op, struct gfx_rect *bounds);

// an op's cache, freed with its list (coregraphics: a CGImage)
void record_op_cache_free(void *cache);

/* *dst = *src with the refs a copy holds */
void record_paint_copy(struct gfx_paint *dst, const struct gfx_paint *src);

/* Another holder of a finished list; gfx_canvas_free drops a ref. */
struct gfx_canvas *record_canvas_ref(struct gfx_canvas *canvas);

#endif
