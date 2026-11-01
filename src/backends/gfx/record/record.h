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
	int filter_mode;
	uint32_t filter_color;
	struct gfx_shader *shader; // held: a copy of the struct takes a ref (record_paint_copy)
};

struct gfx_drawable {
	struct atl_ref *drawable_ref;  // one whose class draws: Java's draw() records it
	struct gfx_bitmap *bitmap;     // a BitmapDrawable's or NinePatchDrawable's, held
	struct gfx_ninepatch *ninepatch; // a NinePatchDrawable's, owned
	int filter_mode;               // over the bitmap, -1: none
	uint32_t filter_color;
	atomic_int refs;
	struct gfx_drawable *selected; // a container's child (neither of the above), held
};

/* A display list of plain data: each op holds a copy of its paint and the matrix it was drawn
 * under, since Paint and Matrix free their native state on the FinalizerDaemon. A finished list
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
	OP_LAYER,      // left, top, right, bottom, has bounds, alpha, PorterDuff mode or -1
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
};

/* *dst = *src with the refs a copy holds */
void record_paint_copy(struct gfx_paint *dst, const struct gfx_paint *src);

/* Another holder of a finished list; gfx_canvas_free drops a ref. */
struct gfx_canvas *record_canvas_ref(struct gfx_canvas *canvas);

#endif
