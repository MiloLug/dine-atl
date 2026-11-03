#include <cairo.h>
#include <math.h>
#include <stdbool.h>
#include <stdlib.h>

#include "src/shared/path.h"
#include "src/shared/pixels.h"
#include "src/shared/shader.h"

#include "bitmap.h"
#include "blend.h"
#include "record.h"

/* A finished list drawn with cairo into pixels, the way coregraphics/draw.c replays one. Approximate
 * where cairo differs: a sweep gradient is wedges, a hairline is 1 unit wide, a bitmap shader tiles
 * both axes as x (ATL-46). */

static cairo_matrix_t affine(const graphene_matrix_t *m)
{
	cairo_matrix_t matrix;
	cairo_matrix_init(&matrix, graphene_matrix_get_value(m, 0, 0), graphene_matrix_get_value(m, 0, 1),
	                  graphene_matrix_get_value(m, 1, 0), graphene_matrix_get_value(m, 1, 1),
	                  graphene_matrix_get_value(m, 3, 0), graphene_matrix_get_value(m, 3, 1));
	return matrix;
}

// PorterDuff.Mode's nativeInt, as blend.h numbers it
static cairo_operator_t operator_of(int mode)
{
	static const cairo_operator_t operators[] = {
	    CAIRO_OPERATOR_CLEAR, CAIRO_OPERATOR_SOURCE, CAIRO_OPERATOR_DEST, CAIRO_OPERATOR_OVER, CAIRO_OPERATOR_DEST_OVER,
	    CAIRO_OPERATOR_IN, CAIRO_OPERATOR_DEST_IN, CAIRO_OPERATOR_OUT, CAIRO_OPERATOR_DEST_OUT,
	    CAIRO_OPERATOR_ATOP, CAIRO_OPERATOR_DEST_ATOP, CAIRO_OPERATOR_XOR, CAIRO_OPERATOR_DARKEN, CAIRO_OPERATOR_LIGHTEN,
	    CAIRO_OPERATOR_MULTIPLY, CAIRO_OPERATOR_SCREEN, CAIRO_OPERATOR_ADD, CAIRO_OPERATOR_OVERLAY,
	};
	return mode >= 0 && mode < (int)(sizeof(operators) / sizeof(*operators)) ? operators[mode] : CAIRO_OPERATOR_OVER;
}

static void add_path(cairo_t *cr, const struct gfx_path *path)
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

static void add_stops(cairo_pattern_t *pattern, const struct gfx_shader *shader)
{
	for (size_t i = 0; i < shader->n; i++) {
		uint32_t c = shader->colors[i];
		float position = shader->positions ? shader->positions[i] : shader->n > 1 ? (float)i / (shader->n - 1) : 0;
		cairo_pattern_add_color_stop_rgba(pattern, position, ((c >> 16) & 0xff) / 255., ((c >> 8) & 0xff) / 255., (c & 0xff) / 255., (c >> 24) / 255.);
	}
}

// the color at t of the shader's stops
static void stop_color(const struct gfx_shader *shader, float t, double rgba[4])
{
	size_t i = 0;
	float p0 = 0, p1 = 0;
	while (i + 1 < shader->n) {
		p0 = shader->positions ? shader->positions[i] : (float)i / (shader->n - 1);
		p1 = shader->positions ? shader->positions[i + 1] : (float)(i + 1) / (shader->n - 1);
		if (t <= p1)
			break;
		i++;
	}
	uint32_t c0 = shader->colors[i], c1 = shader->colors[i + 1 < shader->n ? i + 1 : i];
	float f = p1 > p0 ? fminf(1, fmaxf(0, (t - p0) / (p1 - p0))) : 0;
	for (int k = 0; k < 4; k++) {
		int shift = k == 3 ? 24 : 16 - k * 8;
		rgba[k] = ((((c0 >> shift) & 0xff) * (1 - f)) + (((c1 >> shift) & 0xff) * f)) / 255.;
	}
}

// wedges of a mesh around the center, each from its start's color to its end's
static cairo_pattern_t *sweep_pattern(const struct gfx_shader *shader)
{
	enum { WEDGES = 72 };
	const double PI = 3.14159265358979323846;
	const double radius = 1e5;
	cairo_pattern_t *mesh = cairo_pattern_create_mesh();
	for (int i = 0; i < WEDGES; i++) {
		double a0 = 2 * PI * i / WEDGES, a1 = 2 * PI * (i + 1) / WEDGES;
		double c0[4], c1[4];
		stop_color(shader, (float)i / WEDGES, c0);
		stop_color(shader, (float)(i + 1) / WEDGES, c1);
		cairo_mesh_pattern_begin_patch(mesh);
		cairo_mesh_pattern_move_to(mesh, shader->x0, shader->y0);
		cairo_mesh_pattern_line_to(mesh, shader->x0 + radius * cos(a0), shader->y0 + radius * sin(a0));
		cairo_mesh_pattern_line_to(mesh, shader->x0 + radius * cos(a1), shader->y0 + radius * sin(a1));
		cairo_mesh_pattern_line_to(mesh, shader->x0, shader->y0);
		cairo_mesh_pattern_set_corner_color_rgba(mesh, 0, c0[0], c0[1], c0[2], c0[3]);
		cairo_mesh_pattern_set_corner_color_rgba(mesh, 1, c0[0], c0[1], c0[2], c0[3]);
		cairo_mesh_pattern_set_corner_color_rgba(mesh, 2, c1[0], c1[1], c1[2], c1[3]);
		cairo_mesh_pattern_set_corner_color_rgba(mesh, 3, c1[0], c1[1], c1[2], c1[3]);
		cairo_mesh_pattern_end_patch(mesh);
	}
	return mesh;
}

static cairo_pattern_t *shader_pattern(cairo_t *cr, const struct gfx_shader *shader);

// cairo extends both axes alike: tile_y is taken as tile x
static cairo_pattern_t *bitmap_pattern(const struct gfx_shader *shader)
{
	const struct gfx_bitmap *bitmap = shader->bitmap;
	cairo_surface_t *surface = cairo_image_surface_create_for_data((unsigned char *)bitmap->pixels, CAIRO_FORMAT_ARGB32, bitmap->width, bitmap->height, bitmap->width * 4);
	cairo_pattern_t *pattern = cairo_pattern_create_for_surface(surface);
	cairo_surface_destroy(surface);
	cairo_pattern_set_filter(pattern, CAIRO_FILTER_GOOD);
	return pattern;
}

// dst painted, then src over it in the mode, in the compose's space; an empty child paints nothing
static cairo_pattern_t *compose_pattern(cairo_t *cr, const struct gfx_shader *shader)
{
	cairo_push_group(cr);
	cairo_matrix_t local = affine(&shader->matrix);
	cairo_transform(cr, &local);
	const struct gfx_shader *children[] = {shader->dst, shader->src};
	for (int i = 0; i < 2; i++) {
		if (!children[i])
			continue;
		cairo_pattern_t *child = shader_pattern(cr, children[i]);
		cairo_set_source(cr, child);
		cairo_set_operator(cr, i ? operator_of(shader->mode) : CAIRO_OPERATOR_OVER);
		cairo_paint(cr);
		cairo_pattern_destroy(child);
	}
	return cairo_pop_group(cr);
}

// in the shader's own space: its local matrix maps that to the op's
static cairo_pattern_t *shader_pattern(cairo_t *cr, const struct gfx_shader *shader)
{
	if (shader->kind == SHADER_COMPOSE)
		return compose_pattern(cr, shader);
	cairo_pattern_t *pattern;
	if (shader->kind == SHADER_BITMAP)
		pattern = bitmap_pattern(shader);
	else if (shader->kind == SHADER_LINEAR)
		pattern = cairo_pattern_create_linear(shader->x0, shader->y0, shader->x1, shader->y1);
	else if (shader->kind == SHADER_RADIAL)
		pattern = cairo_pattern_create_radial(shader->x0, shader->y0, 0, shader->x0, shader->y0, shader->radius);
	else
		pattern = sweep_pattern(shader);
	static const cairo_extend_t extend[] = {[SHADER_CLAMP] = CAIRO_EXTEND_PAD, [SHADER_REPEAT] = CAIRO_EXTEND_REPEAT, [SHADER_MIRROR] = CAIRO_EXTEND_REFLECT};
	if (shader->kind != SHADER_SWEEP && shader->kind != SHADER_BITMAP)
		add_stops(pattern, shader);
	if (shader->kind != SHADER_SWEEP)
		cairo_pattern_set_extend(pattern, extend[shader->tile]);
	cairo_matrix_t local = affine(&shader->matrix);
	if (cairo_matrix_invert(&local) == CAIRO_STATUS_SUCCESS)
		cairo_pattern_set_matrix(pattern, &local);
	return pattern;
}

static void set_stroke(cairo_t *cr, const struct gfx_paint *paint)
{
	// Paint.Cap and Join share cairo's order; width 0 is a hairline
	cairo_set_line_width(cr, paint->stroke_width > 0 ? paint->stroke_width : 1);
	cairo_set_line_cap(cr, (cairo_line_cap_t)paint->stroke_cap);
	cairo_set_line_join(cr, (cairo_line_join_t)paint->stroke_join);
}

// the current path filled or stroked with the paint; the path is used up
static void paint_path(cairo_t *cr, const struct gfx_paint *paint, bool even_odd, bool stroke)
{
	cairo_set_fill_rule(cr, even_odd ? CAIRO_FILL_RULE_EVEN_ODD : CAIRO_FILL_RULE_WINDING);
	set_stroke(cr, paint);
	if (!paint->shader) {
		struct blend_color c = blend_paint_color(paint);
		cairo_set_source_rgba(cr, c.a ? c.r / c.a : 0, c.a ? c.g / c.a : 0, c.a ? c.b / c.a : 0, c.a);
		stroke ? cairo_stroke(cr) : cairo_fill(cr);
		return;
	}
	// the gradient opaque inside the shape, then the paint's alpha over it
	cairo_push_group(cr);
	cairo_pattern_t *pattern = shader_pattern(cr, paint->shader);
	cairo_set_source(cr, pattern);
	stroke ? cairo_stroke(cr) : cairo_fill(cr);
	cairo_pattern_destroy(pattern);
	cairo_pop_group_to_source(cr);
	cairo_paint_with_alpha(cr, (paint->color >> 24) / 255.);
}

static void draw_shape(cairo_t *cr, const struct op *op, void (*add)(cairo_t *cr, const struct op *op), bool even_odd)
{
	if (op->paint.style != GFX_PAINT_STROKE) {
		add(cr, op);
		paint_path(cr, &op->paint, even_odd, false);
	}
	if (op->paint.style != GFX_PAINT_FILL) {
		add(cr, op);
		paint_path(cr, &op->paint, false, true);
	}
}

static void add_rect(cairo_t *cr, const struct op *op)
{
	const float *a = op->args;
	cairo_rectangle(cr, a[0], a[1], a[2] - a[0], a[3] - a[1]);
}

static void add_round_rect(cairo_t *cr, const struct op *op)
{
	const float *a = op->args;
	struct gfx_path *path = path_new();
	float r[8] = {a[4], a[5], a[4], a[5], a[4], a[5], a[4], a[5]};
	path_add_round_rect(path, a[0], a[1], a[2], a[3], r, false);
	add_path(cr, path);
	path_unref(path);
}

static void add_op_path(cairo_t *cr, const struct op *op)
{
	add_path(cr, op->path);
}

static void add_arc(cairo_t *cr, const struct op *op)
{
	const float *a = op->args;
	struct gfx_path *path = path_new();
	path_add_draw_arc(path, a[0], a[1], a[2], a[3], a[4], a[5], a[6]);
	add_path(cr, path);
	path_unref(path);
}

// src a[0..3] of the bitmap into dst a[4..7], through the paint's color filter, at its alpha
static void draw_bitmap(cairo_t *cr, const struct op *op)
{
	const float *a = op->args;
	const struct gfx_bitmap *bitmap = op->bitmap;
	if (a[2] == a[0] || a[3] == a[1] || !bitmap->width || !bitmap->height)
		return;
	cairo_surface_t *surface = cairo_image_surface_create_for_data((unsigned char *)bitmap->pixels, CAIRO_FORMAT_ARGB32, bitmap->width, bitmap->height, bitmap->width * 4);
	cairo_rectangle(cr, a[4], a[5], a[6] - a[4], a[7] - a[5]);
	cairo_clip(cr);
	const struct gfx_color_filter *filter = &op->paint.filter;
	bool filtered = gfx_color_filter_is_set(filter);
	if (filtered)
		cairo_push_group(cr);
	cairo_save(cr);
	double xscale = (a[6] - a[4]) / (a[2] - a[0]), yscale = (a[7] - a[5]) / (a[3] - a[1]);
	cairo_translate(cr, a[4] - a[0] * xscale, a[5] - a[1] * yscale);
	cairo_scale(cr, xscale, yscale);
	cairo_set_source_surface(cr, surface, 0, 0);
	cairo_pattern_set_filter(cairo_get_source(cr), CAIRO_FILTER_GOOD);
	cairo_pattern_set_extend(cairo_get_source(cr), CAIRO_EXTEND_PAD);
	if (filtered)
		cairo_paint(cr);
	else
		cairo_paint_with_alpha(cr, (op->paint.color >> 24) / 255.);
	cairo_restore(cr);
	if (filtered && filter->has_matrix) {
		cairo_pattern_t *group = cairo_pop_group(cr);
		cairo_surface_t *drawn;
		if (cairo_pattern_get_surface(group, &drawn) == CAIRO_STATUS_SUCCESS && cairo_surface_get_type(drawn) == CAIRO_SURFACE_TYPE_IMAGE) {
			cairo_surface_flush(drawn);
			uint8_t *data = cairo_image_surface_get_data(drawn);
			int stride = cairo_image_surface_get_stride(drawn);
			for (int y = 0; y < cairo_image_surface_get_height(drawn); y++) {
				uint32_t *row = (uint32_t *)(data + (size_t)y * stride);
				for (int x = 0; x < cairo_image_surface_get_width(drawn); x++)
					row[x] = argb_premultiply(blend_color_matrix(filter->m, argb_unpremultiply(row[x])));
			}
			cairo_surface_mark_dirty(drawn);
		}
		cairo_set_source(cr, group);
		cairo_paint_with_alpha(cr, (op->paint.color >> 24) / 255.);
		cairo_pattern_destroy(group);
	} else if (filtered) {
		struct blend_color c = blend_unpack(filter->color);
		if (filter->mode != 2) { // DST keeps the bitmap
			cairo_set_source_rgba(cr, c.a ? c.r / c.a : 0, c.a ? c.g / c.a : 0, c.a ? c.b / c.a : 0, c.a);
			cairo_set_operator(cr, operator_of(filter->mode));
			cairo_paint(cr);
			cairo_set_operator(cr, CAIRO_OPERATOR_OVER);
		}
		cairo_pop_group_to_source(cr);
		cairo_paint_with_alpha(cr, (op->paint.color >> 24) / 255.);
	}
	cairo_surface_destroy(surface);
}

static void draw_op(cairo_t *cr, const struct op *op)
{
	const float *a = op->args;
	switch (op->kind) {
	case OP_RECT: draw_shape(cr, op, add_rect, false); break;
	case OP_ROUND_RECT: draw_shape(cr, op, add_round_rect, false); break;
	case OP_PATH: draw_shape(cr, op, add_op_path, a[0]); break;
	case OP_ARC: draw_shape(cr, op, add_arc, false); break;
	case OP_LINE: // stroked whatever the style
		cairo_move_to(cr, a[0], a[1]);
		cairo_line_to(cr, a[2], a[3]);
		paint_path(cr, &op->paint, false, true);
		break;
	case OP_BITMAP: draw_bitmap(cr, op); break;
	default: break; // OP_TEXT: no font; the clips and layers are raster's
	}
}

// the rect or path under the op's matrix; DIFFERENCE keeps what's outside it
static void clip(cairo_t *cr, const struct op *op)
{
	const float *a = op->args;
	bool difference = op->kind == OP_CLIP_PATH ? a[1] == GFX_CLIP_DIFFERENCE : a[4] == GFX_CLIP_DIFFERENCE;
	bool even_odd = op->kind == OP_CLIP_PATH && a[0];
	cairo_new_path(cr);
	if (difference) { // even-odd against a box past the pixels: exact for a shape that doesn't overlap itself
		cairo_identity_matrix(cr);
		cairo_rectangle(cr, -1e6, -1e6, 2e6, 2e6);
		even_odd = true;
	}
	cairo_matrix_t matrix = affine(&op->matrix);
	cairo_set_matrix(cr, &matrix);
	if (op->kind == OP_CLIP_PATH)
		add_path(cr, op->path);
	else
		cairo_rectangle(cr, a[0], a[1], a[2] - a[0], a[3] - a[1]);
	cairo_identity_matrix(cr);
	cairo_set_fill_rule(cr, even_odd ? CAIRO_FILL_RULE_EVEN_ODD : CAIRO_FILL_RULE_WINDING);
	cairo_clip(cr);
}

struct push {
	bool layer;
	float alpha;
	int mode;
};

static void begin_layer(cairo_t *cr, const struct op *op, struct push *push)
{
	const float *a = op->args;
	cairo_save(cr);
	if (a[4]) {
		cairo_matrix_t matrix = affine(&op->matrix);
		cairo_set_matrix(cr, &matrix);
		cairo_rectangle(cr, a[0], a[1], a[2] - a[0], a[3] - a[1]);
		cairo_identity_matrix(cr);
		cairo_clip(cr);
	}
	cairo_push_group(cr);
	*push = (struct push){true, a[5] / 255.f, (int)a[6]};
}

static void pop(cairo_t *cr, const struct push *push)
{
	if (push->layer) {
		cairo_pop_group_to_source(cr);
		if (push->mode != 2) { // DST keeps the destination
			cairo_set_operator(cr, push->mode < 0 ? CAIRO_OPERATOR_OVER : operator_of(push->mode));
			cairo_paint_with_alpha(cr, push->alpha);
			cairo_set_operator(cr, CAIRO_OPERATOR_OVER);
		}
	}
	cairo_restore(cr);
}

void record_raster(const struct gfx_canvas *list, uint32_t *pixels, int width, int height)
{
	cairo_surface_t *surface = cairo_image_surface_create_for_data((unsigned char *)pixels, CAIRO_FORMAT_ARGB32, width, height, width * 4);
	cairo_t *cr = cairo_create(surface);
	// what clips and layers pushed, ended last first by OP_UNCLIP, OP_UNLAYER or the end
	struct push *pushes = NULL;
	size_t n_pushes = 0, pushes_size = 0;
	for (size_t i = 0; i < list->n_ops; i++) {
		const struct op *op = &list->ops[i];
		if (op->kind == OP_CLIP || op->kind == OP_CLIP_PATH || op->kind == OP_LAYER) {
			if (n_pushes == pushes_size)
				pushes = realloc(pushes, (pushes_size = pushes_size ? pushes_size * 2 : 8) * sizeof(*pushes));
			if (op->kind == OP_LAYER) {
				begin_layer(cr, op, &pushes[n_pushes++]);
			} else {
				pushes[n_pushes++] = (struct push){false};
				cairo_save(cr);
				clip(cr, op);
			}
			continue;
		}
		if (op->kind == OP_UNCLIP || op->kind == OP_UNLAYER) {
			for (int n = op->kind == OP_UNLAYER ? 1 : op->args[0]; n > 0 && n_pushes; n--, n_pushes--)
				pop(cr, &pushes[n_pushes - 1]);
			continue;
		}
		cairo_save(cr);
		cairo_matrix_t matrix = affine(&op->matrix);
		cairo_set_matrix(cr, &matrix);
		cairo_new_path(cr);
		draw_op(cr, op);
		cairo_restore(cr);
	}
	while (n_pushes--)
		pop(cr, &pushes[n_pushes]);
	free(pushes);
	cairo_destroy(cr);
	cairo_surface_flush(surface);
	cairo_surface_destroy(surface);
}
