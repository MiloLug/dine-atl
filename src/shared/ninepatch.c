#include <stdlib.h>
#include <string.h>

#include "ninepatch.h"

/* Res_png_9patch's header: wasDeserialized, numXDivs, numYDivs, numColors, then uint32 xDivsOffset,
 * yDivsOffset, int32 paddingLeft, paddingRight, paddingTop, paddingBottom, uint32 colorsOffset. */
#define HEADER    32
#define PADDING   12 // paddingLeft's offset
#define SIZE(x, y, colors) (HEADER + 4 * ((size_t)(x) + (y) + (colors)))

static uint32_t be32(const uint8_t *p)
{
	return (uint32_t)p[0] << 24 | p[1] << 16 | p[2] << 8 | p[3];
}

static void put32(uint8_t *p, uint32_t value)
{
	memcpy(p, &value, 4);
}

static int32_t get32(const uint8_t *p)
{
	int32_t value;
	memcpy(&value, p, 4);
	return value;
}

// AOSP's NinePatchPeeker: the file's chunk is exactly its arrays long, then deserialize (offsets
// from the counts) and fileToDevice (divs, padding and colors from network order)
void *ninepatch_chunk_from_png(const uint8_t *png, size_t size, size_t *chunk_size)
{
	static const uint8_t signature[8] = {0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
	if (size < 8 || memcmp(png, signature, 8))
		return NULL;
	for (size_t at = 8; at + 12 <= size;) {
		uint32_t length = be32(png + at);
		const uint8_t *type = png + at + 4, *data = png + at + 8;
		if (length > size - at - 12)
			return NULL;
		if (!memcmp(type, "IDAT", 4) || !memcmp(type, "IEND", 4))
			return NULL; // npTc comes before the image data
		if (!memcmp(type, "npTc", 4)) {
			if (length < HEADER || length != SIZE(data[1], data[2], data[3]))
				return NULL;
			uint8_t *chunk = malloc(length);
			memcpy(chunk, data, length);
			chunk[0] = 1;
			put32(chunk + 4, HEADER);
			put32(chunk + 8, HEADER + 4 * chunk[1]);
			put32(chunk + 28, HEADER + 4 * (chunk[1] + chunk[2]));
			for (size_t i = PADDING; i < PADDING + 16; i += 4)
				put32(chunk + i, be32(data + i));
			for (size_t i = HEADER; i < length; i += 4)
				put32(chunk + i, be32(data + i));
			*chunk_size = length;
			return chunk;
		}
		at += 12 + length;
	}
	return NULL;
}

// AOSP's validateNinePatchChunk and deserialize, whose offsets follow the counts, and the arrays
// inside the chunk, which AOSP doesn't check
struct gfx_ninepatch *ninepatch_parse(const void *chunk_data, size_t size)
{
	const uint8_t *chunk = chunk_data;
	if (size < HEADER || (int8_t)chunk[0] == -1 || size < SIZE(chunk[1], chunk[2], chunk[3]))
		return NULL;
	struct gfx_ninepatch *ninepatch = calloc(1, sizeof(*ninepatch));
	ninepatch->n_x_divs = chunk[1];
	ninepatch->n_y_divs = chunk[2];
	ninepatch->n_colors = chunk[3];
	ninepatch->x_divs = malloc(4 * (ninepatch->n_x_divs + 1));
	ninepatch->y_divs = malloc(4 * (ninepatch->n_y_divs + 1));
	ninepatch->colors = malloc(4 * (ninepatch->n_colors + 1));
	const uint8_t *p = chunk + HEADER;
	for (int i = 0; i < ninepatch->n_x_divs; i++, p += 4)
		ninepatch->x_divs[i] = get32(p);
	for (int i = 0; i < ninepatch->n_y_divs; i++, p += 4)
		ninepatch->y_divs[i] = get32(p);
	for (int i = 0; i < ninepatch->n_colors; i++, p += 4)
		ninepatch->colors[i] = get32(p);
	ninepatch->padding_left = get32(chunk + PADDING);
	ninepatch->padding_right = get32(chunk + PADDING + 4);
	ninepatch->padding_top = get32(chunk + PADDING + 8);
	ninepatch->padding_bottom = get32(chunk + PADDING + 12);
	return ninepatch;
}

void ninepatch_free(struct gfx_ninepatch *ninepatch)
{
	if (!ninepatch)
		return;
	free(ninepatch->x_divs);
	free(ninepatch->y_divs);
	free(ninepatch->colors);
	free(ninepatch);
}

struct axis {
	float scale_fixed, scale_stretch;
};

// odd sections stretch; when they can't take up the difference, every section scales alike
static struct axis axis(const int32_t *divs, int n, int size, float dst)
{
	float stretch = 0;
	for (int i = 1; i <= n; i += 2)
		stretch += (i == n ? size : divs[i]) - divs[i - 1];
	float fixed = size - stretch;
	if (stretch > 0 && dst >= fixed)
		return (struct axis){1, (dst - fixed) / stretch};
	if (dst < fixed)
		return (struct axis){dst / fixed, 0};
	float scale = size > 0 ? dst / size : 0; // nothing stretches
	return (struct axis){scale, scale};
}

static int section_start(const int32_t *divs, int i, int size)
{
	int at = i ? divs[i - 1] : 0;
	return at < 0 ? 0 : at > size ? size : at;
}

size_t ninepatch_slices(const struct gfx_ninepatch *ninepatch, int width, int height, float dst_width, float dst_height, struct ninepatch_slice **slices)
{
	struct axis x = axis(ninepatch->x_divs, ninepatch->n_x_divs, width, dst_width);
	struct axis y = axis(ninepatch->y_divs, ninepatch->n_y_divs, height, dst_height);
	size_t n = 0;
	*slices = malloc((ninepatch->n_x_divs + 1) * (ninepatch->n_y_divs + 1) * sizeof(**slices));
	int color = 0;
	float dst_top = 0;
	for (int j = 0; j <= ninepatch->n_y_divs; j++) {
		int top = section_start(ninepatch->y_divs, j, height), bottom = section_start(ninepatch->y_divs, j + 1, height);
		if (j == ninepatch->n_y_divs)
			bottom = height;
		float dst_bottom = dst_top + (bottom - top) * (j % 2 ? y.scale_stretch : y.scale_fixed);
		float dst_left = 0;
		for (int i = 0; bottom > top && i <= ninepatch->n_x_divs; i++) {
			int left = section_start(ninepatch->x_divs, i, width), right = section_start(ninepatch->x_divs, i + 1, width);
			if (i == ninepatch->n_x_divs)
				right = width;
			float dst_right = dst_left + (right - left) * (i % 2 ? x.scale_stretch : x.scale_fixed);
			if (right <= left)
				continue;
			uint32_t c = color < ninepatch->n_colors ? ninepatch->colors[color] : NINEPATCH_NO_COLOR;
			color++;
			if (c != NINEPATCH_TRANSPARENT && dst_right > dst_left && dst_bottom > dst_top)
				(*slices)[n++] = (struct ninepatch_slice){{left, top, right, bottom}, {dst_left, dst_top, dst_right, dst_bottom}, c};
			dst_left = dst_right;
		}
		dst_top = dst_bottom;
	}
	return n;
}
