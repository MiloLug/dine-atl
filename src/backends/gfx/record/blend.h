#ifndef GFX_RECORD_BLEND_H
#define GFX_RECORD_BLEND_H

#include <math.h>
#include <stdint.h>

#include "record.h"

/* PorterDuff.Mode's nativeInt for the backends that replay record's lists. */

struct blend_color {
	float a, r, g, b; // premultiplied
};

static inline struct blend_color blend_unpack(uint32_t argb)
{
	float a = (argb >> 24) / 255.f;
	return (struct blend_color){a, ((argb >> 16) & 0xff) / 255.f * a, ((argb >> 8) & 0xff) / 255.f * a, (argb & 0xff) / 255.f * a};
}

// one premultiplied channel of PorterDuff mode `mode`, the filter's color over the paint's
static inline float blend_channel(int mode, float sa, float sc, float da, float dc)
{
	switch (mode) {
	case 0: return 0;                                    // CLEAR
	case 1: return sc;                                   // SRC
	case 2: return dc;                                   // DST
	case 3: return sc + dc * (1 - sa);                   // SRC_OVER
	case 4: return dc + sc * (1 - da);                   // DST_OVER
	case 5: return sc * da;                              // SRC_IN
	case 6: return dc * sa;                              // DST_IN
	case 7: return sc * (1 - da);                        // SRC_OUT
	case 8: return dc * (1 - sa);                        // DST_OUT
	case 9: return sc * da + dc * (1 - sa);              // SRC_ATOP
	case 10: return dc * sa + sc * (1 - da);             // DST_ATOP
	case 11: return sc * (1 - da) + dc * (1 - sa);       // XOR
	case 12: return sc + dc - fmaxf(sc * da, dc * sa); // DARKEN
	case 13: return sc + dc - fminf(sc * da, dc * sa); // LIGHTEN
	case 14: return sc * dc;                             // MULTIPLY
	case 15: return sc + dc - sc * dc;                   // SCREEN
	case 16: return fminf(1, sc + dc);                   // ADD
	case 17:                                             // OVERLAY
		return sc * (1 - da) + dc * (1 - sa) + (2 * dc <= da ? 2 * sc * dc : sa * da - 2 * (da - dc) * (sa - sc));
	default: return dc;
	}
}

// a ColorMatrix over an unpremultiplied ARGB color
static inline uint32_t blend_color_matrix(const float m[20], uint32_t argb)
{
	float in[4] = {(argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff, argb >> 24};
	uint32_t out[4];
	for (int row = 0; row < 4; row++) {
		float v = m[row * 5] * in[0] + m[row * 5 + 1] * in[1] + m[row * 5 + 2] * in[2] + m[row * 5 + 3] * in[3] + m[row * 5 + 4];
		out[row] = v <= 0 ? 0 : v >= 255 ? 255 : (uint32_t)(v + 0.5f);
	}
	return out[3] << 24 | out[0] << 16 | out[1] << 8 | out[2];
}

// a solid paint's color through its color filter, premultiplied
static inline struct blend_color blend_paint_color(const struct gfx_paint *paint)
{
	if (paint->filter.has_matrix)
		return blend_unpack(blend_color_matrix(paint->filter.m, paint->color));
	struct blend_color d = blend_unpack(paint->color);
	if (paint->filter.mode == -1)
		return d;
	struct blend_color s = blend_unpack(paint->filter.color);
	int mode = paint->filter.mode;
	// the alpha of the separable modes but MULTIPLY's (Skia's modulate) is SRC_OVER's
	float a = mode <= 11 ? blend_channel(mode, s.a, s.a, d.a, d.a) : mode == 14 ? s.a * d.a : mode == 16 ? fminf(1, s.a + d.a) : s.a + d.a - s.a * d.a;
	return (struct blend_color){a, blend_channel(mode, s.a, s.r, d.a, d.r), blend_channel(mode, s.a, s.g, d.a, d.g), blend_channel(mode, s.a, s.b, d.a, d.b)};
}

#endif
