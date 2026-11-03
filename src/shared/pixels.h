#ifndef SHARED_PIXELS_H
#define SHARED_PIXELS_H

#include <stdint.h>

/* Color's ARGB ints and the premultiplied ARGB words that cairo's ARGB32, CoreGraphics' 32-bit
 * little-endian premultiplied-first and Skia's N32 keep: the same layout. */

static inline uint32_t argb_premultiply(uint32_t argb)
{
	uint32_t a = argb >> 24;
	if (a == 255)
		return argb;
	uint32_t r = ((argb >> 16) & 0xff) * a, g = ((argb >> 8) & 0xff) * a, b = (argb & 0xff) * a;
	return a << 24 | ((r + 127) / 255) << 16 | ((g + 127) / 255) << 8 | (b + 127) / 255;
}

static inline uint32_t argb_unpremultiply(uint32_t premultiplied)
{
	uint32_t a = premultiplied >> 24;
	if (a == 255)
		return premultiplied;
	if (a == 0)
		return 0;
	uint32_t r = (premultiplied >> 16) & 0xff, g = (premultiplied >> 8) & 0xff, b = premultiplied & 0xff;
	r = (r * 255 + a / 2) / a, g = (g * 255 + a / 2) / a, b = (b * 255 + a / 2) / a;
	return a << 24 | (r > 255 ? 255 : r) << 16 | (g > 255 ? 255 : g) << 8 | (b > 255 ? 255 : b);
}

#endif
