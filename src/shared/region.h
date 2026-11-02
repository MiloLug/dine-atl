#ifndef SHARED_REGION_H
#define SHARED_REGION_H

#include <stdbool.h>
#include <stddef.h>

struct gfx_path;

/* android.graphics.Region's integer area for every build: y-bands of x-spans, each [left, right).
 * Kept canonical (bands sorted and non-empty, spans sorted and apart, equal touching bands merged),
 * so equal areas are equal structures and a rect count means what SkRegion's does. */

struct region_rect {
	int left, top, right, bottom;
};

/* must match android.graphics.Region.Op */
enum region_op {
	REGION_DIFFERENCE,
	REGION_INTERSECT,
	REGION_UNION,
	REGION_XOR,
	REGION_REVERSE_DIFFERENCE,
	REGION_REPLACE,
};

struct region *region_new(void);
void region_free(struct region *region);
void region_set(struct region *dst, const struct region *src);
/* empty when the rect is */
void region_set_rect(struct region *region, int left, int top, int right, int bottom);
/* dst = a op b; dst may be a or b */
void region_op(struct region *dst, const struct region *a, const struct region *b, enum region_op op);
/* The pixels whose centres the path's fill covers, inside clip's bounds and then clip (NULL: the
 * path's bounds; an inverse fill with no clip is empty). */
void region_set_path(struct region *dst, const struct gfx_path *path, const struct region *clip, bool even_odd, bool inverse);
void region_translate(struct region *region, int dx, int dy);
/* each rect's edges times scale, rounded */
void region_scale(struct region *dst, const struct region *src, float scale);

bool region_is_empty(const struct region *region);
size_t region_rect_count(const struct region *region);
/* rects top to bottom, left to right */
struct region_rect region_rect_at(const struct region *region, size_t i);
/* all 0 when empty */
struct region_rect region_bounds(const struct region *region);
bool region_contains(const struct region *region, int x, int y);
bool region_contains_rect(const struct region *region, int left, int top, int right, int bottom);
bool region_intersects_rect(const struct region *region, int left, int top, int right, int bottom);
bool region_equal(const struct region *a, const struct region *b);

#endif
