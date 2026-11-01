#ifndef SHARED_NINEPATCH_H
#define SHARED_NINEPATCH_H

#include <stddef.h>
#include <stdint.h>

#include "src/slots/gfx.h"

/* A nine-patch (Res_png_9patch): x divs pair up as the start and end of each stretched column, y
 * divs of each stretched row; a color for each patch with pixels, row by row. */
struct gfx_ninepatch {
	int n_x_divs, n_y_divs, n_colors;
	int32_t *x_divs, *y_divs;
	uint32_t *colors; // NINEPATCH_NO_COLOR, NINEPATCH_TRANSPARENT or the patch's one ARGB
	int32_t padding_left, padding_top, padding_right, padding_bottom;
};

#define NINEPATCH_TRANSPARENT 0
#define NINEPATCH_NO_COLOR    1 // the bitmap's part draws

/* A PNG's npTc as Bitmap.getNinePatchChunk returns it (deserialized, native order); NULL: none,
 * or not a valid one. */
void *ninepatch_chunk_from_png(const uint8_t *png, size_t size, size_t *chunk_size);
/* A chunk in that form; NULL: its arrays don't fit size. */
struct gfx_ninepatch *ninepatch_parse(const void *chunk, size_t size);
void ninepatch_free(struct gfx_ninepatch *ninepatch);

struct ninepatch_slice {
	struct gfx_rect src, dst;
	uint32_t color; // NINEPATCH_NO_COLOR: src of the bitmap into dst; else dst filled with it
};
/* What a width x height bitmap draws at dst_width x dst_height, transparent patches left out;
 * fixed parts shrink when dst is smaller than they are. Returns the count; *slices malloc'd. */
size_t ninepatch_slices(const struct gfx_ninepatch *ninepatch, int width, int height, float dst_width, float dst_height, struct ninepatch_slice **slices);

#endif
