#include <cairo.h>
#include <math.h>
#include <stdbool.h>
#include <stdlib.h>

#include "src/backends/gfx/cairo/painter.h"
#include "src/shared/bitmap.h"
#include "src/shared/blend.h"
#include "src/shared/path.h"
#include "src/shared/shader.h"

#include "record.h"

/* A Canvas(Bitmap): record's ops drawn with cairo as they come (src/backends/gfx/cairo). Approximate
 * where cairo differs: a sweep gradient is wedges, a hairline is 1 unit wide, a bitmap shader tiles
 * both axes as x, text isn't drawn (ATL-46, ATL-48). */

struct painter {
	struct cpainter cairo;
};

static void paint_source(cairo_t *cr, const void *pattern)
{
	cairo_set_source(cr, (cairo_pattern_t *)pattern);
	cairo_paint(cr);
}

static void paint_white(cairo_t *cr, const void *unused)
{
	cairo_set_source_rgb(cr, 1, 1, 1);
	cairo_paint(cr);
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
	cairo_surface_t *surface = cpainter_bitmap_surface(shader->bitmap);
	cairo_pattern_t *pattern = cairo_pattern_create_for_surface(surface);
	cairo_surface_destroy(surface);
	cairo_pattern_set_filter(pattern, CAIRO_FILTER_GOOD);
	return pattern;
}

// dst painted, then src over it in the mode, in the compose's space; an empty child paints nothing
static cairo_pattern_t *compose_pattern(cairo_t *cr, const struct gfx_shader *shader)
{
	cairo_push_group(cr);
	cairo_matrix_t local = cpainter_affine(&shader->matrix);
	cairo_transform(cr, &local);
	if (shader->dst) {
		cairo_pattern_t *child = shader_pattern(cr, shader->dst);
		cairo_set_source(cr, child);
		cairo_paint(cr);
		cairo_pattern_destroy(child);
	}
	if (shader->src) {
		cairo_pattern_t *child = shader_pattern(cr, shader->src);
		cpainter_draw_in_mode(cr, shader->mode, paint_source, paint_white, child);
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
	cairo_matrix_t local = cpainter_affine(&shader->matrix);
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
		struct blend_color c = blend_filter_word(&paint->filter, paint->color);
		cairo_set_source_rgba(cr, c.a ? c.r / c.a : 0, c.a ? c.g / c.a : 0, c.a ? c.b / c.a : 0, c.a);
		stroke ? cairo_stroke(cr) : cairo_fill(cr);
		return;
	}
	// Skia's order: the shader's colors, the paint's alpha, the color filter, then the shape
	double x0, y0, x1, y1;
	stroke ? cairo_stroke_extents(cr, &x0, &y0, &x1, &y1) : cairo_fill_extents(cr, &x0, &y0, &x1, &y1);
	cairo_path_t *shape = cairo_copy_path(cr);
	cairo_new_path(cr);
	cairo_save(cr);
	cairo_rectangle(cr, floor(x0), floor(y0), ceil(x1) - floor(x0), ceil(y1) - floor(y0));
	cairo_clip(cr);
	cairo_push_group(cr);
	cairo_pattern_t *pattern = shader_pattern(cr, paint->shader);
	cairo_set_source(cr, pattern);
	cairo_paint_with_alpha(cr, (paint->color >> 24) / 255.);
	cairo_pattern_destroy(pattern);
	cairo_pattern_t *colors = cairo_pop_group(cr);
	cairo_surface_t *surface;
	if (gfx_color_filter_is_set(&paint->filter) && cairo_pattern_get_surface(colors, &surface) == CAIRO_STATUS_SUCCESS &&
	    cairo_surface_get_type(surface) == CAIRO_SURFACE_TYPE_IMAGE)
		cpainter_filter_surface(surface, &paint->filter);
	cairo_restore(cr);
	cairo_append_path(cr, shape);
	cairo_path_destroy(shape);
	cairo_set_source(cr, colors);
	stroke ? cairo_stroke(cr) : cairo_fill(cr);
	cairo_pattern_destroy(colors);
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
	cpainter_add_path(cr, path);
	path_unref(path);
}

static void add_op_path(cairo_t *cr, const struct op *op)
{
	cpainter_add_path(cr, op->path);
}

static void add_arc(cairo_t *cr, const struct op *op)
{
	const float *a = op->args;
	struct gfx_path *path = path_new();
	path_add_draw_arc(path, a[0], a[1], a[2], a[3], a[4], a[5], a[6]);
	cpainter_add_path(cr, path);
	path_unref(path);
}

static void draw_op_plain(cairo_t *cr, const void *data)
{
	const struct op *op = data;
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
	case OP_BITMAP: {
		cairo_pattern_t *shader = op->paint.shader && op->bitmap->config == GFX_BITMAP_ALPHA_8 ? shader_pattern(cr, op->paint.shader) : NULL;
		cpainter_draw_bitmap(cr, op->bitmap, &(struct gfx_rect){a[0], a[1], a[2], a[3]}, &(struct gfx_rect){a[4], a[5], a[6], a[7]},
		                     (op->paint.color >> 24) / 255.f, &op->paint.filter, op->paint.color, shader);
		if (shader)
			cairo_pattern_destroy(shader);
		break;
	}
	default: break; // OP_TEXT: no font
	}
}

// where the op draws: a bitmap's destination, else its shape in opaque white
static void cover_op(cairo_t *cr, const void *data)
{
	const struct op *op = data;
	if (op->kind == OP_BITMAP) {
		const float *a = op->args;
		cairo_rectangle(cr, a[4], a[5], a[6] - a[4], a[7] - a[5]);
		cairo_set_source_rgb(cr, 1, 1, 1);
		cairo_fill(cr);
		return;
	}
	struct op white = *op;
	white.paint.color = 0xffffffff;
	white.paint.shader = NULL;
	white.paint.filter = GFX_NO_COLOR_FILTER;
	draw_op_plain(cr, &white);
}

struct painter *painter_new(struct gfx_bitmap *bitmap)
{
	struct painter *painter = malloc(sizeof(*painter));
	cpainter_init(&painter->cairo, bitmap);
	return painter;
}

void painter_free(struct painter *painter)
{
	cpainter_finish(&painter->cairo);
	free(painter);
}

void painter_save(struct painter *painter)
{
	cpainter_save(&painter->cairo);
}

void painter_save_layer(struct painter *painter, const struct op *op)
{
	const float *a = op->args;
	cpainter_save_layer(&painter->cairo, &op->matrix, a[4] ? &(struct gfx_rect){a[0], a[1], a[2], a[3]} : NULL, a[5], (enum gfx_blend)a[6]);
}

void painter_restore(struct painter *painter)
{
	cpainter_restore(&painter->cairo);
}

void painter_clip(struct painter *painter, const struct op *op)
{
	const float *a = op->args;
	if (op->kind == OP_CLIP_PATH)
		cpainter_clip_path(&painter->cairo, &op->matrix, op->path, a[0], (enum gfx_clip_op)a[1]);
	else
		cpainter_clip_rect(&painter->cairo, &op->matrix, &(struct gfx_rect){a[0], a[1], a[2], a[3]}, (enum gfx_clip_op)a[4]);
}

void painter_draw(struct painter *painter, const struct op *op)
{
	struct op drawn = *op;
	struct gfx_bitmap *own = NULL;
	if (op->kind == OP_BITMAP && op->bitmap == painter->cairo.bitmap) // into itself: from a copy
		drawn.bitmap = own = bitmap_snapshot(op->bitmap);
	struct gfx_rect bounds;
	bool bounded = record_op_bounds(op, &bounds);
	cpainter_draw(&painter->cairo, &op->matrix, op->paint.blend, draw_op_plain, cover_op, &drawn, bounded ? &bounds : NULL);
	if (own)
		gfx_bitmap_unref(own);
}
