#ifndef GFX_RECORD_BITMAP_H
#define GFX_RECORD_BITMAP_H

#include <stdatomic.h>
#include <stdint.h>

#include "src/slots/gfx.h"

/* Pixels as premultiplied ARGB words (src/shared/pixels.h), whatever the config: cairo's ARGB32,
 * which raster.c draws into. */
struct gfx_bitmap {
	atomic_int refs;
	int width, height;
	enum gfx_bitmap_config config;
	uint32_t *pixels;
};

/* Draws a finished list over the pixels; text isn't drawn (record has no font). */
void record_raster(const struct gfx_canvas *list, uint32_t *pixels, int width, int height);

#endif
