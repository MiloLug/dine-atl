#ifndef GFX_RECORD_H
#define GFX_RECORD_H

#include <stdatomic.h>
#include <stdint.h>

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
	int filter_mode;
	uint32_t filter_color;
};

struct gfx_drawable {
	struct atl_ref *drawable_ref; // NULL for a container
	atomic_int refs;
	struct gfx_drawable *selected; // a container's child, held
};

#endif
