#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "src/slots/gfx.h"

#include "path.h"
#include "region.h"

struct band {
	int top, bottom;
	size_t first, count; // spans first .. first + count - 1 in xs
};

struct region {
	struct band *bands;
	size_t n_bands, bands_size;
	int *xs; // each span's left and right
	size_t n_spans, spans_size;
};

static void *grow(void *array, size_t *size, size_t needed, size_t item)
{
	if (needed <= *size)
		return array;
	*size = needed > *size * 2 ? needed : *size * 2;
	return realloc(array, *size * item);
}

static void clear(struct region *region)
{
	region->n_bands = region->n_spans = 0;
}

/* xs: count spans; a band equal to the last one and touching it extends it */
static void append_band(struct region *region, int top, int bottom, const int *xs, size_t count)
{
	if (!count || bottom <= top)
		return;
	if (region->n_bands) {
		struct band *last = &region->bands[region->n_bands - 1];
		if (last->bottom == top && last->count == count &&
		    !memcmp(&region->xs[last->first * 2], xs, count * 2 * sizeof(*xs))) {
			last->bottom = bottom;
			return;
		}
	}
	region->bands = grow(region->bands, &region->bands_size, region->n_bands + 1, sizeof(*region->bands));
	region->xs = grow(region->xs, &region->spans_size, region->n_spans + count, 2 * sizeof(*region->xs));
	memcpy(&region->xs[region->n_spans * 2], xs, count * 2 * sizeof(*xs));
	region->bands[region->n_bands++] = (struct band){top, bottom, region->n_spans, count};
	region->n_spans += count;
}

static void swap(struct region *a, struct region *b)
{
	struct region tmp = *a;
	*a = *b;
	*b = tmp;
}

struct region *region_new(void)
{
	return calloc(1, sizeof(struct region));
}

void region_free(struct region *region)
{
	free(region->bands);
	free(region->xs);
	free(region);
}

void region_set(struct region *dst, const struct region *src)
{
	if (dst == src)
		return;
	clear(dst);
	for (size_t i = 0; i < src->n_bands; i++) {
		const struct band *band = &src->bands[i];
		append_band(dst, band->top, band->bottom, &src->xs[band->first * 2], band->count);
	}
}

void region_set_rect(struct region *region, int left, int top, int right, int bottom)
{
	clear(region);
	if (right > left)
		append_band(region, top, bottom, (int[]){left, right}, 1);
}

static bool op_inside(enum region_op op, bool a, bool b)
{
	switch (op) {
		case REGION_DIFFERENCE:
			return a && !b;
		case REGION_INTERSECT:
			return a && b;
		case REGION_UNION:
			return a || b;
		case REGION_XOR:
			return a != b;
		case REGION_REVERSE_DIFFERENCE:
			return b && !a;
		case REGION_REPLACE:
			return b;
	}
	return false;
}

// out: room for n_a + n_b spans
static size_t merge_spans(const int *a, size_t n_a, const int *b, size_t n_b, enum region_op op, int *out)
{
	size_t i = 0, j = 0, n = 0;
	bool in = false;
	n_a *= 2;
	n_b *= 2;
	while (i < n_a || j < n_b) {
		int x = i < n_a && (j >= n_b || a[i] <= b[j]) ? a[i] : b[j];
		// an edge at an even index starts a span
		while (i < n_a && a[i] == x)
			i++;
		while (j < n_b && b[j] == x)
			j++;
		bool now = op_inside(op, i & 1, j & 1);
		if (now != in) {
			out[n++] = x;
			in = now;
		}
	}
	return n / 2;
}

static int compare_int(const void *a, const void *b)
{
	int x = *(const int *)a, y = *(const int *)b;
	return (x > y) - (x < y);
}

/* the band of region covering y, from index *i on; y only grows between calls */
static const struct band *band_at(const struct region *region, size_t *i, int y)
{
	while (*i < region->n_bands && region->bands[*i].bottom <= y)
		(*i)++;
	return *i < region->n_bands && region->bands[*i].top <= y ? &region->bands[*i] : NULL;
}

void region_op(struct region *dst, const struct region *a, const struct region *b, enum region_op op)
{
	struct region out = {0};
	size_t n_ys = 0;
	int *ys = malloc((a->n_bands + b->n_bands) * 2 * sizeof(*ys) + 1);
	for (size_t i = 0; i < a->n_bands; i++) {
		ys[n_ys++] = a->bands[i].top;
		ys[n_ys++] = a->bands[i].bottom;
	}
	for (size_t i = 0; i < b->n_bands; i++) {
		ys[n_ys++] = b->bands[i].top;
		ys[n_ys++] = b->bands[i].bottom;
	}
	qsort(ys, n_ys, sizeof(*ys), compare_int);

	int *spans = malloc((a->n_spans + b->n_spans) * 2 * sizeof(*spans) + 1);
	size_t ia = 0, ib = 0;
	for (size_t k = 0; k + 1 < n_ys; k++) {
		if (ys[k] == ys[k + 1])
			continue;
		const struct band *band_a = band_at(a, &ia, ys[k]);
		const struct band *band_b = band_at(b, &ib, ys[k]);
		size_t n = merge_spans(band_a ? &a->xs[band_a->first * 2] : NULL, band_a ? band_a->count : 0,
		                       band_b ? &b->xs[band_b->first * 2] : NULL, band_b ? band_b->count : 0, op, spans);
		append_band(&out, ys[k], ys[k + 1], spans, n);
	}
	free(spans);
	free(ys);

	swap(dst, &out);
	free(out.bands);
	free(out.xs);
}

struct edges {
	float *xy; // x0, y0, x1, y1 each, y0 != y1
	size_t n, size;
};

static void add_edge(void *data, float x0, float y0, float x1, float y1)
{
	struct edges *edges = data;
	if (y0 == y1)
		return;
	edges->xy = grow(edges->xy, &edges->size, edges->n + 1, 4 * sizeof(*edges->xy));
	memcpy(&edges->xy[edges->n++ * 4], (float[]){x0, y0, x1, y1}, 4 * sizeof(float));
}

struct crossing {
	float x;
	int winding;
};

static int compare_crossing(const void *a, const void *b)
{
	float x = ((const struct crossing *)a)->x, y = ((const struct crossing *)b)->x;
	return (x > y) - (x < y);
}

/* Skia's non-antialiased rounding: a pixel row or column is in when its centre is past the start
 * edge and not past the end one. */
static int round_edge(float v)
{
	return (int)floorf(v + 0.5f);
}

static void rasterize(struct region *dst, const struct edges *edges, struct region_rect area, bool even_odd, bool inverse)
{
	struct crossing *crossings = malloc((edges->n + 1) * sizeof(*crossings));
	int *spans = malloc((edges->n + 2) * 2 * sizeof(*spans));
	for (int y = area.top; y < area.bottom; y++) {
		float cy = y + 0.5f;
		size_t n = 0;
		for (size_t i = 0; i < edges->n; i++) {
			const float *e = &edges->xy[i * 4];
			if ((e[1] < cy) == (e[3] < cy))
				continue;
			float x = e[0] + (cy - e[1]) * (e[2] - e[0]) / (e[3] - e[1]);
			crossings[n++] = (struct crossing){x, e[3] > e[1] ? 1 : -1};
		}
		qsort(crossings, n, sizeof(*crossings), compare_crossing);

		size_t n_spans = 0;
		int winding = 0;
		float start = -INFINITY;
		for (size_t i = 0; i <= n; i++) {
			bool in = (even_odd ? winding & 1 : winding != 0) != inverse;
			float end = i < n ? crossings[i].x : INFINITY;
			if (in && end > start) {
				int left = start == -INFINITY ? area.left : round_edge(start);
				int right = end == INFINITY ? area.right : round_edge(end);
				left = left < area.left ? area.left : left;
				right = right > area.right ? area.right : right;
				if (right > left) {
					if (n_spans && spans[n_spans * 2 - 1] >= left) // touches the last one
						spans[n_spans * 2 - 1] = right;
					else {
						spans[n_spans * 2] = left;
						spans[n_spans * 2 + 1] = right;
						n_spans++;
					}
				}
			}
			if (i < n) {
				winding += crossings[i].winding;
				start = end;
			}
		}
		append_band(dst, y, y + 1, spans, n_spans);
	}
	free(spans);
	free(crossings);
}

void region_set_path(struct region *dst, const struct gfx_path *path, const struct region *clip, bool even_odd, bool inverse)
{
	struct region out = {0};
	struct region_rect area;
	if (clip && region_is_empty(clip))
		goto done;
	if (inverse) {
		if (!clip)
			goto done;
		area = region_bounds(clip);
	} else {
		struct gfx_rect bounds;
		path_bounds(path, &bounds);
		const float limit = 0x1p30f; // beyond an int a cast is undefined
		if (!(fabsf(bounds.left) < limit && fabsf(bounds.top) < limit && fabsf(bounds.right) < limit && fabsf(bounds.bottom) < limit))
			goto done;
		area = (struct region_rect){floorf(bounds.left), floorf(bounds.top), ceilf(bounds.right), ceilf(bounds.bottom)};
		if (clip) {
			struct region_rect c = region_bounds(clip);
			area.left = area.left > c.left ? area.left : c.left;
			area.top = area.top > c.top ? area.top : c.top;
			area.right = area.right < c.right ? area.right : c.right;
			area.bottom = area.bottom < c.bottom ? area.bottom : c.bottom;
		}
	}
	if (area.right <= area.left || area.bottom <= area.top)
		goto done;

	struct edges edges = {0};
	path_flatten(path, add_edge, &edges);
	rasterize(&out, &edges, area, even_odd, inverse);
	free(edges.xy);
	if (clip)
		region_op(&out, &out, clip, REGION_INTERSECT);
done:
	swap(dst, &out);
	free(out.bands);
	free(out.xs);
}

void region_translate(struct region *region, int dx, int dy)
{
	for (size_t i = 0; i < region->n_bands; i++) {
		region->bands[i].top += dy;
		region->bands[i].bottom += dy;
	}
	for (size_t i = 0; i < region->n_spans * 2; i++)
		region->xs[i] += dx;
}

void region_scale(struct region *dst, const struct region *src, float scale)
{
	struct region out = {0}, rect = {0};
	for (size_t i = 0; scale > 0 && i < region_rect_count(src); i++) {
		struct region_rect r = region_rect_at(src, i);
		region_set_rect(&rect, roundf(r.left * scale), roundf(r.top * scale), roundf(r.right * scale), roundf(r.bottom * scale));
		region_op(&out, &out, &rect, REGION_UNION);
	}
	swap(dst, &out);
	free(out.bands);
	free(out.xs);
	free(rect.bands);
	free(rect.xs);
}

bool region_is_empty(const struct region *region)
{
	return !region->n_bands;
}

size_t region_rect_count(const struct region *region)
{
	return region->n_spans;
}

struct region_rect region_rect_at(const struct region *region, size_t i)
{
	size_t b = 0;
	while (i >= region->bands[b].first + region->bands[b].count)
		b++;
	return (struct region_rect){region->xs[i * 2], region->bands[b].top, region->xs[i * 2 + 1], region->bands[b].bottom};
}

struct region_rect region_bounds(const struct region *region)
{
	if (region_is_empty(region))
		return (struct region_rect){0};
	struct region_rect bounds = {region->xs[0], region->bands[0].top, region->xs[1], region->bands[region->n_bands - 1].bottom};
	for (size_t i = 0; i < region->n_bands; i++) {
		const struct band *band = &region->bands[i];
		if (region->xs[band->first * 2] < bounds.left)
			bounds.left = region->xs[band->first * 2];
		if (region->xs[(band->first + band->count) * 2 - 1] > bounds.right)
			bounds.right = region->xs[(band->first + band->count) * 2 - 1];
	}
	return bounds;
}

bool region_contains(const struct region *region, int x, int y)
{
	size_t b = 0;
	const struct band *band = band_at(region, &b, y);
	for (size_t i = 0; band && i < band->count; i++) {
		const int *span = &region->xs[(band->first + i) * 2];
		if (x >= span[0] && x < span[1])
			return true;
	}
	return false;
}

static bool rect_op_empty(const struct region *region, int left, int top, int right, int bottom, enum region_op op)
{
	struct region rect = {0}, out = {0};
	region_set_rect(&rect, left, top, right, bottom);
	region_op(&out, &rect, region, op);
	bool empty = region_is_empty(&out);
	free(rect.bands);
	free(rect.xs);
	free(out.bands);
	free(out.xs);
	return empty;
}

bool region_contains_rect(const struct region *region, int left, int top, int right, int bottom)
{
	return right > left && bottom > top && rect_op_empty(region, left, top, right, bottom, REGION_DIFFERENCE);
}

bool region_intersects_rect(const struct region *region, int left, int top, int right, int bottom)
{
	return right > left && bottom > top && !rect_op_empty(region, left, top, right, bottom, REGION_INTERSECT);
}

bool region_equal(const struct region *a, const struct region *b)
{
	if (a->n_bands != b->n_bands || a->n_spans != b->n_spans)
		return false;
	if (!a->n_spans)
		return true;
	for (size_t i = 0; i < a->n_bands; i++)
		if (a->bands[i].top != b->bands[i].top || a->bands[i].bottom != b->bands[i].bottom || a->bands[i].count != b->bands[i].count)
			return false;
	return !memcmp(a->xs, b->xs, a->n_spans * 2 * sizeof(*a->xs));
}
