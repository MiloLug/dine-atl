#include <math.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

#include "src/slots/gfx.h"

#include "shader.h"

// Skia's: positions clamp to 0..1 and never go back; a first stop past 0 or a last before 1 extends
struct gfx_shader *shader_new(enum shader_kind kind, enum shader_tile tile, const float geometry[5], const graphene_matrix_t *matrix,
                              const uint32_t *colors, const float *positions, size_t n)
{
	struct gfx_shader *shader = calloc(1, sizeof(*shader));
	shader->refs = 1;
	shader->kind = kind;
	shader->tile = tile;
	shader->x0 = geometry[0];
	shader->y0 = geometry[1];
	shader->x1 = geometry[2];
	shader->y1 = geometry[3];
	shader->radius = geometry[4];
	if (matrix)
		shader->matrix = *matrix;
	else
		graphene_matrix_init_identity(&shader->matrix);
	shader->colors = malloc((n + 2) * sizeof(*shader->colors));
	shader->positions = malloc((n + 2) * sizeof(*shader->positions));
	float last = 0;
	for (size_t i = 0; i < n; i++) {
		float position = positions ? fminf(1, fmaxf(last, positions[i])) : n > 1 ? (float)i / (n - 1) : 0;
		if (!shader->n && position > 0) {
			shader->colors[shader->n] = colors[0];
			shader->positions[shader->n++] = 0;
		}
		shader->colors[shader->n] = colors[i];
		shader->positions[shader->n++] = last = position;
	}
	if (!shader->n || last < 1) {
		shader->colors[shader->n] = n ? colors[n - 1] : 0;
		shader->positions[shader->n++] = 1;
	}
	return shader;
}

static struct gfx_shader *shader_alloc(enum shader_kind kind, const graphene_matrix_t *matrix)
{
	struct gfx_shader *shader = calloc(1, sizeof(*shader));
	shader->refs = 1;
	shader->kind = kind;
	if (matrix)
		shader->matrix = *matrix;
	else
		graphene_matrix_init_identity(&shader->matrix);
	return shader;
}

struct gfx_shader *shader_new_bitmap(struct gfx_bitmap *bitmap, enum shader_tile tile_x, enum shader_tile tile_y, const graphene_matrix_t *matrix)
{
	struct gfx_shader *shader = shader_alloc(SHADER_BITMAP, matrix);
	gfx_bitmap_ref(bitmap);
	shader->bitmap = bitmap;
	shader->tile = tile_x;
	shader->tile_y = tile_y;
	return shader;
}

struct gfx_shader *shader_new_compose(struct gfx_shader *dst, struct gfx_shader *src, int mode, const graphene_matrix_t *matrix)
{
	struct gfx_shader *shader = shader_alloc(SHADER_COMPOSE, matrix);
	shader->dst = shader_ref(dst);
	shader->src = shader_ref(src);
	shader->mode = mode;
	return shader;
}

struct gfx_shader *shader_ref(struct gfx_shader *shader)
{
	if (shader)
		atomic_fetch_add(&shader->refs, 1);
	return shader;
}

void shader_unref(struct gfx_shader *shader)
{
	if (!shader || atomic_fetch_sub(&shader->refs, 1) != 1)
		return;
	free(shader->colors);
	free(shader->positions);
	if (shader->bitmap)
		gfx_bitmap_unref(shader->bitmap);
	shader_unref(shader->dst);
	shader_unref(shader->src);
	free(shader);
}

void shader_t_range(const struct gfx_shader *shader, const struct gfx_rect *rect, float *t_min, float *t_max)
{
	const float corners[4][2] = {{rect->left, rect->top}, {rect->right, rect->top}, {rect->left, rect->bottom}, {rect->right, rect->bottom}};
	*t_min = 0;
	*t_max = 1;
	if (shader->kind == SHADER_LINEAR) {
		float dx = shader->x1 - shader->x0, dy = shader->y1 - shader->y0, length2 = dx * dx + dy * dy;
		if (!length2)
			return;
		*t_min = INFINITY;
		*t_max = -INFINITY;
		for (int i = 0; i < 4; i++) {
			float t = ((corners[i][0] - shader->x0) * dx + (corners[i][1] - shader->y0) * dy) / length2;
			*t_min = fminf(*t_min, t);
			*t_max = fmaxf(*t_max, t);
		}
	} else if (shader->kind == SHADER_RADIAL && shader->radius > 0) {
		*t_max = 0;
		for (int i = 0; i < 4; i++)
			*t_max = fmaxf(*t_max, hypotf(corners[i][0] - shader->x0, corners[i][1] - shader->y0) / shader->radius);
		float nearest_x = fminf(fmaxf(shader->x0, rect->left), rect->right), nearest_y = fminf(fmaxf(shader->y0, rect->top), rect->bottom);
		*t_min = hypotf(nearest_x - shader->x0, nearest_y - shader->y0) / shader->radius;
	}
}

// more periods than a screen shows only at absurd scales
#define MAX_PERIODS 256
// pieces a pair of stops with different alpha is cut into
#define PREMUL_STEPS 8

uint32_t shader_mix(uint32_t c0, uint32_t c1, float f)
{
	float a0 = c0 >> 24, a1 = c1 >> 24, a = a0 + (a1 - a0) * f;
	uint32_t out = (uint32_t)lroundf(a) << 24;
	for (int shift = 0; a > 0 && shift < 24; shift += 8) {
		float premultiplied = ((c0 >> shift) & 0xff) * a0 * (1 - f) + ((c1 >> shift) & 0xff) * a1 * f;
		out |= (uint32_t)lroundf(premultiplied / a) << shift;
	}
	return out;
}

size_t shader_ramp(const struct gfx_shader *shader, uint32_t **colors, float **positions)
{
	size_t n = 0;
	*colors = malloc(shader->n * PREMUL_STEPS * sizeof(**colors));
	*positions = malloc(shader->n * PREMUL_STEPS * sizeof(**positions));
	for (size_t i = 0; i < shader->n; i++) {
		(*colors)[n] = shader->colors[i];
		(*positions)[n++] = shader->positions[i];
		if (i + 1 == shader->n || shader->colors[i] >> 24 == shader->colors[i + 1] >> 24)
			continue;
		for (int step = 1; step < PREMUL_STEPS; step++) {
			float f = (float)step / PREMUL_STEPS;
			(*colors)[n] = shader_mix(shader->colors[i], shader->colors[i + 1], f);
			(*positions)[n++] = shader->positions[i] + (shader->positions[i + 1] - shader->positions[i]) * f;
		}
	}
	return n;
}

size_t shader_stops(const struct gfx_shader *shader, float t_min, float t_max, int *k0, int *k1, uint32_t **colors, float **positions)
{
	*k0 = 0;
	*k1 = 1;
	if (shader->tile != SHADER_CLAMP && shader->kind != SHADER_SWEEP && isfinite(t_min) && isfinite(t_max)) {
		*k0 = floorf(fmaxf(t_min, -1e6));
		*k1 = fmaxf(ceilf(fminf(t_max, 1e6)), *k0 + 1);
		if (*k1 - *k0 > MAX_PERIODS)
			*k1 = *k0 + MAX_PERIODS;
	}
	uint32_t *ramp_colors;
	float *ramp_positions;
	size_t ramp = shader_ramp(shader, &ramp_colors, &ramp_positions);
	size_t periods = *k1 - *k0, n = ramp * periods;
	*colors = malloc(n * sizeof(**colors));
	*positions = malloc(n * sizeof(**positions));
	for (size_t p = 0, out = 0; p < periods; p++) {
		bool mirrored = shader->tile == SHADER_MIRROR && ((*k0 + (int)p) & 1);
		for (size_t i = 0; i < ramp; i++, out++) {
			size_t j = mirrored ? ramp - 1 - i : i;
			(*colors)[out] = ramp_colors[j];
			(*positions)[out] = (p + (mirrored ? 1 - ramp_positions[j] : ramp_positions[j])) / periods;
		}
	}
	free(ramp_colors);
	free(ramp_positions);
	return n;
}
