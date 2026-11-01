#ifndef SHARED_PATH_H
#define SHARED_PATH_H

#include <stdatomic.h>
#include <stdbool.h>
#include <stddef.h>

struct _graphene_matrix_t; // graphene_matrix_t
struct gfx_rect;

/* android.graphics.Path's geometry for every gfx backend: arcs, ovals and round rects are cubics
 * already, so all of them draw the same curves. Java edits a path only it holds; a display list
 * holds a ref, so a held path doesn't change. Refs drop on any thread. */

enum path_verb {
	PATH_MOVE,  // 1 point
	PATH_LINE,  // 1 point
	PATH_QUAD,  // 2 points
	PATH_CUBIC, // 3 points
	PATH_CLOSE, // none
};

struct gfx_path {
	atomic_int refs;
	unsigned char *verbs; // enum path_verb
	size_t n_verbs, verbs_size;
	float *points; // x, y
	size_t n_points, points_size;
	float move_x, move_y; // the contour's start, where a segment after a close begins
};

static inline size_t path_verb_points(enum path_verb verb)
{
	return verb == PATH_CUBIC ? 3 : verb == PATH_QUAD ? 2 : verb == PATH_CLOSE ? 0 : 1;
}

struct gfx_path *path_new(void);
struct gfx_path *path_ref(struct gfx_path *path);
void path_unref(struct gfx_path *path);
/* A path Java may edit: path itself when nothing else holds it, else a copy; drops path's ref. */
struct gfx_path *path_edit(struct gfx_path *path);
/* path_edit's, emptied */
struct gfx_path *path_reset(struct gfx_path *path);

void path_move_to(struct gfx_path *path, float x, float y);
void path_line_to(struct gfx_path *path, float x, float y);
void path_quad_to(struct gfx_path *path, float x1, float y1, float x2, float y2);
void path_cubic_to(struct gfx_path *path, float x1, float y1, float x2, float y2, float x3, float y3);
void path_close(struct gfx_path *path);
/* The last point, (0, 0) for an empty path; the r*To calls are relative to it. */
void path_last_point(const struct gfx_path *path, float *x, float *y);
void path_set_last_point(struct gfx_path *path, float x, float y);

/* Skia's: the oval's arc from start for sweep degrees (clockwise, y down), joined to the contour
 * by a line unless force_move_to or the path is empty. */
void path_arc_to(struct gfx_path *path, float left, float top, float right, float bottom, float start, float sweep, bool force_move_to);
void path_add_arc(struct gfx_path *path, float left, float top, float right, float bottom, float start, float sweep);
void path_add_rect(struct gfx_path *path, float left, float top, float right, float bottom, bool ccw);
void path_add_oval(struct gfx_path *path, float left, float top, float right, float bottom, bool ccw);
/* radii: x, y of the top left, top right, bottom right and bottom left corners */
void path_add_round_rect(struct gfx_path *path, float left, float top, float right, float bottom, const float radii[8], bool ccw);
/* Canvas.drawArc's shape: Android 10's hwui draws the oval for |sweep| >= 360, whatever the style
 * and use_center; else Skia's CreateDrawArcPath. */
void path_add_draw_arc(struct gfx_path *path, float left, float top, float right, float bottom, float start, float sweep, bool use_center);
/* matrix: NULL for identity */
void path_add_path(struct gfx_path *path, const struct gfx_path *src, const struct _graphene_matrix_t *matrix);
void path_transform(struct gfx_path *path, const struct _graphene_matrix_t *matrix);

/* Control-point bounds, as Android's computeBounds; all 0 when empty. */
void path_bounds(const struct gfx_path *path, struct gfx_rect *bounds);
bool path_contains(const struct gfx_path *path, bool even_odd, float x, float y);
/* One closed or open contour of 4 axis-aligned edges */
bool path_is_rect(const struct gfx_path *path, struct gfx_rect *rect);

#endif
