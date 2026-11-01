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
	size_t periods = *k1 - *k0, n = shader->n * periods;
	*colors = malloc(n * sizeof(**colors));
	*positions = malloc(n * sizeof(**positions));
	for (size_t p = 0, out = 0; p < periods; p++) {
		bool mirrored = shader->tile == SHADER_MIRROR && ((*k0 + (int)p) & 1);
		for (size_t i = 0; i < shader->n; i++, out++) {
			size_t j = mirrored ? shader->n - 1 - i : i;
			(*colors)[out] = shader->colors[j];
			(*positions)[out] = (p + (mirrored ? 1 - shader->positions[j] : shader->positions[j])) / periods;
		}
	}
	return n;
}
