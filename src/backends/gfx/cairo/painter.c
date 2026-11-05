#include <math.h>
#include <stdlib.h>

#include "src/shared/bitmap.h"
#include "src/shared/blend.h"
#include "src/shared/path.h"

#include "painter.h"

cairo_matrix_t cpainter_affine(const graphene_matrix_t *m)
{
	cairo_matrix_t matrix;
	cairo_matrix_init(&matrix, graphene_matrix_get_value(m, 0, 0), graphene_matrix_get_value(m, 0, 1),
	                  graphene_matrix_get_value(m, 1, 0), graphene_matrix_get_value(m, 1, 1),
	                  graphene_matrix_get_value(m, 3, 0), graphene_matrix_get_value(m, 3, 1));
	return matrix;
}

static bool invertible(const cairo_matrix_t *matrix)
{
	cairo_matrix_t inverse = *matrix;
	return cairo_matrix_invert(&inverse) == CAIRO_STATUS_SUCCESS;
}

void cpainter_draw_in_mode(cairo_t *cr, enum gfx_blend mode, cpainter_draw_fn draw, cpainter_draw_fn cover, const void *data)
{
	cairo_push_group(cr);
	draw(cr, data);
	cairo_pattern_t *drawn = cairo_pop_group(cr);
	cairo_push_group(cr);
	cover(cr, data);
	cairo_pattern_t *covered = cairo_pop_group(cr);
	cairo_surface_t *target = cairo_get_group_target(cr), *s_surface, *c_surface;
	if (cairo_pattern_get_surface(drawn, &s_surface) == CAIRO_STATUS_SUCCESS && cairo_pattern_get_surface(covered, &c_surface) == CAIRO_STATUS_SUCCESS
	    && cairo_surface_get_type(target) == CAIRO_SURFACE_TYPE_IMAGE && cairo_surface_get_type(s_surface) == CAIRO_SURFACE_TYPE_IMAGE
	    && cairo_surface_get_type(c_surface) == CAIRO_SURFACE_TYPE_IMAGE) {
		cairo_surface_flush(target);
		cairo_surface_flush(s_surface);
		cairo_surface_flush(c_surface);
		double t_x, t_y, s_x, s_y, c_x, c_y; // device offsets: a pixel is at device position minus them
		cairo_surface_get_device_offset(target, &t_x, &t_y);
		cairo_surface_get_device_offset(s_surface, &s_x, &s_y);
		cairo_surface_get_device_offset(c_surface, &c_x, &c_y);
		int t_w = cairo_image_surface_get_width(target), t_h = cairo_image_surface_get_height(target);
		int c_w = cairo_image_surface_get_width(c_surface), c_h = cairo_image_surface_get_height(c_surface);
		uint8_t *t_data = cairo_image_surface_get_data(target), *s_data = cairo_image_surface_get_data(s_surface), *c_data = cairo_image_surface_get_data(c_surface);
		int t_stride = cairo_image_surface_get_stride(target), s_stride = cairo_image_surface_get_stride(s_surface), c_stride = cairo_image_surface_get_stride(c_surface);
		int s_w = cairo_image_surface_get_width(s_surface), s_h = cairo_image_surface_get_height(s_surface);
		for (int cy = 0; cy < c_h; cy++) {
			int ty = cy - (int)c_y + (int)t_y, sy = cy - (int)c_y + (int)s_y;
			if (ty < 0 || ty >= t_h)
				continue;
			uint32_t *t_row = (uint32_t *)(t_data + (size_t)ty * t_stride);
			const uint32_t *c_row = (const uint32_t *)(c_data + (size_t)cy * c_stride);
			const uint32_t *s_row = sy >= 0 && sy < s_h ? (const uint32_t *)(s_data + (size_t)sy * s_stride) : NULL;
			for (int cx = 0; cx < c_w; cx++) {
				int tx = cx - (int)c_x + (int)t_x, sx = cx - (int)c_x + (int)s_x;
				float m = (c_row[cx] >> 24) / 255.f;
				if (!m || tx < 0 || tx >= t_w)
					continue;
				struct blend_color drawn_color = s_row && sx >= 0 && sx < s_w ? blend_from_word(s_row[sx]) : (struct blend_color){0};
				struct blend_color src = {drawn_color.a / m, drawn_color.r / m, drawn_color.g / m, drawn_color.b / m};
				struct blend_color dst = blend_from_word(t_row[tx]), b = blend(mode, src, dst);
				t_row[tx] = blend_to_word(blend_mix(b, m, dst, 1 - m));
			}
		}
		cairo_surface_mark_dirty(target);
	}
	cairo_pattern_destroy(drawn);
	cairo_pattern_destroy(covered);
}

void cpainter_filter_surface(cairo_surface_t *surface, const struct gfx_color_filter *filter)
{
	cairo_surface_flush(surface);
	uint8_t *data = cairo_image_surface_get_data(surface);
	int stride = cairo_image_surface_get_stride(surface);
	for (int y = 0; y < cairo_image_surface_get_height(surface); y++) {
		uint32_t *row = (uint32_t *)(data + (size_t)y * stride);
		for (int x = 0; x < cairo_image_surface_get_width(surface); x++)
			row[x] = blend_filter_pixel(filter, row[x], 1);
	}
	cairo_surface_mark_dirty(surface);
}

void cpainter_add_path(cairo_t *cr, const struct gfx_path *path)
{
	const float *p = path->points;
	for (size_t i = 0; i < path->n_verbs; i++) {
		switch (path->verbs[i]) {
		case PATH_MOVE: cairo_move_to(cr, p[0], p[1]); break;
		case PATH_LINE: cairo_line_to(cr, p[0], p[1]); break;
		case PATH_QUAD: {
			double x0, y0;
			cairo_get_current_point(cr, &x0, &y0);
			cairo_curve_to(cr, x0 + 2. / 3 * (p[0] - x0), y0 + 2. / 3 * (p[1] - y0), p[2] + 2. / 3 * (p[0] - p[2]), p[3] + 2. / 3 * (p[1] - p[3]), p[2], p[3]);
			break;
		}
		case PATH_CUBIC: cairo_curve_to(cr, p[0], p[1], p[2], p[3], p[4], p[5]); break;
		case PATH_CLOSE: cairo_close_path(cr); break;
		}
		p += path_verb_points(path->verbs[i]) * 2;
	}
}

cairo_surface_t *cpainter_bitmap_surface(const struct gfx_bitmap *bitmap)
{
	return cairo_image_surface_create_for_data((unsigned char *)bitmap->words, CAIRO_FORMAT_ARGB32, bitmap->width, bitmap->height, bitmap->width * 4);
}

void cpainter_draw_bitmap(cairo_t *cr, const struct gfx_bitmap *bitmap, const struct gfx_rect *src, const struct gfx_rect *dst,
                          float alpha, const struct gfx_color_filter *filter, uint32_t color, cairo_pattern_t *shader)
{
	struct gfx_rect all = {0, 0, bitmap->width, bitmap->height};
	if (!src)
		src = &all;
	if (src->right == src->left || src->bottom == src->top || !bitmap->width || !bitmap->height)
		return;
	cairo_surface_t *surface = cpainter_bitmap_surface(bitmap);
	cairo_rectangle(cr, dst->left, dst->top, dst->right - dst->left, dst->bottom - dst->top);
	cairo_clip(cr);
	bool mask = bitmap->config == GFX_BITMAP_ALPHA_8;
	bool filtered = !mask && gfx_color_filter_is_set(filter);
	cairo_pattern_t *colors = NULL; // a mask's: the shader at alpha through the filter, before the shape
	if (mask && shader) {
		cairo_push_group(cr);
		cairo_set_source(cr, shader);
		cairo_paint_with_alpha(cr, alpha);
		colors = cairo_pop_group(cr);
		cairo_surface_t *drawn;
		if (gfx_color_filter_is_set(filter) && cairo_pattern_get_surface(colors, &drawn) == CAIRO_STATUS_SUCCESS && cairo_surface_get_type(drawn) == CAIRO_SURFACE_TYPE_IMAGE)
			cpainter_filter_surface(drawn, filter);
		cairo_set_source(cr, colors); // locked to this user space, before the bitmap's scale
	}
	if (filtered)
		cairo_push_group(cr);
	cairo_save(cr);
	double xscale = (dst->right - dst->left) / (src->right - src->left), yscale = (dst->bottom - dst->top) / (src->bottom - src->top);
	cairo_translate(cr, dst->left - src->left * xscale, dst->top - src->top * yscale);
	cairo_scale(cr, xscale, yscale);
	cairo_pattern_t *pattern = cairo_pattern_create_for_surface(surface);
	cairo_pattern_set_filter(pattern, CAIRO_FILTER_GOOD);
	cairo_pattern_set_extend(pattern, CAIRO_EXTEND_PAD);
	if (mask) { // the paint's colors where the bitmap's alpha says
		if (!colors) {
			struct blend_color c = blend_filter_word(filter, color);
			cairo_set_source_rgba(cr, c.a ? c.r / c.a : 0, c.a ? c.g / c.a : 0, c.a ? c.b / c.a : 0, c.a);
		}
		cairo_mask(cr, pattern);
	} else {
		cairo_set_source(cr, pattern);
		cairo_paint_with_alpha(cr, alpha);
	}
	cairo_pattern_destroy(pattern);
	cairo_restore(cr);
	if (filtered) {
		cairo_pattern_t *group = cairo_pop_group(cr);
		cairo_surface_t *drawn;
		if (cairo_pattern_get_surface(group, &drawn) == CAIRO_STATUS_SUCCESS && cairo_surface_get_type(drawn) == CAIRO_SURFACE_TYPE_IMAGE)
			cpainter_filter_surface(drawn, filter);
		cairo_set_source(cr, group);
		cairo_paint(cr);
		cairo_pattern_destroy(group);
	}
	if (colors)
		cairo_pattern_destroy(colors);
	cairo_surface_destroy(surface);
}

void cpainter_init(struct cpainter *painter, struct gfx_bitmap *bitmap)
{
	gfx_bitmap_ref(bitmap);
	*painter = (struct cpainter){.bitmap = bitmap, .surface = cpainter_bitmap_surface(bitmap)};
	painter->cr = cairo_create(painter->surface);
}

void cpainter_finish(struct cpainter *painter)
{
	while (painter->n_levels)
		cpainter_restore(painter);
	cairo_destroy(painter->cr);
	cairo_surface_destroy(painter->surface);
	free(painter->levels);
	gfx_bitmap_unref(painter->bitmap);
}

static void push_level(struct cpainter *painter, struct cpainter_level level)
{
	if (painter->n_levels == painter->levels_size) {
		painter->levels_size = painter->levels_size ? painter->levels_size * 2 : 4;
		painter->levels = realloc(painter->levels, painter->levels_size * sizeof(*painter->levels));
	}
	painter->levels[painter->n_levels++] = level;
}

// rounds what the last draw may have changed and counts the change; within a layer, its restore does
static void changed(struct cpainter *painter, double x0, double y0, double x1, double y1)
{
	if (painter->layers)
		return;
	const double margin = 2; // antialiasing
	bitmap_round(painter->bitmap, (int)floor(x0 - margin), (int)floor(y0 - margin), (int)ceil(x1 + margin), (int)ceil(y1 + margin));
}

static void clip_extents(cairo_t *cr, double *x0, double *y0, double *x1, double *y1)
{
	cairo_save(cr);
	cairo_identity_matrix(cr);
	cairo_clip_extents(cr, x0, y0, x1, y1);
	cairo_restore(cr);
}

void cpainter_save(struct cpainter *painter)
{
	cairo_save(painter->cr);
	push_level(painter, (struct cpainter_level){false});
}

void cpainter_save_layer(struct cpainter *painter, const graphene_matrix_t *matrix, const struct gfx_rect *bounds, uint8_t alpha, enum gfx_blend blend)
{
	cairo_t *cr = painter->cr;
	cairo_save(cr);
	if (bounds)
		cpainter_clip_rect(painter, matrix, bounds, GFX_CLIP_INTERSECT);
	cairo_push_group(cr);
	push_level(painter, (struct cpainter_level){true, alpha / 255.f, blend});
	painter->layers++;
}

struct layer {
	cairo_pattern_t *pattern;
	float alpha;
};

static void paint_layer(cairo_t *cr, const void *data)
{
	const struct layer *layer = data;
	cairo_set_source(cr, layer->pattern);
	cairo_paint_with_alpha(cr, layer->alpha);
}

static void paint_white(cairo_t *cr, const void *unused)
{
	cairo_set_source_rgb(cr, 1, 1, 1);
	cairo_paint(cr);
}

void cpainter_restore(struct cpainter *painter)
{
	if (!painter->n_levels)
		return;
	cairo_t *cr = painter->cr;
	struct cpainter_level level = painter->levels[--painter->n_levels];
	if (level.layer) {
		struct layer layer = {cairo_pop_group(cr), level.alpha};
		painter->layers--;
		if (level.mode == GFX_BLEND_SRC_OVER)
			paint_layer(cr, &layer);
		else if (level.mode != GFX_BLEND_DST)
			cpainter_draw_in_mode(cr, level.mode, paint_layer, paint_white, &layer);
		cairo_pattern_destroy(layer.pattern);
		double x0, y0, x1, y1;
		clip_extents(cr, &x0, &y0, &x1, &y1);
		changed(painter, x0, y0, x1, y1);
	}
	cairo_restore(cr);
}

// nothing: the clip a singular matrix leaves
static void clip_all(cairo_t *cr)
{
	cairo_new_path(cr);
	cairo_identity_matrix(cr);
	cairo_rectangle(cr, 0, 0, 0, 0);
	cairo_clip(cr);
}

/* the path added under matrix; DIFFERENCE keeps what's outside it, as even-odd against a box past
 * the pixels: exact for a shape that doesn't cross itself (ATL-53) */
static void clip(cairo_t *cr, const cairo_matrix_t *matrix, void (*add)(cairo_t *cr, const void *data), const void *data, bool even_odd, enum gfx_clip_op op)
{
	cairo_new_path(cr);
	if (op == GFX_CLIP_DIFFERENCE) {
		cairo_identity_matrix(cr);
		cairo_rectangle(cr, -1e6, -1e6, 2e6, 2e6);
		even_odd = true;
	}
	cairo_set_matrix(cr, matrix);
	add(cr, data);
	cairo_identity_matrix(cr);
	cairo_set_fill_rule(cr, even_odd ? CAIRO_FILL_RULE_EVEN_ODD : CAIRO_FILL_RULE_WINDING);
	cairo_clip(cr);
}

static void add_rect(cairo_t *cr, const void *data)
{
	const struct gfx_rect *r = data;
	cairo_rectangle(cr, r->left, r->top, r->right - r->left, r->bottom - r->top);
}

static void add_path(cairo_t *cr, const void *data)
{
	cpainter_add_path(cr, data);
}

void cpainter_clip_rect(struct cpainter *painter, const graphene_matrix_t *matrix, const struct gfx_rect *rect, enum gfx_clip_op op)
{
	cairo_matrix_t m = cpainter_affine(matrix);
	if (invertible(&m))
		clip(painter->cr, &m, add_rect, rect, false, op);
	else if (op != GFX_CLIP_DIFFERENCE) // what a singular matrix maps to nothing, DIFFERENCE keeps
		clip_all(painter->cr);
}

void cpainter_clip_path(struct cpainter *painter, const graphene_matrix_t *matrix, const struct gfx_path *path, bool even_odd, enum gfx_clip_op op)
{
	cairo_matrix_t m = cpainter_affine(matrix);
	if (invertible(&m))
		clip(painter->cr, &m, add_path, path, even_odd, op);
	else if (op != GFX_CLIP_DIFFERENCE)
		clip_all(painter->cr);
}

void cpainter_draw(struct cpainter *painter, const graphene_matrix_t *matrix, enum gfx_blend mode,
                   cpainter_draw_fn draw, cpainter_draw_fn cover, const void *data, const struct gfx_rect *bounds)
{
	cairo_matrix_t m = cpainter_affine(matrix);
	if (mode == GFX_BLEND_DST || !invertible(&m))
		return;
	cairo_t *cr = painter->cr;
	double x0, y0, x1, y1;
	clip_extents(cr, &x0, &y0, &x1, &y1);
	if (bounds) { // its device box, inside the clip's
		double xs[4] = {bounds->left, bounds->right, bounds->left, bounds->right}, ys[4] = {bounds->top, bounds->top, bounds->bottom, bounds->bottom};
		double bx0 = INFINITY, by0 = INFINITY, bx1 = -INFINITY, by1 = -INFINITY;
		for (int i = 0; i < 4; i++) {
			cairo_matrix_transform_point(&m, &xs[i], &ys[i]);
			bx0 = fmin(bx0, xs[i]), by0 = fmin(by0, ys[i]), bx1 = fmax(bx1, xs[i]), by1 = fmax(by1, ys[i]);
		}
		x0 = fmax(x0, bx0 - 2), y0 = fmax(y0, by0 - 2), x1 = fmin(x1, bx1 + 2), y1 = fmin(y1, by1 + 2); // antialiasing
		if (x1 <= x0 || y1 <= y0)
			return; // outside the clip
	}
	cairo_save(cr);
	if (bounds && mode != GFX_BLEND_SRC_OVER) { // the mode's groups only as large as the draw
		cairo_rectangle(cr, floor(x0), floor(y0), ceil(x1) - floor(x0), ceil(y1) - floor(y0));
		cairo_clip(cr);
	}
	cairo_set_matrix(cr, &m);
	cairo_new_path(cr);
	if (mode == GFX_BLEND_SRC_OVER)
		draw(cr, data);
	else
		cpainter_draw_in_mode(cr, mode, draw, cover, data);
	cairo_restore(cr);
	if (x1 > x0 && y1 > y0)
		changed(painter, x0, y0, x1, y1);
	else if (!painter->layers)
		bitmap_changed(painter->bitmap);
}
