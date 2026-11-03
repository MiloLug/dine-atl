#ifndef SHARED_SHADER_H
#define SHARED_SHADER_H

#include <stdatomic.h>
#include <stddef.h>
#include <stdint.h>

#include <graphene.h>

struct gfx_rect;
struct gfx_bitmap;

/* A Shader for every gfx backend; paints and display lists hold refs, which drop on any thread. A
 * gradient's stops run from 0 to 1. */

enum shader_kind {
	SHADER_LINEAR, // x0, y0 to x1, y1
	SHADER_RADIAL, // center x0, y0, radius
	SHADER_SWEEP,  // center x0, y0, clockwise from the right
	SHADER_BITMAP, // bitmap at 0, 0 in its pixels, tile x and tile_y
	SHADER_COMPOSE, // dst, then src over it in PorterDuff mode `mode`, each in this one's space
};

enum shader_tile { // Shader.TileMode.nativeInt
	SHADER_CLAMP,
	SHADER_REPEAT,
	SHADER_MIRROR,
};

struct gfx_shader {
	atomic_int refs;
	enum shader_kind kind;
	enum shader_tile tile;
	enum shader_tile tile_y; // SHADER_BITMAP's
	float x0, y0, x1, y1, radius;
	struct gfx_bitmap *bitmap;      // SHADER_BITMAP's, held
	struct gfx_shader *dst, *src;   // SHADER_COMPOSE's, held
	int mode;
	graphene_matrix_t matrix; // the local matrix
	size_t n;
	uint32_t *colors; // ARGB
	float *positions;
};

/* positions: NULL spaces the colors evenly */
struct gfx_shader *shader_new(enum shader_kind kind, enum shader_tile tile, const float geometry[5], const graphene_matrix_t *matrix,
                              const uint32_t *colors, const float *positions, size_t n);
struct gfx_shader *shader_new_bitmap(struct gfx_bitmap *bitmap, enum shader_tile tile_x, enum shader_tile tile_y, const graphene_matrix_t *matrix);
struct gfx_shader *shader_new_compose(struct gfx_shader *dst, struct gfx_shader *src, int mode, const graphene_matrix_t *matrix);
struct gfx_shader *shader_ref(struct gfx_shader *shader);
void shader_unref(struct gfx_shader *shader);

/* The gradient's parameter t over a rect in its own space (the local matrix undone): 0 to 1 is one
 * period (linear: from the start point to the end; radial: from the center out to the radius). */
void shader_t_range(const struct gfx_shader *shader, const struct gfx_rect *rect, float *t_min, float *t_max);
/* Stops for t from k0 to k1 (whole periods covering t_min..t_max) at positions 0 to 1, so a
 * backend draws REPEAT and MIRROR as a clamped gradient from k0 to k1; CLAMP: the shader's own,
 * k0 0 and k1 1. Returns the count; colors and positions are malloc'd. */
size_t shader_stops(const struct gfx_shader *shader, float t_min, float t_max, int *k0, int *k1, uint32_t **colors, float **positions);

#endif
