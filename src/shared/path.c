#include <graphene.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "src/slots/gfx.h"

#include "path.h"

struct gfx_path *path_new(void)
{
	struct gfx_path *path = calloc(1, sizeof(*path));
	path->refs = 1;
	return path;
}

struct gfx_path *path_ref(struct gfx_path *path)
{
	atomic_fetch_add(&path->refs, 1);
	return path;
}

void path_unref(struct gfx_path *path)
{
	if (!path || atomic_fetch_sub(&path->refs, 1) != 1)
		return;
	free(path->verbs);
	free(path->points);
	free(path);
}

// only the holder of the last ref could add one, so a count of 1 can't change under us
struct gfx_path *path_edit(struct gfx_path *path)
{
	if (atomic_load(&path->refs) == 1)
		return path;
	struct gfx_path *copy = path_new();
	path_add_path(copy, path, NULL);
	copy->move_x = path->move_x;
	copy->move_y = path->move_y;
	path_unref(path);
	return copy;
}

struct gfx_path *path_reset(struct gfx_path *path)
{
	if (atomic_load(&path->refs) != 1) {
		path_unref(path);
		return path_new();
	}
	path->n_verbs = path->n_points = 0;
	return path;
}

static void add(struct gfx_path *path, enum path_verb verb, const float *points)
{
	size_t n = path_verb_points(verb) * 2;
	if (path->n_verbs == path->verbs_size) {
		path->verbs_size = path->verbs_size ? path->verbs_size * 2 : 8;
		path->verbs = realloc(path->verbs, path->verbs_size);
	}
	path->verbs[path->n_verbs++] = verb;
	if (path->n_points + n > path->points_size) {
		path->points_size = path->points_size ? path->points_size * 2 + n : 16;
		path->points = realloc(path->points, path->points_size * sizeof(*path->points));
	}
	memcpy(path->points + path->n_points, points, n * sizeof(*points));
	path->n_points += n;
}

void path_last_point(const struct gfx_path *path, float *x, float *y)
{
	*x = path->n_points ? path->points[path->n_points - 2] : 0;
	*y = path->n_points ? path->points[path->n_points - 1] : 0;
}

void path_set_last_point(struct gfx_path *path, float x, float y)
{
	if (!path->n_points) {
		path_move_to(path, x, y);
		return;
	}
	path->points[path->n_points - 2] = x;
	path->points[path->n_points - 1] = y;
}

void path_move_to(struct gfx_path *path, float x, float y)
{
	add(path, PATH_MOVE, (float[]){x, y});
	path->move_x = x;
	path->move_y = y;
}

// Skia's: a segment starts a contour at (0, 0) in an empty path, at the closed one's start after a close
static void start_segment(struct gfx_path *path)
{
	if (!path->n_verbs)
		path_move_to(path, 0, 0);
	else if (path->verbs[path->n_verbs - 1] == PATH_CLOSE)
		path_move_to(path, path->move_x, path->move_y);
}

void path_line_to(struct gfx_path *path, float x, float y)
{
	start_segment(path);
	add(path, PATH_LINE, (float[]){x, y});
}

void path_quad_to(struct gfx_path *path, float x1, float y1, float x2, float y2)
{
	start_segment(path);
	add(path, PATH_QUAD, (float[]){x1, y1, x2, y2});
}

void path_cubic_to(struct gfx_path *path, float x1, float y1, float x2, float y2, float x3, float y3)
{
	start_segment(path);
	add(path, PATH_CUBIC, (float[]){x1, y1, x2, y2, x3, y3});
}

void path_close(struct gfx_path *path)
{
	if (path->n_verbs && path->verbs[path->n_verbs - 1] != PATH_CLOSE)
		add(path, PATH_CLOSE, NULL);
}

// cubics of at most 90 degrees each; the current point is the arc's start
static void arc_segments(struct gfx_path *path, float cx, float cy, float rx, float ry, double start, double sweep)
{
	int n = (int)ceil(fabs(sweep) / (M_PI / 2) - 1e-6);
	double delta = sweep / n, k = 4. / 3 * tan(delta / 4);
	for (int i = 0; i < n; i++) {
		double a = start + delta * i, b = a + delta;
		double ca = cos(a), sa = sin(a), cb = cos(b), sb = sin(b);
		path_cubic_to(path, cx + rx * (ca - k * sa), cy + ry * (sa + k * ca),
		              cx + rx * (cb + k * sb), cy + ry * (sb - k * cb),
		              cx + rx * cb, cy + ry * sb);
	}
}

void path_arc_to(struct gfx_path *path, float left, float top, float right, float bottom, float start, float sweep, bool force_move_to)
{
	if (right < left || bottom < top)
		return;
	sweep = fmaxf(-360, fminf(360, sweep));
	float cx = (left + right) / 2, cy = (top + bottom) / 2, rx = (right - left) / 2, ry = (bottom - top) / 2;
	double a = start * M_PI / 180;
	float x = cx + rx * cos(a), y = cy + ry * sin(a);
	if (force_move_to || !path->n_verbs) {
		path_move_to(path, x, y);
	} else {
		float last_x, last_y;
		path_last_point(path, &last_x, &last_y);
		if (last_x != x || last_y != y || path->verbs[path->n_verbs - 1] == PATH_CLOSE)
			path_line_to(path, x, y);
	}
	if (sweep && rx && ry)
		arc_segments(path, cx, cy, rx, ry, a, sweep * M_PI / 180);
}

void path_add_arc(struct gfx_path *path, float left, float top, float right, float bottom, float start, float sweep)
{
	path_arc_to(path, left, top, right, bottom, start, sweep, true);
	if (fabsf(sweep) >= 360)
		path_close(path);
}

void path_add_rect(struct gfx_path *path, float left, float top, float right, float bottom, bool ccw)
{
	path_move_to(path, left, top);
	if (ccw) {
		path_line_to(path, left, bottom);
		path_line_to(path, right, bottom);
		path_line_to(path, right, top);
	} else {
		path_line_to(path, right, top);
		path_line_to(path, right, bottom);
		path_line_to(path, left, bottom);
	}
	path_close(path);
}

void path_add_oval(struct gfx_path *path, float left, float top, float right, float bottom, bool ccw)
{
	path_arc_to(path, left, top, right, bottom, 0, ccw ? -360 : 360, true);
	path_close(path);
}

void path_add_round_rect(struct gfx_path *path, float left, float top, float right, float bottom, const float radii[8], bool ccw)
{
	float r[8];
	for (int i = 0; i < 8; i++)
		r[i] = fmaxf(radii[i], 0);
	// SkRRect's: radii that don't fit a side shrink, all by one factor
	float width = right - left, height = bottom - top, scale = 1;
	const float sums[4][2] = {{r[0] + r[2], width}, {r[4] + r[6], width}, {r[1] + r[7], height}, {r[3] + r[5], height}};
	for (int i = 0; i < 4; i++)
		if (sums[i][0] > sums[i][1])
			scale = fminf(scale, sums[i][1] / sums[i][0]);
	for (int i = 0; i < 8; i++)
		r[i] *= scale;
	// each corner's oval and the angle where a clockwise walk enters it
	const float corners[4][5] = {
		{right - 2 * r[2], top, right, top + 2 * r[3], -90},           // top right
		{right - 2 * r[4], bottom - 2 * r[5], right, bottom, 0},       // bottom right
		{left, bottom - 2 * r[7], left + 2 * r[6], bottom, 90},        // bottom left
		{left, top, left + 2 * r[0], top + 2 * r[1], 180},             // top left
	};
	path_move_to(path, left + r[0], top);
	for (int i = 0; i < 4; i++) {
		const float *c = corners[ccw ? 3 - i : i];
		path_arc_to(path, c[0], c[1], c[2], c[3], ccw ? c[4] + 90 : c[4], ccw ? -90 : 90, false);
	}
	path_close(path);
}

void path_add_draw_arc(struct gfx_path *path, float left, float top, float right, float bottom, float start, float sweep, bool use_center)
{
	if (!sweep || right <= left || bottom <= top) // SkCanvas::drawArc draws nothing
		return;
	if (fabsf(sweep) >= 360) {
		path_add_oval(path, left, top, right, bottom, false);
		return;
	}
	if (use_center)
		path_move_to(path, (left + right) / 2, (top + bottom) / 2);
	path_arc_to(path, left, top, right, bottom, start, sweep, !use_center);
	if (use_center)
		path_close(path);
}

// with the perspective divide, as Matrix.mapPoints
static void map_point(const graphene_matrix_t *matrix, float *x, float *y)
{
	graphene_vec4_t point;
	graphene_matrix_transform_vec4(matrix, graphene_vec4_init(&point, *x, *y, 0, 1), &point);
	float w = graphene_vec4_get_w(&point);
	*x = graphene_vec4_get_x(&point) / w;
	*y = graphene_vec4_get_y(&point) / w;
}

void path_add_path(struct gfx_path *path, const struct gfx_path *src, const graphene_matrix_t *matrix)
{
	if (src == path) { // its arrays grow while it's read
		struct gfx_path *copy = path_new();
		path_add_path(copy, src, NULL);
		path_add_path(path, copy, matrix);
		path_unref(copy);
		return;
	}
	const float *points = src->points;
	for (size_t i = 0; i < src->n_verbs; i++) {
		enum path_verb verb = src->verbs[i];
		float p[6];
		size_t n = path_verb_points(verb) * 2;
		memcpy(p, points, n * sizeof(*p));
		points += n;
		for (size_t j = 0; matrix && j < n; j += 2)
			map_point(matrix, &p[j], &p[j + 1]);
		if (verb == PATH_MOVE)
			path_move_to(path, p[0], p[1]);
		else if (verb == PATH_CLOSE)
			path_close(path);
		else
			add(path, verb, p);
	}
}

void path_transform(struct gfx_path *path, const graphene_matrix_t *matrix)
{
	for (size_t i = 0; i < path->n_points; i += 2)
		map_point(matrix, &path->points[i], &path->points[i + 1]);
	map_point(matrix, &path->move_x, &path->move_y);
}

void path_bounds(const struct gfx_path *path, struct gfx_rect *bounds)
{
	*bounds = (struct gfx_rect){0};
	for (size_t i = 0; i < path->n_points; i += 2) {
		float x = path->points[i], y = path->points[i + 1];
		if (!i) {
			*bounds = (struct gfx_rect){x, y, x, y};
			continue;
		}
		bounds->left = fminf(bounds->left, x);
		bounds->top = fminf(bounds->top, y);
		bounds->right = fmaxf(bounds->right, x);
		bounds->bottom = fmaxf(bounds->bottom, y);
	}
}

// curves flattened into this many lines each
#define FLATTEN 16

void path_flatten(const struct gfx_path *path, path_line_fn line, void *data)
{
	const float *p = path->points;
	float start_x = 0, start_y = 0, cur_x = 0, cur_y = 0;
	for (size_t i = 0; i < path->n_verbs; i++) {
		switch (path->verbs[i]) {
		case PATH_MOVE:
			line(data, cur_x, cur_y, start_x, start_y); // a fill closes each contour
			start_x = cur_x = p[0];
			start_y = cur_y = p[1];
			break;
		case PATH_LINE:
			line(data, cur_x, cur_y, p[0], p[1]);
			cur_x = p[0];
			cur_y = p[1];
			break;
		case PATH_QUAD:
		case PATH_CUBIC:
			for (int s = 1; s <= FLATTEN; s++) {
				float t = (float)s / FLATTEN, u = 1 - t, nx, ny;
				if (path->verbs[i] == PATH_QUAD) {
					nx = u * u * cur_x + 2 * u * t * p[0] + t * t * p[2];
					ny = u * u * cur_y + 2 * u * t * p[1] + t * t * p[3];
				} else {
					nx = u * u * u * cur_x + 3 * u * u * t * p[0] + 3 * u * t * t * p[2] + t * t * t * p[4];
					ny = u * u * u * cur_y + 3 * u * u * t * p[1] + 3 * u * t * t * p[3] + t * t * t * p[5];
				}
				line(data, cur_x, cur_y, nx, ny);
				cur_x = nx;
				cur_y = ny;
			}
			break;
		case PATH_CLOSE:
			line(data, cur_x, cur_y, start_x, start_y);
			cur_x = start_x;
			cur_y = start_y;
			break;
		}
		p += path_verb_points(path->verbs[i]) * 2;
	}
	line(data, cur_x, cur_y, start_x, start_y);
}

struct crossing {
	float x, y;
	int winding; // signed crossings of the ray to the right of (x, y)
};

static void edge(void *data, float x0, float y0, float x1, float y1)
{
	struct crossing *c = data;
	if ((y0 <= c->y) == (y1 <= c->y))
		return;
	float x = x0 + (c->y - y0) * (x1 - x0) / (y1 - y0);
	if (x > c->x)
		c->winding += y1 > y0 ? 1 : -1;
}

bool path_contains(const struct gfx_path *path, bool even_odd, float x, float y)
{
	struct crossing c = {x, y, 0};
	path_flatten(path, edge, &c);
	return even_odd ? c.winding & 1 : c.winding != 0;
}

bool path_is_rect(const struct gfx_path *path, struct gfx_rect *rect)
{
	size_t n = path->n_verbs;
	if (n && path->verbs[n - 1] == PATH_CLOSE)
		n--;
	if (n < 4 || n > 5 || path->verbs[0] != PATH_MOVE)
		return false;
	for (size_t i = 1; i < n; i++)
		if (path->verbs[i] != PATH_LINE)
			return false;
	const float *p = path->points;
	if (n == 5 && (p[8] != p[0] || p[9] != p[1]))
		return false;
	bool horizontal[4];
	for (int i = 0; i < 4; i++) {
		const float *a = p + i * 2, *b = p + (i + 1) % 4 * 2;
		horizontal[i] = a[1] == b[1] && a[0] != b[0];
		if (!horizontal[i] && !(a[0] == b[0] && a[1] != b[1]))
			return false;
		if (i && horizontal[i] == horizontal[i - 1])
			return false;
	}
	path_bounds(path, rect);
	return true;
}
