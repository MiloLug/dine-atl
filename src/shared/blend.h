#ifndef SHARED_BLEND_H
#define SHARED_BLEND_H

#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#include "src/slots/gfx.h"

#include "pixels.h"

/* Skia's blend modes (enum gfx_blend) over premultiplied colors, for the backends that blend on the
 * CPU: SkRasterPipeline's separable stages, W3C's non-separable ones. */

struct blend_color {
	float a, r, g, b; // premultiplied
};

static inline struct blend_color blend_unpack(uint32_t argb)
{
	float a = (argb >> 24) / 255.f;
	return (struct blend_color){a, ((argb >> 16) & 0xff) / 255.f * a, ((argb >> 8) & 0xff) / 255.f * a, (argb & 0xff) / 255.f * a};
}

// cairo's ARGB32 word, premultiplied
static inline struct blend_color blend_from_word(uint32_t word)
{
	return (struct blend_color){(word >> 24) / 255.f, ((word >> 16) & 0xff) / 255.f, ((word >> 8) & 0xff) / 255.f, (word & 0xff) / 255.f};
}

static inline uint32_t blend_channel_byte(float v)
{
	return v <= 0 ? 0 : v >= 1 ? 255 : (uint32_t)(v * 255 + 0.5f);
}

static inline uint32_t blend_to_word(struct blend_color c)
{
	uint32_t a = blend_channel_byte(c.a), r = blend_channel_byte(c.r), g = blend_channel_byte(c.g), b = blend_channel_byte(c.b);
	return a << 24 | (r < a ? r : a) << 16 | (g < a ? g : a) << 8 | (b < a ? b : a);
}

static inline float blend_hard_light(float s, float d, float sa, float da)
{
	return s * (1 - da) + d * (1 - sa) + (2 * s <= sa ? 2 * s * d : sa * da - 2 * (da - d) * (sa - s));
}

static inline float blend_separable(enum gfx_blend mode, float s, float d, float sa, float da)
{
	switch (mode) {
	case GFX_BLEND_OVERLAY: return blend_hard_light(d, s, da, sa);
	case GFX_BLEND_DARKEN: return s + d - fmaxf(s * da, d * sa);
	case GFX_BLEND_LIGHTEN: return s + d - fminf(s * da, d * sa);
	case GFX_BLEND_COLOR_DODGE:
		if (d == 0)
			return s * (1 - da);
		if (s == sa)
			return s + d * (1 - sa);
		return sa * fminf(da, d * sa / (sa - s)) + s * (1 - da) + d * (1 - sa);
	case GFX_BLEND_COLOR_BURN:
		if (d == da)
			return d + s * (1 - da);
		if (s == 0)
			return d * (1 - sa);
		return sa * (da - fminf(da, (da - d) * sa / s)) + s * (1 - da) + d * (1 - sa);
	case GFX_BLEND_HARD_LIGHT: return blend_hard_light(s, d, sa, da);
	case GFX_BLEND_SOFT_LIGHT: {
		float m = da > 0 ? d / da : 0, s2 = 2 * s, m4 = 4 * m;
		float dark_src = d * (sa + (s2 - sa) * (1 - m));
		float dark_dst = (m4 * m4 + m4) * (m - 1) + 7 * m;
		float lite_dst = sqrtf(m) - m;
		float lite_src = d * sa + da * (s2 - sa) * (4 * d <= da ? dark_dst : lite_dst);
		return s * (1 - da) + d * (1 - sa) + (s2 <= sa ? dark_src : lite_src);
	}
	case GFX_BLEND_DIFFERENCE: return s + d - 2 * fminf(s * da, d * sa);
	case GFX_BLEND_EXCLUSION: return s + d - 2 * s * d;
	default: return s * (1 - da) + d * (1 - sa) + s * d; // MULTIPLY
	}
}

static inline float blend_lum(const float c[3])
{
	return 0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2];
}

static inline void blend_set_lum(float c[3], float l)
{
	float delta = l - blend_lum(c);
	for (int i = 0; i < 3; i++)
		c[i] += delta;
	float lo = blend_lum(c), min = fminf(c[0], fminf(c[1], c[2])), max = fmaxf(c[0], fmaxf(c[1], c[2]));
	for (int i = 0; i < 3; i++) {
		if (min < 0)
			c[i] = lo + (c[i] - lo) * lo / (lo - min);
		if (max > 1)
			c[i] = lo + (c[i] - lo) * (1 - lo) / (max - lo);
	}
}

static inline void blend_set_sat(float c[3], float s)
{
	float max = fmaxf(c[0], fmaxf(c[1], c[2])), min = fminf(c[0], fminf(c[1], c[2]));
	for (int i = 0; i < 3; i++)
		c[i] = max > min ? (c[i] - min) * s / (max - min) : 0;
}

static inline struct blend_color blend_non_separable(enum gfx_blend mode, struct blend_color s, struct blend_color d)
{
	float cs[3] = {s.a > 0 ? s.r / s.a : 0, s.a > 0 ? s.g / s.a : 0, s.a > 0 ? s.b / s.a : 0};
	float cb[3] = {d.a > 0 ? d.r / d.a : 0, d.a > 0 ? d.g / d.a : 0, d.a > 0 ? d.b / d.a : 0};
	float sat_s = fmaxf(cs[0], fmaxf(cs[1], cs[2])) - fminf(cs[0], fminf(cs[1], cs[2]));
	float sat_b = fmaxf(cb[0], fmaxf(cb[1], cb[2])) - fminf(cb[0], fminf(cb[1], cb[2]));
	float b[3];
	switch (mode) {
	case GFX_BLEND_HUE:
		b[0] = cs[0], b[1] = cs[1], b[2] = cs[2];
		blend_set_sat(b, sat_b);
		blend_set_lum(b, blend_lum(cb));
		break;
	case GFX_BLEND_SATURATION:
		b[0] = cb[0], b[1] = cb[1], b[2] = cb[2];
		blend_set_sat(b, sat_s);
		blend_set_lum(b, blend_lum(cb));
		break;
	case GFX_BLEND_COLOR:
		b[0] = cs[0], b[1] = cs[1], b[2] = cs[2];
		blend_set_lum(b, blend_lum(cb));
		break;
	default: // LUMINOSITY
		b[0] = cb[0], b[1] = cb[1], b[2] = cb[2];
		blend_set_lum(b, blend_lum(cs));
		break;
	}
	float k = s.a * d.a;
	return (struct blend_color){s.a + d.a - k, s.r * (1 - d.a) + d.r * (1 - s.a) + k * b[0], s.g * (1 - d.a) + d.g * (1 - s.a) + k * b[1],
	                            s.b * (1 - d.a) + d.b * (1 - s.a) + k * b[2]};
}

// s mixed with fs plus d with fd, every channel
static inline struct blend_color blend_mix(struct blend_color s, float fs, struct blend_color d, float fd)
{
	return (struct blend_color){s.a * fs + d.a * fd, s.r * fs + d.r * fd, s.g * fs + d.g * fd, s.b * fs + d.b * fd};
}

static inline struct blend_color blend(enum gfx_blend mode, struct blend_color s, struct blend_color d)
{
	switch (mode) {
	case GFX_BLEND_CLEAR: return (struct blend_color){0};
	case GFX_BLEND_SRC: return s;
	case GFX_BLEND_DST: return d;
	case GFX_BLEND_SRC_OVER: return blend_mix(s, 1, d, 1 - s.a);
	case GFX_BLEND_DST_OVER: return blend_mix(d, 1, s, 1 - d.a);
	case GFX_BLEND_SRC_IN: return blend_mix(s, d.a, d, 0);
	case GFX_BLEND_DST_IN: return blend_mix(d, s.a, s, 0);
	case GFX_BLEND_SRC_OUT: return blend_mix(s, 1 - d.a, d, 0);
	case GFX_BLEND_DST_OUT: return blend_mix(d, 1 - s.a, s, 0);
	case GFX_BLEND_SRC_ATOP: return blend_mix(s, d.a, d, 1 - s.a);
	case GFX_BLEND_DST_ATOP: return blend_mix(d, s.a, s, 1 - d.a);
	case GFX_BLEND_XOR: return blend_mix(s, 1 - d.a, d, 1 - s.a);
	case GFX_BLEND_PLUS: return (struct blend_color){fminf(1, s.a + d.a), fminf(1, s.r + d.r), fminf(1, s.g + d.g), fminf(1, s.b + d.b)};
	case GFX_BLEND_MODULATE: return (struct blend_color){s.a * d.a, s.r * d.r, s.g * d.g, s.b * d.b};
	case GFX_BLEND_SCREEN: return (struct blend_color){s.a + d.a - s.a * d.a, s.r + d.r - s.r * d.r, s.g + d.g - s.g * d.g, s.b + d.b - s.b * d.b};
	case GFX_BLEND_HUE:
	case GFX_BLEND_SATURATION:
	case GFX_BLEND_COLOR:
	case GFX_BLEND_LUMINOSITY: return blend_non_separable(mode, s, d);
	default:
		if (mode >= GFX_BLEND_COUNT)
			return d;
		return (struct blend_color){s.a + d.a - s.a * d.a, blend_separable(mode, s.r, d.r, s.a, d.a), blend_separable(mode, s.g, d.g, s.a, d.a),
		                            blend_separable(mode, s.b, d.b, s.a, d.a)};
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

// a color, premultiplied, through a color filter
static inline struct blend_color blend_filter(const struct gfx_color_filter *filter, struct blend_color c)
{
	if (filter->has_matrix) {
		uint32_t a = blend_channel_byte(c.a);
		uint32_t argb = a ? a << 24 | blend_channel_byte(c.r / c.a) << 16 | blend_channel_byte(c.g / c.a) << 8 | blend_channel_byte(c.b / c.a) : 0;
		return blend_unpack(blend_color_matrix(filter->m, argb));
	}
	return filter->mode < 0 ? c : blend(filter->mode, blend_unpack(filter->color), c);
}

/* The filter as a ColorMatrix (unpremultiplied colors, offsets 0-255) where one is exact: a mode
 * whose result is linear in the unpremultiplied source. false: none is. */
static inline bool blend_filter_matrix(const struct gfx_color_filter *filter, float m[20])
{
	if (filter->has_matrix) {
		memcpy(m, filter->m, sizeof(filter->m));
		return true;
	}
	uint32_t color = filter->color;
	float a = (color >> 24) / 255.f, rgb[3] = {((color >> 16) & 0xff) / 255.f, ((color >> 8) & 0xff) / 255.f, (color & 0xff) / 255.f};
	memset(m, 0, 20 * sizeof(*m));
	for (int i = 0; i < 3; i++) {
		float *row = m + i * 5;
		switch (filter->mode) {
		case GFX_BLEND_CLEAR: break;
		case GFX_BLEND_SRC: case GFX_BLEND_SRC_IN: case GFX_BLEND_SRC_OUT: row[4] = rgb[i] * 255; break;
		case GFX_BLEND_DST_IN: case GFX_BLEND_DST_OUT: row[i] = 1; break;
		case GFX_BLEND_SRC_ATOP: row[i] = 1 - a, row[4] = a * rgb[i] * 255; break;
		case GFX_BLEND_MODULATE: row[i] = rgb[i]; break;
		default: return false; // none, DST, or not linear
		}
	}
	float *alpha = m + 15;
	switch (filter->mode) {
	case GFX_BLEND_SRC: alpha[4] = a * 255; break;
	case GFX_BLEND_SRC_IN: case GFX_BLEND_DST_IN: case GFX_BLEND_MODULATE: alpha[3] = a; break;
	case GFX_BLEND_DST_OUT: alpha[3] = 1 - a; break;
	case GFX_BLEND_SRC_OUT: alpha[3] = -a, alpha[4] = a * 255; break;
	case GFX_BLEND_SRC_ATOP: alpha[3] = 1; break;
	default: break; // CLEAR
	}
	return true;
}

// a premultiplied ARGB pixel word, scaled by alpha, through a color filter (Skia's order)
static inline uint32_t blend_filter_pixel(const struct gfx_color_filter *filter, uint32_t word, float alpha)
{
	if (alpha < 1) {
		struct blend_color c = blend_from_word(word);
		word = blend_to_word((struct blend_color){c.a * alpha, c.r * alpha, c.g * alpha, c.b * alpha});
	}
	if (filter->has_matrix)
		return argb_premultiply(blend_color_matrix(filter->m, argb_unpremultiply(word)));
	return blend_to_word(blend_filter(filter, blend_from_word(word)));
}

// an unpremultiplied ARGB color through a color filter: a matrix takes it as it is
static inline struct blend_color blend_filter_word(const struct gfx_color_filter *filter, uint32_t argb)
{
	if (filter->has_matrix)
		return blend_unpack(blend_color_matrix(filter->m, argb));
	return blend_filter(filter, blend_unpack(argb));
}

#endif
