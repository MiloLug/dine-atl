#ifndef SHARED_MATRIX_H
#define SHARED_MATRIX_H

#include <graphene.h>
#include <math.h>

/* A rotation about (px, py) as Skia's SkScalarSinCos makes it from degrees: a sin or cos within
 * 1/4096 of 0 is 0, so quarter turns map rects to rects exactly. */
static inline graphene_matrix_t *matrix_init_rotate(graphene_matrix_t *matrix, float degrees, float px, float py)
{
	float radians = degrees * (float)(M_PI / 180);
	float sin_v = sinf(radians), cos_v = cosf(radians);
	if (fabsf(sin_v) <= 1.f / 4096)
		sin_v = 0;
	if (fabsf(cos_v) <= 1.f / 4096)
		cos_v = 0;
	return graphene_matrix_init_from_2d(matrix, cos_v, sin_v, -sin_v, cos_v, sin_v * py + (1 - cos_v) * px, -sin_v * px + (1 - cos_v) * py);
}

#endif
