#include <math.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/pixels.h"

#include "bitmap.h"

/* Android 10's Skia stores RGB_565 and ARGB_4444 through its lowp raster pipeline: a write rounds
 * 8 bits down to the stored ones, a read repeats their bits (SkRasterPipeline_opts.h store_565,
 * from_565). The words keep the expanded value, so reads need nothing. */
static uint32_t expand5(uint32_t q)
{
	return q << 3 | q >> 2;
}

static uint32_t expand6(uint32_t q)
{
	return q << 2 | q >> 4;
}

static uint32_t quantize5(uint32_t v)
{
	return (v * 9 + 36) / 74;
}

static uint32_t quantize6(uint32_t v)
{
	return (v * 21 + 42) / 85;
}

static uint32_t quantize4(uint32_t v)
{
	return (v + 8) / 17;
}

// a premultiplied word as the config stores it: RGB_565 drops alpha (opaque), ALPHA_8 keeps only it
static uint32_t round_word(enum gfx_bitmap_config config, uint32_t word)
{
	uint32_t a = word >> 24, r = (word >> 16) & 0xff, g = (word >> 8) & 0xff, b = word & 0xff;
	switch (config) {
	case GFX_BITMAP_RGB_565:
		return 0xff000000 | expand5(quantize5(r)) << 16 | expand6(quantize6(g)) << 8 | expand5(quantize5(b));
	case GFX_BITMAP_ARGB_4444:
		return quantize4(a) * 17 << 24 | quantize4(r) * 17 << 16 | quantize4(g) * 17 << 8 | quantize4(b) * 17;
	case GFX_BITMAP_ALPHA_8:
		return word & 0xff000000;
	default:
		return word;
	}
}

static bool config_rounds(enum gfx_bitmap_config config)
{
	return config == GFX_BITMAP_RGB_565 || config == GFX_BITMAP_ARGB_4444 || config == GFX_BITMAP_ALPHA_8;
}

static struct gfx_bitmap *bitmap_new(int width, int height, enum gfx_bitmap_config config, uint32_t *words)
{
	struct gfx_bitmap *bitmap = calloc(1, sizeof(*bitmap));
	bitmap->refs = 1;
	bitmap->width = width;
	bitmap->height = height;
	bitmap->config = config;
	bitmap->words = words;
	pthread_mutex_init(&bitmap->image_lock, NULL);
	return bitmap;
}

struct gfx_bitmap *gfx_bitmap_new(int width, int height, enum gfx_bitmap_config config)
{
	size_t n = (size_t)width * height;
	uint32_t *words = calloc(n ? n : 1, sizeof(*words));
	if (config == GFX_BITMAP_RGB_565) // opaque: black
		for (size_t i = 0; i < n; i++)
			words[i] = 0xff000000;
	return bitmap_new(width, height, config, words);
}

struct gfx_bitmap *gfx_bitmap_decode(const void *data, size_t size)
{
	int width, height;
	uint32_t *words = gfx_image_decode(data, size, &width, &height);
	return words ? bitmap_new(width, height, GFX_BITMAP_ARGB_8888, words) : NULL;
}

struct gfx_bitmap *gfx_bitmap_copy(const struct gfx_bitmap *bitmap, enum gfx_bitmap_config config)
{
	size_t n = (size_t)bitmap->width * bitmap->height;
	uint32_t *words = malloc((n ? n : 1) * sizeof(*words));
	for (size_t i = 0; i < n; i++)
		words[i] = round_word(config, bitmap->words[i]);
	return bitmap_new(bitmap->width, bitmap->height, config, words);
}

struct gfx_bitmap *bitmap_snapshot(const struct gfx_bitmap *src)
{
	return gfx_bitmap_copy(src, src->config);
}

int gfx_bitmap_get_width(const struct gfx_bitmap *bitmap)
{
	return bitmap->width;
}

int gfx_bitmap_get_height(const struct gfx_bitmap *bitmap)
{
	return bitmap->height;
}

void gfx_bitmap_ref(struct gfx_bitmap *bitmap)
{
	atomic_fetch_add(&bitmap->refs, 1);
}

void gfx_bitmap_unref(struct gfx_bitmap *bitmap)
{
	if (atomic_fetch_sub(&bitmap->refs, 1) != 1)
		return;
	if (bitmap->image)
		gfx_bitmap_image_free(bitmap->image);
	pthread_mutex_destroy(&bitmap->image_lock);
	free(bitmap->words);
	free(bitmap);
}

void bitmap_changed(struct gfx_bitmap *bitmap)
{
	atomic_fetch_add(&bitmap->generation, 1);
}

void bitmap_round(struct gfx_bitmap *bitmap, int x0, int y0, int x1, int y1)
{
	if (config_rounds(bitmap->config)) {
		x0 = x0 < 0 ? 0 : x0, y0 = y0 < 0 ? 0 : y0;
		x1 = x1 > bitmap->width ? bitmap->width : x1, y1 = y1 > bitmap->height ? bitmap->height : y1;
		for (int y = y0; y < y1; y++) {
			uint32_t *row = bitmap->words + (size_t)y * bitmap->width;
			for (int x = x0; x < x1; x++)
				row[x] = round_word(bitmap->config, row[x]);
		}
	}
	bitmap_changed(bitmap);
}

void gfx_bitmap_erase(struct gfx_bitmap *bitmap, uint32_t argb)
{
	uint32_t word = round_word(bitmap->config, argb_premultiply(argb));
	for (size_t i = 0; i < (size_t)bitmap->width * bitmap->height; i++)
		bitmap->words[i] = word;
	bitmap_changed(bitmap);
}

void gfx_bitmap_read(const struct gfx_bitmap *bitmap, int x, int y, int width, int height, uint32_t *argb, int stride)
{
	for (int row = 0; row < height; row++) {
		const uint32_t *words = bitmap->words + (size_t)(y + row) * bitmap->width + x;
		for (int col = 0; col < width; col++)
			argb[(ptrdiff_t)row * stride + col] = argb_unpremultiply(words[col]);
	}
}

// setPixels: an opaque config takes the color as it is, its alpha dropped (Skia's writePixels)
void gfx_bitmap_write(struct gfx_bitmap *bitmap, int x, int y, int width, int height, const uint32_t *argb, int stride)
{
	bool opaque = bitmap->config == GFX_BITMAP_RGB_565;
	for (int row = 0; row < height; row++) {
		uint32_t *words = bitmap->words + (size_t)(y + row) * bitmap->width + x;
		for (int col = 0; col < width; col++) {
			uint32_t color = argb[(ptrdiff_t)row * stride + col];
			words[col] = round_word(bitmap->config, opaque ? color | 0xff000000 : argb_premultiply(color));
		}
	}
	bitmap_changed(bitmap);
}

void *gfx_bitmap_encode_png(const struct gfx_bitmap *bitmap, size_t *size)
{
	return gfx_image_encode_png(bitmap->words, bitmap->width, bitmap->height, size);
}

int bitmap_bytes_per_pixel(enum gfx_bitmap_config config)
{
	switch (config) {
	case GFX_BITMAP_ALPHA_8: return 1;
	case GFX_BITMAP_RGB_565:
	case GFX_BITMAP_ARGB_4444: return 2;
	case GFX_BITMAP_RGBA_F16: return 8;
	default: return 4;
	}
}

static uint16_t half(float value)
{
	union {
		float f;
		uint32_t u;
	} bits = {.f = value};
	uint32_t sign = (bits.u >> 16) & 0x8000;
	int exponent = (int)((bits.u >> 23) & 0xff) - 127 + 15;
	uint32_t mantissa = bits.u & 0x7fffff;
	if (exponent <= 0)
		return sign;
	if (exponent >= 31)
		return sign | 0x7c00;
	return sign | exponent << 10 | (mantissa + 0x1000) >> 13;
}

static float from_half(uint16_t h)
{
	int exponent = (h >> 10) & 0x1f, mantissa = h & 0x3ff;
	float value = exponent == 0 ? ldexpf(mantissa, -24) : exponent == 31 ? INFINITY : ldexpf(mantissa + 1024, exponent - 25);
	return h & 0x8000 ? -value : value;
}

static uint32_t channel(float value)
{
	return value <= 0 ? 0 : value >= 1 ? 255 : (uint32_t)lroundf(value * 255);
}

void bitmap_pack(const struct gfx_bitmap *bitmap, uint8_t *dst, int row_bytes)
{
	for (int y = 0; y < bitmap->height; y++) {
		const uint32_t *words = bitmap->words + (size_t)y * bitmap->width;
		uint8_t *row = dst + (size_t)y * row_bytes;
		for (int x = 0; x < bitmap->width; x++) {
			uint32_t w = words[x], a = w >> 24, r = (w >> 16) & 0xff, g = (w >> 8) & 0xff, b = w & 0xff;
			switch (bitmap->config) {
			case GFX_BITMAP_ALPHA_8:
				row[x] = a;
				break;
			case GFX_BITMAP_RGB_565: {
				uint16_t v = quantize5(r) << 11 | quantize6(g) << 5 | quantize5(b);
				memcpy(row + x * 2, &v, 2);
				break;
			}
			case GFX_BITMAP_ARGB_4444: {
				uint16_t v = quantize4(r) << 12 | quantize4(g) << 8 | quantize4(b) << 4 | quantize4(a);
				memcpy(row + x * 2, &v, 2);
				break;
			}
			case GFX_BITMAP_RGBA_F16: {
				uint16_t v[4] = {half(r / 255.f), half(g / 255.f), half(b / 255.f), half(a / 255.f)};
				memcpy(row + x * 8, v, 8);
				break;
			}
			default:
				row[x * 4] = r, row[x * 4 + 1] = g, row[x * 4 + 2] = b, row[x * 4 + 3] = a;
			}
		}
	}
}

void bitmap_unpack(struct gfx_bitmap *bitmap, const uint8_t *src, int row_bytes)
{
	for (int y = 0; y < bitmap->height; y++) {
		uint32_t *words = bitmap->words + (size_t)y * bitmap->width;
		const uint8_t *row = src + (size_t)y * row_bytes;
		for (int x = 0; x < bitmap->width; x++) {
			uint32_t a, r, g, b;
			switch (bitmap->config) {
			case GFX_BITMAP_ALPHA_8:
				a = row[x], r = g = b = 0;
				break;
			case GFX_BITMAP_RGB_565: {
				uint16_t v;
				memcpy(&v, row + x * 2, 2);
				a = 255, r = expand5(v >> 11), g = expand6((v >> 5) & 63), b = expand5(v & 31);
				break;
			}
			case GFX_BITMAP_ARGB_4444: {
				uint16_t v;
				memcpy(&v, row + x * 2, 2);
				r = (v >> 12) * 17, g = ((v >> 8) & 15) * 17, b = ((v >> 4) & 15) * 17, a = (v & 15) * 17;
				break;
			}
			case GFX_BITMAP_RGBA_F16: {
				uint16_t v[4];
				memcpy(v, row + x * 8, 8);
				r = channel(from_half(v[0])), g = channel(from_half(v[1])), b = channel(from_half(v[2])), a = channel(from_half(v[3]));
				break;
			}
			default:
				r = row[x * 4], g = row[x * 4 + 1], b = row[x * 4 + 2], a = row[x * 4 + 3];
			}
			words[x] = a << 24 | r << 16 | g << 8 | b;
		}
	}
	bitmap_changed(bitmap);
}
