#include <math.h>
#include <string.h>

#include <gtk/gtk.h>

#include "src/api-impl-jni/graphics/AndroidPaint.h"
#include "src/api-impl-jni/graphics/java_paintable.h"
#include "src/shared/blend.h"
#include "src/shared/matrix.h"
#include "src/shared/path.h"
#include "src/shared/shader.h"
#include "src/slots/gfx.h"

#include "bitmap.h"
#include "canvas.h"
#include "text.h"

/* A canvas is a GtkSnapshot: the widget's snapshot during a draw, a Bitmap's or a RenderNode's. */

/* An open composite of what is drawn next onto what's below (composite_begin): the pops that close
 * it, after the tail an arithmetic mode appends there first. */
struct composite {
	guint pops;
	GskRenderNode *tail; // a template (composite_end), not held
	graphene_rect_t bounds;
};

// a save level: the base one first
struct level {
	guint pushes; // the pushes its restore pops first: clips, and a layer's isolation and opacity
	float m[4];   // the linear part of the canvas's transform: x' = m[0] x + m[1] y, y' = m[2] x + m[3] y
	struct composite layer; // a layer's blend, closed next
	guint outer;            // then a layer's bounds clip
};

static GArray *levels_of(GtkSnapshot *snapshot)
{
	GArray *levels = g_object_get_data(G_OBJECT(snapshot), "atl-levels");
	if (!levels) {
		levels = g_array_new(FALSE, TRUE, sizeof(struct level));
		g_array_append_val(levels, ((struct level){.m = {1, 0, 0, 1}}));
		g_object_set_data_full(G_OBJECT(snapshot), "atl-levels", levels, (GDestroyNotify)g_array_unref);
	}
	return levels;
}

static struct level *top_level(GtkSnapshot *snapshot)
{
	GArray *levels = levels_of(snapshot);
	return &g_array_index(levels, struct level, levels->len - 1);
}

// a push the restore of the current save level pops
static void gsk_canvas_pushed(GtkSnapshot *snapshot)
{
	top_level(snapshot)->pushes++;
}

static void transformed(GtkSnapshot *snapshot, float a, float b, float c, float d)
{
	float *m = top_level(snapshot)->m;
	float n[4] = {m[0] * a + m[1] * c, m[0] * b + m[1] * d, m[2] * a + m[3] * c, m[2] * b + m[3] * d};
	memcpy(m, n, sizeof(n));
}

/* Two device pixels in user units at the canvas's smallest scale, the view's own transform
 * outside it taken as 1 or more. */
static float pixels_margin(GtkSnapshot *snapshot)
{
	const float *m = top_level(snapshot)->m;
	float t = m[0] * m[0] + m[1] * m[1] + m[2] * m[2] + m[3] * m[3], det = m[0] * m[3] - m[1] * m[2];
	float least = sqrtf(fmaxf(0, (t - sqrtf(fmaxf(0, t * t - 4 * det * det))) / 2)); // the smaller singular value
	return 2 / fmaxf(fminf(least, 1), 0.01f);
}

static const GdkRGBA WHITE = {1, 1, 1, 1};

/* GSK nodes need finite bounds: what a layer with no bounds or a fill of the clip covers, larger than
 * any canvas. */
static const graphene_rect_t EVERYWHERE = {{-100000, -100000}, {200000, 200000}};

/* Blend modes composite onto what the snapshot has below in its innermost layer: a Bitmap's pixels,
 * or a Java-drawn view's own drawing and its children's (WrapperWidget isolates it, a layer by
 * default). */

static bool porter_duff(enum gfx_blend mode, GskPorterDuff *op)
{
	switch (mode) {
	case GFX_BLEND_CLEAR: *op = GSK_PORTER_DUFF_CLEAR; return true;
	case GFX_BLEND_SRC: *op = GSK_PORTER_DUFF_SOURCE; return true;
	case GFX_BLEND_DST_OVER: *op = GSK_PORTER_DUFF_DEST_OVER_SOURCE; return true;
	case GFX_BLEND_SRC_IN: *op = GSK_PORTER_DUFF_SOURCE_IN_DEST; return true;
	case GFX_BLEND_DST_IN: *op = GSK_PORTER_DUFF_DEST_IN_SOURCE; return true;
	case GFX_BLEND_SRC_OUT: *op = GSK_PORTER_DUFF_SOURCE_OUT_DEST; return true;
	case GFX_BLEND_DST_OUT: *op = GSK_PORTER_DUFF_DEST_OUT_SOURCE; return true;
	case GFX_BLEND_SRC_ATOP: *op = GSK_PORTER_DUFF_SOURCE_ATOP_DEST; return true;
	case GFX_BLEND_DST_ATOP: *op = GSK_PORTER_DUFF_DEST_ATOP_SOURCE; return true;
	case GFX_BLEND_XOR: *op = GSK_PORTER_DUFF_XOR; return true;
	default: return false;
	}
}

static GskBlendMode gsk_blend(enum gfx_blend mode)
{
	switch (mode) {
	case GFX_BLEND_SCREEN: return GSK_BLEND_MODE_SCREEN;
	case GFX_BLEND_OVERLAY: return GSK_BLEND_MODE_OVERLAY;
	case GFX_BLEND_DARKEN: return GSK_BLEND_MODE_DARKEN;
	case GFX_BLEND_LIGHTEN: return GSK_BLEND_MODE_LIGHTEN;
	case GFX_BLEND_COLOR_DODGE: return GSK_BLEND_MODE_COLOR_DODGE;
	case GFX_BLEND_COLOR_BURN: return GSK_BLEND_MODE_COLOR_BURN;
	case GFX_BLEND_HARD_LIGHT: return GSK_BLEND_MODE_HARD_LIGHT;
	case GFX_BLEND_SOFT_LIGHT: return GSK_BLEND_MODE_SOFT_LIGHT;
	case GFX_BLEND_DIFFERENCE: return GSK_BLEND_MODE_DIFFERENCE;
	case GFX_BLEND_EXCLUSION: return GSK_BLEND_MODE_EXCLUSION;
	case GFX_BLEND_HUE: return GSK_BLEND_MODE_HUE;
	case GFX_BLEND_SATURATION: return GSK_BLEND_MODE_SATURATION;
	case GFX_BLEND_COLOR: return GSK_BLEND_MODE_COLOR;
	case GFX_BLEND_LUMINOSITY: return GSK_BLEND_MODE_LUMINOSITY;
	default: return GSK_BLEND_MODE_MULTIPLY;
	}
}

static void draw_part(GtkSnapshot *snapshot, const struct gsk_draw *draw, enum gsk_part part)
{
	draw->fn(snapshot, draw, part);
}

/* PLUS and MODULATE as GSK's arithmetic node, k1 i1 i2 + k2 i1 + k3 i2 + k4 over premultiplied
 * colors, clamped: i1 the backdrop's copy, i2 the source's. GTK has no constructor for it, only its
 * node parser. */
static GskRenderNode *parse_arithmetic(const char *k)
{
	char *text = g_strdup_printf("arithmetic { bounds: -100000 -100000 200000 200000; k: %s;"
	                             " first: paste { bounds: -100000 -100000 200000 200000; depth: 1; }"
	                             " second: paste { bounds: -100000 -100000 200000 200000; depth: 0; } }", k);
	GBytes *bytes = g_bytes_new_take(text, strlen(text));
	GskRenderNode *node = gsk_render_node_deserialize(bytes, NULL, NULL);
	g_bytes_unref(bytes);
	if (!node || gsk_render_node_get_node_type(node) != GSK_ARITHMETIC_NODE)
		g_error("GSK can't parse an arithmetic node");
	return node;
}

static GskRenderNode *arithmetic(enum gfx_blend mode)
{
	static GskRenderNode *plus, *modulate;
	if (g_once_init_enter_pointer(&plus)) {
		modulate = parse_arithmetic("1 0 0 0");
		g_once_init_leave_pointer(&plus, parse_arithmetic("0 1 1 0"));
	}
	return mode == GFX_BLEND_PLUS ? plus : mode == GFX_BLEND_MODULATE ? modulate : NULL;
}

/* Opens mode's composite, onto what's below, of what is drawn next; coverage draws its coverage
 * (NULL: all of bounds). *source: draw the source, not the content. composite_end closes it. */
static struct composite composite_begin(GtkSnapshot *snapshot, enum gfx_blend mode, const graphene_rect_t *bounds, const struct gsk_draw *coverage, bool *source)
{
	GskPorterDuff op;
	*source = false;
	if (mode == GFX_BLEND_SRC_OVER)
		return (struct composite){0};
	if (mode == GFX_BLEND_DST) {
		gtk_snapshot_push_opacity(snapshot, 0);
		return (struct composite){1};
	}
	GskRenderNode *tail = arithmetic(mode);
	if (porter_duff(mode, &op) || tail) { // Skia's lerp(below, op(source, below), coverage)
		if (tail)
			gtk_snapshot_push_copy(snapshot);
		gtk_snapshot_push_composite(snapshot, tail ? GSK_PORTER_DUFF_SOURCE : op);
		if (coverage)
			draw_part(snapshot, coverage, GSK_COVERAGE);
		else
			gtk_snapshot_append_color(snapshot, &WHITE, bounds);
		gtk_snapshot_pop(snapshot);
		*source = coverage != NULL;
		if (!tail)
			return (struct composite){1};
		// the source alone, then the arithmetic of the copies of the backdrop and of it in its place
		graphene_rect_t wide;
		float margin = pixels_margin(snapshot);
		graphene_rect_inset_r(bounds, -margin, -margin, &wide);
		gtk_snapshot_push_clip(snapshot, &wide);
		gtk_snapshot_push_isolation(snapshot, GSK_ISOLATION_BACKGROUND);
		return (struct composite){4, tail, wide};
	}
	// a blend is linear in the source's coverage, and a blend of nothing is what's below
	gtk_snapshot_push_copy(snapshot);
	gtk_snapshot_push_composite(snapshot, GSK_PORTER_DUFF_SOURCE);
	gtk_snapshot_append_color(snapshot, &WHITE, bounds);
	gtk_snapshot_pop(snapshot);
	gtk_snapshot_push_blend(snapshot, gsk_blend(mode));
	gtk_snapshot_append_paste(snapshot, bounds, 0);
	gtk_snapshot_pop(snapshot);
	return (struct composite){3};
}

static void composite_end(GtkSnapshot *snapshot, const struct composite *composite)
{
	if (composite->tail) {
		gtk_snapshot_push_copy(snapshot);
		gtk_snapshot_push_composite(snapshot, GSK_PORTER_DUFF_SOURCE);
		gtk_snapshot_append_color(snapshot, &WHITE, &composite->bounds);
		gtk_snapshot_pop(snapshot);
		gtk_snapshot_append_node(snapshot, composite->tail);
		gtk_snapshot_pop(snapshot);
		gtk_snapshot_pop(snapshot);
	}
	for (guint n = composite->pops; n; n--)
		gtk_snapshot_pop(snapshot);
}

/* draw in mode onto what's below; solid: the one color it draws, or NULL */
static void gsk_composite(GtkSnapshot *snapshot, enum gfx_blend mode, const struct gsk_draw *draw, const GdkRGBA *solid)
{
	struct gsk_painter *painter = gsk_painter_of(snapshot);
	if (painter) {
		gsk_painter_composite(painter, mode, draw);
		return;
	}
	if (mode == GFX_BLEND_MODULATE && solid) { // what's below scaled by the color, where it draws
		graphene_matrix_t scale;
		graphene_matrix_init_from_float(&scale, (float[16]){solid->red, 0, 0, 0, 0, solid->green, 0, 0, 0, 0, solid->blue, 0, 0, 0, 0, solid->alpha});
		gtk_snapshot_push_copy(snapshot);
		gtk_snapshot_push_composite(snapshot, GSK_PORTER_DUFF_SOURCE);
		draw_part(snapshot, draw, GSK_COVERAGE);
		gtk_snapshot_pop(snapshot);
		gtk_snapshot_push_color_matrix(snapshot, &scale, graphene_vec4_zero());
		gtk_snapshot_append_paste(snapshot, draw->bounds, 0);
		gtk_snapshot_pop(snapshot);
		gtk_snapshot_pop(snapshot);
		gtk_snapshot_pop(snapshot);
		return;
	}
	bool source;
	struct composite composite = composite_begin(snapshot, mode, draw->bounds, draw, &source);
	if (source) { // the colors reach past every pixel the shape touches: the mask alone gives the coverage
		graphene_rect_t wide;
		float margin = pixels_margin(snapshot);
		graphene_rect_inset_r(draw->bounds, -margin, -margin, &wide);
		draw_part(snapshot, &(struct gsk_draw){draw->fn, draw->data, draw->paint, &wide}, GSK_SOURCE);
	} else {
		draw_part(snapshot, draw, GSK_CONTENT);
	}
	composite_end(snapshot, &composite);
}

struct filter_fill {
	const struct gsk_draw *filtered;
	enum gsk_part part; // of filtered: its content, or its source, which covers all of its bounds
};

// a filter's color where the draw it filters draws
static void draw_filter_fill(GtkSnapshot *snapshot, const struct gsk_draw *draw, enum gsk_part part)
{
	const struct filter_fill *fill = draw->data;
	bool shaped = fill->part == GSK_CONTENT;
	if (part == GSK_COVERAGE) {
		if (shaped)
			draw_part(snapshot, fill->filtered, GSK_COVERAGE);
		else
			gtk_snapshot_append_color(snapshot, &WHITE, draw->bounds);
		return;
	}
	uint32_t c = draw->paint->filter.color;
	GdkRGBA color = {((c >> 16) & 0xff) / 255.f, ((c >> 8) & 0xff) / 255.f, (c & 0xff) / 255.f, (c >> 24) / 255.f};
	if (part == GSK_CONTENT && shaped) {
		gtk_snapshot_push_mask(snapshot, GSK_MASK_MODE_ALPHA);
		draw_part(snapshot, fill->filtered, GSK_COVERAGE);
		gtk_snapshot_pop(snapshot);
	}
	gtk_snapshot_append_color(snapshot, &color, draw->bounds);
	if (part == GSK_CONTENT && shaped)
		gtk_snapshot_pop(snapshot);
}

void gsk_filtered(GtkSnapshot *snapshot, const struct AndroidPaint *filter, const struct gsk_draw *draw, enum gsk_part part)
{
	if (filter->use_color_filter) {
		gtk_snapshot_push_color_matrix(snapshot, &filter->color_matrix, &filter->color_offset);
		draw_part(snapshot, draw, part);
		gtk_snapshot_pop(snapshot);
	} else if (!gfx_color_filter_is_set(&filter->filter) || filter->filter.mode == GFX_BLEND_DST) {
		draw_part(snapshot, draw, part);
	} else { // the filter's color composited onto the draw alone
		gtk_snapshot_push_isolation(snapshot, GSK_ISOLATION_BACKGROUND);
		draw_part(snapshot, draw, part);
		gsk_composite(snapshot, filter->filter.mode, &(struct gsk_draw){draw_filter_fill, &(struct filter_fill){draw, part}, filter, draw->bounds}, NULL);
		gtk_snapshot_pop(snapshot);
	}
}

static void append_shader(GtkSnapshot *snapshot, const graphene_rect_t *shape, const struct gfx_shader *shader, float alpha);

static void draw_shader_colors(GtkSnapshot *snapshot, const struct gsk_draw *draw, enum gsk_part part)
{
	if (part == GSK_COVERAGE)
		gtk_snapshot_append_color(snapshot, &WHITE, draw->bounds);
	else
		append_shader(snapshot, draw->bounds, draw->paint->shader, draw->paint->color.alpha);
}

/* The paint's colors over bounds: its one color (filtered on the CPU beforehand), or its shader's at
 * its alpha through its color filter (Skia's order). */
static void append_source(GtkSnapshot *snapshot, const struct AndroidPaint *paint, const graphene_rect_t *bounds)
{
	if (paint->shader)
		gsk_filtered(snapshot, paint, &(struct gsk_draw){draw_shader_colors, NULL, paint, bounds}, GSK_CONTENT);
	else
		gtk_snapshot_append_color(snapshot, &paint->color, bounds);
}

// a paint's draw of a shape or text; bounds hold it
static void paint_draw(GtkSnapshot *snapshot, const struct AndroidPaint *paint, const graphene_rect_t *bounds, gsk_draw_fn fn, const void *data)
{
	struct AndroidPaint solid;
	if (!paint->shader && gfx_color_filter_is_set(&paint->filter)) {
		uint32_t argb = (uint32_t)lroundf(paint->color.alpha * 255) << 24 | (uint32_t)lroundf(paint->color.red * 255) << 16 |
		                (uint32_t)lroundf(paint->color.green * 255) << 8 | (uint32_t)lroundf(paint->color.blue * 255);
		struct blend_color c = blend_filter_word(&paint->filter, argb);
		solid = *paint;
		solid.color = c.a > 0 ? (GdkRGBA){c.r / c.a, c.g / c.a, c.b / c.a, c.a} : (GdkRGBA){0};
		paint = &solid;
	}
	gsk_composite(snapshot, paint->blend, &(struct gsk_draw){fn, data, paint, bounds}, paint->shader ? NULL : &paint->color);
}

/* A 2D matrix as 2D transforms: GSK's cairo renderer, which draws off the main thread, draws a
 * transform made from a whole matrix (gtk_snapshot_transform_matrix) as hot pink. */
static void transform_affine(GtkSnapshot *snapshot, const graphene_matrix_t *m)
{
	if (!graphene_matrix_is_2d(m)) {
		gtk_snapshot_transform_matrix(snapshot, m);
		return;
	}
	float a = graphene_matrix_get_value(m, 0, 0), b = graphene_matrix_get_value(m, 0, 1);
	float c = graphene_matrix_get_value(m, 1, 0), d = graphene_matrix_get_value(m, 1, 1);
	float tx = graphene_matrix_get_value(m, 3, 0), ty = graphene_matrix_get_value(m, 3, 1);
	// translate, rotate, skew x, scale
	float sx = hypotf(a, b), angle = atan2f(b, a);
	float sy = -c * sinf(angle) + d * cosf(angle);
	if (sx == 0 || sy == 0) {
		gtk_snapshot_scale(snapshot, 0, 0);
		return;
	}
	float skew = (c * cosf(angle) + d * sinf(angle)) / sy;
	gtk_snapshot_translate(snapshot, &GRAPHENE_POINT_INIT(tx, ty));
	gtk_snapshot_rotate(snapshot, angle * 180 / G_PI);
	if (skew)
		gtk_snapshot_transform(snapshot, gsk_transform_skew(NULL, atanf(skew) * 180 / G_PI, 0));
	gtk_snapshot_scale(snapshot, sx, sy);
}

// CLAMP draws the bitmap once, its edges not extended; MIRROR repeats (ATL-48)
static void append_bitmap_shader(GtkSnapshot *snapshot, const graphene_rect_t *bounds, const struct gfx_shader *shader)
{
	GdkTexture *texture = gsk_bitmap_texture(shader->bitmap);
	graphene_rect_t tile = GRAPHENE_RECT_INIT(0, 0, gdk_texture_get_width(texture), gdk_texture_get_height(texture));
	if (shader->tile == SHADER_CLAMP && shader->tile_y == SHADER_CLAMP) {
		gtk_snapshot_append_texture(snapshot, texture, &tile);
	} else {
		gtk_snapshot_push_repeat(snapshot, bounds, &tile);
		gtk_snapshot_append_texture(snapshot, texture, &tile);
		gtk_snapshot_pop(snapshot);
	}
	g_object_unref(texture);
}

// dst's colors, then src's composited onto them alone in the shader's mode
static void append_compose(GtkSnapshot *snapshot, const graphene_rect_t *bounds, const struct gfx_shader *shader)
{
	gtk_snapshot_push_isolation(snapshot, GSK_ISOLATION_BACKGROUND);
	if (shader->dst)
		append_shader(snapshot, bounds, shader->dst, 1);
	if (shader->src) {
		bool source;
		struct composite composite = composite_begin(snapshot, shader->mode, bounds, NULL, &source);
		append_shader(snapshot, bounds, shader->src, 1);
		composite_end(snapshot, &composite);
	}
	gtk_snapshot_pop(snapshot);
}

// fills the snapshot's fill or stroke push: the local matrix applies in it, the paint's alpha over it
static void append_shader(GtkSnapshot *snapshot, const graphene_rect_t *shape, const struct gfx_shader *shader, float alpha)
{
	graphene_matrix_t inverse;
	graphene_rect_t bounds;
	if (!graphene_matrix_inverse(&shader->matrix, &inverse))
		return;
	graphene_matrix_transform_bounds(&inverse, shape, &bounds);
	if (shader->kind == SHADER_BITMAP || shader->kind == SHADER_COMPOSE) {
		gtk_snapshot_save(snapshot);
		transform_affine(snapshot, &shader->matrix);
		gtk_snapshot_push_opacity(snapshot, alpha);
		if (shader->kind == SHADER_BITMAP)
			append_bitmap_shader(snapshot, &bounds, shader);
		else
			append_compose(snapshot, &bounds, shader);
		gtk_snapshot_pop(snapshot);
		gtk_snapshot_restore(snapshot);
		return;
	}
	float t_min, t_max;
	shader_t_range(shader, &(struct gfx_rect){bounds.origin.x, bounds.origin.y, bounds.origin.x + bounds.size.width, bounds.origin.y + bounds.size.height}, &t_min, &t_max);
	int k0, k1;
	uint32_t *colors;
	float *positions;
	size_t n = shader_stops(shader, t_min, t_max, &k0, &k1, &colors, &positions);
	GskColorStop *stops = g_new(GskColorStop, n);
	for (size_t i = 0; i < n; i++)
		stops[i] = (GskColorStop){positions[i], {((colors[i] >> 16) & 0xff) / 255.f, ((colors[i] >> 8) & 0xff) / 255.f, (colors[i] & 0xff) / 255.f, (colors[i] >> 24) / 255.f * alpha}};
	gtk_snapshot_save(snapshot);
	transform_affine(snapshot, &shader->matrix);
	graphene_point_t center = GRAPHENE_POINT_INIT(shader->x0, shader->y0);
	float dx = shader->x1 - shader->x0, dy = shader->y1 - shader->y0;
	if (shader->kind == SHADER_LINEAR && (dx || dy))
		gtk_snapshot_append_linear_gradient(snapshot, &bounds, &GRAPHENE_POINT_INIT(shader->x0 + dx * k0, shader->y0 + dy * k0),
		                                    &GRAPHENE_POINT_INIT(shader->x0 + dx * k1, shader->y0 + dy * k1), stops, n);
	else if (shader->kind == SHADER_RADIAL && shader->radius > 0)
		gtk_snapshot_append_radial_gradient(snapshot, &bounds, &center, shader->radius * k1, shader->radius * k1, (float)k0 / k1, 1, stops, n);
	else if (shader->kind == SHADER_SWEEP)
		gtk_snapshot_append_conic_gradient(snapshot, &bounds, &center, 90, stops, n); // GSK's 0 is at the top
	else // no length: the last color
		gtk_snapshot_append_color(snapshot, &stops[n - 1].color, &bounds);
	gtk_snapshot_restore(snapshot);
	g_free(stops);
	free(positions);
	free(colors);
}

struct path_draw {
	GskPath *path;
	GskFillRule rule;
};

static void draw_path(GtkSnapshot *snapshot, const struct gsk_draw *draw, enum gsk_part part)
{
	const struct path_draw *path = draw->data;
	const struct AndroidPaint *paint = draw->paint;
	if (part == GSK_SOURCE) {
		append_source(snapshot, paint, draw->bounds);
	} else if (part == GSK_CONTENT && paint->shader) {
		if (paint->is_fill) {
			gtk_snapshot_push_fill(snapshot, path->path, path->rule);
			append_source(snapshot, paint, draw->bounds);
			gtk_snapshot_pop(snapshot);
		}
		if (paint->is_stroke) {
			gtk_snapshot_push_stroke(snapshot, path->path, paint->gsk_stroke);
			append_source(snapshot, paint, draw->bounds);
			gtk_snapshot_pop(snapshot);
		}
	} else {
		const GdkRGBA *color = part == GSK_COVERAGE ? &WHITE : &paint->color;
		if (paint->is_fill)
			gtk_snapshot_append_fill(snapshot, path->path, path->rule, color);
		if (paint->is_stroke)
			gtk_snapshot_append_stroke(snapshot, path->path, paint->gsk_stroke, color);
	}
}

static void paint_path(GtkSnapshot *snapshot, GskPath *path, GskFillRule rule, const struct AndroidPaint *paint)
{
	graphene_rect_t bounds = GRAPHENE_RECT_INIT(0, 0, 0, 0), stroke;
	if (paint->is_fill && !gsk_path_get_bounds(path, &bounds))
		bounds = GRAPHENE_RECT_INIT(0, 0, 0, 0);
	if (paint->is_stroke && gsk_path_get_stroke_bounds(path, paint->gsk_stroke, &stroke))
		graphene_rect_union(&bounds, &stroke, &bounds);
	paint_draw(snapshot, paint, &bounds, draw_path, &(struct path_draw){path, rule});
}

// a filled rounded rectangle as a clipped source, which GSK draws faster than a path
static void draw_round_rect(GtkSnapshot *snapshot, const struct gsk_draw *draw, enum gsk_part part)
{
	const GskRoundedRect *rect = draw->data;
	const struct AndroidPaint *paint = draw->paint;
	if (part == GSK_SOURCE) {
		append_source(snapshot, paint, draw->bounds);
		return;
	}
	if (paint->is_fill) {
		gtk_snapshot_push_rounded_clip(snapshot, rect);
		if (part == GSK_COVERAGE)
			gtk_snapshot_append_color(snapshot, &WHITE, &rect->bounds);
		else
			append_source(snapshot, paint, &rect->bounds);
		gtk_snapshot_pop(snapshot);
	}
	if (paint->is_stroke) {
		GskPathBuilder *builder = gsk_path_builder_new();
		gsk_path_builder_add_rounded_rect(builder, rect);
		GskPath *path = gsk_path_builder_free_to_path(builder);
		if (part == GSK_CONTENT && paint->shader) {
			gtk_snapshot_push_stroke(snapshot, path, paint->gsk_stroke);
			append_source(snapshot, paint, draw->bounds);
			gtk_snapshot_pop(snapshot);
		} else {
			gtk_snapshot_append_stroke(snapshot, path, paint->gsk_stroke, part == GSK_COVERAGE ? &WHITE : &paint->color);
		}
		gsk_path_unref(path);
	}
}

static void paint_round_rect(GtkSnapshot *snapshot, const GskRoundedRect *rect, const struct AndroidPaint *paint)
{
	graphene_rect_t bounds = rect->bounds;
	if (paint->is_stroke) { // a miter's corner reaches out less than a line width
		float width = gsk_stroke_get_line_width(paint->gsk_stroke);
		graphene_rect_inset(&bounds, -width, -width);
	}
	paint_draw(snapshot, paint, &bounds, draw_round_rect, rect);
}

struct gfx_canvas *gfx_canvas_new(void)
{
	return (struct gfx_canvas *)gtk_snapshot_new();
}

void gfx_canvas_free(struct gfx_canvas *canvas)
{
	g_object_unref(canvas);
}

void gfx_canvas_save(struct gfx_canvas *canvas)
{
	struct gsk_painter *painter = gsk_painter_of(GTK_SNAPSHOT(canvas));
	if (painter) {
		gsk_painter_save(painter);
		return;
	}
	gtk_snapshot_save(GTK_SNAPSHOT(canvas));
	struct level level = {0};
	memcpy(level.m, top_level(GTK_SNAPSHOT(canvas))->m, sizeof(level.m));
	g_array_append_val(levels_of(GTK_SNAPSHOT(canvas)), level);
}

void gfx_canvas_save_layer(struct gfx_canvas *canvas, const struct gfx_rect *bounds, uint8_t alpha, enum gfx_blend blend)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	struct gsk_painter *painter = gsk_painter_of(snapshot);
	if (painter) {
		gsk_painter_save_layer(painter, bounds, alpha, blend);
		return;
	}
	graphene_rect_t rect = bounds ? GRAPHENE_RECT_INIT(bounds->left, bounds->top, bounds->right - bounds->left, bounds->bottom - bounds->top) : EVERYWHERE;
	gfx_canvas_save(canvas);
	struct level *level = top_level(snapshot);
	if (bounds) {
		gtk_snapshot_push_clip(snapshot, &rect);
		level->outer = 1;
	}
	bool source;
	level->layer = composite_begin(snapshot, blend, &rect, NULL, &source);
	gtk_snapshot_push_isolation(snapshot, GSK_ISOLATION_BACKGROUND); // a layer starts transparent
	gsk_canvas_pushed(snapshot);
	gtk_snapshot_push_opacity(snapshot, alpha / 255.);
	gsk_canvas_pushed(snapshot);
}

void gfx_canvas_restore(struct gfx_canvas *canvas)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	struct gsk_painter *painter = gsk_painter_of(snapshot);
	if (painter) {
		gsk_painter_restore(painter);
		return;
	}
	GArray *levels = levels_of(snapshot);
	if (levels->len > 1) {
		struct level *level = &g_array_index(levels, struct level, levels->len - 1);
		for (guint n = level->pushes; n; n--)
			gtk_snapshot_pop(snapshot);
		composite_end(snapshot, &level->layer);
		for (guint n = level->outer; n; n--)
			gtk_snapshot_pop(snapshot);
		g_array_set_size(levels, levels->len - 1);
	}
	gtk_snapshot_restore(snapshot);
}

void gfx_canvas_clip_rect(struct gfx_canvas *canvas, float left, float top, float right, float bottom, enum gfx_clip_op op)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	struct gsk_painter *painter = gsk_painter_of(snapshot);
	if (painter) {
		gsk_painter_clip_rect(painter, &(struct gfx_rect){left, top, right, bottom}, op);
		return;
	}
	graphene_rect_t rect = GRAPHENE_RECT_INIT(left, top, right - left, bottom - top);
	if (op == GFX_CLIP_DIFFERENCE) { // the rect as an inverted mask: the source until the restore's pop
		gtk_snapshot_push_mask(snapshot, GSK_MASK_MODE_INVERTED_ALPHA);
		gtk_snapshot_append_color(snapshot, &(GdkRGBA){0, 0, 0, 1}, &rect);
		gtk_snapshot_pop(snapshot);
	} else {
		gtk_snapshot_push_clip(snapshot, &rect);
	}
	gsk_canvas_pushed(snapshot);
}

// a Canvas(Bitmap)'s painter takes the matrix
static bool painter_concat(struct gfx_canvas *canvas, const graphene_matrix_t *matrix)
{
	struct gsk_painter *painter = gsk_painter_of(GTK_SNAPSHOT(canvas));
	if (painter)
		gsk_painter_concat(painter, matrix);
	return painter != NULL;
}

void gfx_canvas_translate(struct gfx_canvas *canvas, float dx, float dy)
{
	graphene_matrix_t m;
	if (!painter_concat(canvas, graphene_matrix_init_translate(&m, &GRAPHENE_POINT3D_INIT(dx, dy, 0))))
		gtk_snapshot_translate(GTK_SNAPSHOT(canvas), &GRAPHENE_POINT_INIT(dx, dy));
}

void gfx_canvas_rotate(struct gfx_canvas *canvas, float degrees)
{
	graphene_matrix_t m;
	if (painter_concat(canvas, matrix_init_rotate(&m, degrees, 0, 0)))
		return;
	gtk_snapshot_rotate(GTK_SNAPSHOT(canvas), degrees);
	float c = cosf(degrees * (float)M_PI / 180), s = sinf(degrees * (float)M_PI / 180);
	transformed(GTK_SNAPSHOT(canvas), c, -s, s, c);
}

void gfx_canvas_scale(struct gfx_canvas *canvas, float sx, float sy)
{
	graphene_matrix_t m;
	if (painter_concat(canvas, graphene_matrix_init_scale(&m, sx, sy, 1)))
		return;
	gtk_snapshot_scale(GTK_SNAPSHOT(canvas), sx, sy);
	transformed(GTK_SNAPSHOT(canvas), sx, 0, 0, sy);
}

void gfx_canvas_concat(struct gfx_canvas *canvas, const graphene_matrix_t *matrix)
{
	if (painter_concat(canvas, matrix))
		return;
	transform_affine(GTK_SNAPSHOT(canvas), matrix);
	transformed(GTK_SNAPSHOT(canvas), graphene_matrix_get_value(matrix, 0, 0), graphene_matrix_get_value(matrix, 1, 0),
	            graphene_matrix_get_value(matrix, 0, 1), graphene_matrix_get_value(matrix, 1, 1));
}

void gfx_canvas_draw_rect(struct gfx_canvas *canvas, float left, float top, float right, float bottom, const struct gfx_paint *gfx_paint)
{
	GskRoundedRect rect;
	gsk_rounded_rect_init_from_rect(&rect, &GRAPHENE_RECT_INIT(left, top, right - left, bottom - top), 0);
	paint_round_rect(GTK_SNAPSHOT(canvas), &rect, (const struct AndroidPaint *)gfx_paint);
}

void gfx_canvas_draw_round_rect(struct gfx_canvas *canvas, float left, float top, float right, float bottom, float rx, float ry, const struct gfx_paint *gfx_paint)
{
	GskRoundedRect rect = {
		.bounds = GRAPHENE_RECT_INIT(left, top, right - left, bottom - top),
		.corner = {{rx, ry}, {rx, ry}, {rx, ry}, {rx, ry}},
	};
	paint_round_rect(GTK_SNAPSHOT(canvas), &rect, (const struct AndroidPaint *)gfx_paint);
}

void gfx_canvas_draw_line(struct gfx_canvas *canvas, float x0, float y0, float x1, float y1, const struct gfx_paint *gfx_paint)
{
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	struct AndroidPaint stroke = *paint; // a line is stroked whatever the paint's style
	stroke.is_fill = false;
	stroke.is_stroke = true;
	GskPathBuilder *builder = gsk_path_builder_new();
	gsk_path_builder_move_to(builder, x0, y0);
	gsk_path_builder_line_to(builder, x1, y1);
	GskPath *path = gsk_path_builder_free_to_path(builder);
	paint_path(GTK_SNAPSHOT(canvas), path, GSK_FILL_RULE_WINDING, &stroke);
	gsk_path_unref(path);
}

void gfx_canvas_draw_path(struct gfx_canvas *canvas, struct gfx_path *path, bool even_odd, const struct gfx_paint *gfx_paint)
{
	GskPath *gsk_path = gsk_path_from_gfx(path);
	paint_path(GTK_SNAPSHOT(canvas), gsk_path, even_odd ? GSK_FILL_RULE_EVEN_ODD : GSK_FILL_RULE_WINDING, (const struct AndroidPaint *)gfx_paint);
	gsk_path_unref(gsk_path);
}

void gfx_canvas_clip_path(struct gfx_canvas *canvas, struct gfx_path *path, bool even_odd, enum gfx_clip_op op)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(canvas);
	struct gsk_painter *painter = gsk_painter_of(snapshot);
	if (painter) {
		gsk_painter_clip_path(painter, path, even_odd, op);
		return;
	}
	GskPath *gsk_path = gsk_path_from_gfx(path);
	gtk_snapshot_push_mask(snapshot, op == GFX_CLIP_DIFFERENCE ? GSK_MASK_MODE_INVERTED_ALPHA : GSK_MASK_MODE_ALPHA);
	gtk_snapshot_append_fill(snapshot, gsk_path, even_odd ? GSK_FILL_RULE_EVEN_ODD : GSK_FILL_RULE_WINDING, &WHITE);
	gtk_snapshot_pop(snapshot);
	gsk_canvas_pushed(snapshot);
	gsk_path_unref(gsk_path);
}

void gfx_canvas_draw_arc(struct gfx_canvas *canvas, float left, float top, float right, float bottom, float start, float sweep, bool use_center, const struct gfx_paint *gfx_paint)
{
	struct gfx_path *arc = path_new();
	path_add_draw_arc(arc, left, top, right, bottom, start, sweep, use_center);
	GskPath *gsk_path = gsk_path_from_gfx(arc);
	paint_path(GTK_SNAPSHOT(canvas), gsk_path, GSK_FILL_RULE_WINDING, (const struct AndroidPaint *)gfx_paint);
	gsk_path_unref(gsk_path);
	path_unref(arc);
}

void gfx_canvas_draw_drawable(struct gfx_canvas *canvas, struct gfx_drawable *drawable, int width, int height)
{
	GdkPaintable *paintable = GDK_PAINTABLE(drawable);
	struct gsk_painter *painter = gsk_painter_of(GTK_SNAPSHOT(canvas));
	if (JAVA_IS_PAINTABLE(paintable))
		return;
	if (painter)
		gsk_painter_draw_paintable(painter, paintable, width, height);
	else
		gdk_paintable_snapshot(paintable, GDK_SNAPSHOT(canvas), width, height);
}

struct bitmap_draw {
	const struct gfx_bitmap *bitmap;
	GdkTexture *texture; // the bitmap's
	const struct gfx_rect *src;
	graphene_rect_t dst;
};

/* src's whole texels with each edge texel repeated pad times outward, as Skia's clamped sampling
 * gives them past the edge; premultiplied */
static GdkTexture *padded_texture(const struct gfx_bitmap *bitmap, int x0, int y0, int x1, int y1, int pad)
{
	int width = bitmap->width, w = x1 - x0, h = y1 - y0, padded_w = w + 2 * pad, padded_h = h + 2 * pad;
	const uint32_t *texels = bitmap->words;
	uint32_t *padded = g_malloc((size_t)padded_w * padded_h * 4);
	for (int y = 0; y < padded_h; y++)
		for (int x = 0; x < padded_w; x++)
			padded[(size_t)y * padded_w + x] = texels[(size_t)(y0 + CLAMP(y - pad, 0, h - 1)) * width + x0 + CLAMP(x - pad, 0, w - 1)];
	GBytes *bytes = g_bytes_new_take(padded, (size_t)padded_w * padded_h * 4);
	GdkTexture *result = gdk_memory_texture_new(padded_w, padded_h, GDK_MEMORY_B8G8R8A8_PREMULTIPLIED, bytes, padded_w * 4);
	g_bytes_unref(bytes);
	return result;
}

// an ALPHA_8 bitmap's colors: the paint's shader or color at its alpha
static void draw_mask_colors(GtkSnapshot *snapshot, const struct gsk_draw *draw, enum gsk_part part)
{
	if (part == GSK_COVERAGE)
		gtk_snapshot_append_color(snapshot, &WHITE, draw->bounds);
	else if (draw->paint->shader)
		append_shader(snapshot, draw->bounds, draw->paint->shader, draw->paint->color.alpha);
	else
		gtk_snapshot_append_color(snapshot, &draw->paint->color, draw->bounds);
}

/* src: the texture scaled so that src fills dst, at the paint's alpha; as a source its edge texels
 * reach past dst, as Skia's clamped sampling does. An ALPHA_8 one masks the paint's colors through
 * its filter (Skia's order) and is its own coverage. */
static void draw_texture(GtkSnapshot *snapshot, const struct gsk_draw *draw, enum gsk_part part)
{
	const struct bitmap_draw *bitmap = draw->data;
	const graphene_rect_t *dst = &bitmap->dst;
	bool mask = bitmap->bitmap->config == GFX_BITMAP_ALPHA_8;
	if (part == GSK_COVERAGE && !mask) {
		gtk_snapshot_append_color(snapshot, &WHITE, dst);
		return;
	}
	int width = gdk_texture_get_width(bitmap->texture), height = gdk_texture_get_height(bitmap->texture);
	struct gfx_rect src = bitmap->src ? *bitmap->src : (struct gfx_rect){0, 0, width, height};
	float xscale = dst->size.width / (src.right - src.left), yscale = dst->size.height / (src.bottom - src.top);
	graphene_rect_t drawn = *dst;
	bool faded = !mask && draw->paint->color.alpha < 1;
	if (faded)
		gtk_snapshot_push_opacity(snapshot, draw->paint->color.alpha);
	if (mask)
		gtk_snapshot_push_mask(snapshot, GSK_MASK_MODE_ALPHA);
	if (part == GSK_SOURCE) {
		int x0 = CLAMP(floorf(src.left), 0, width - 1), y0 = CLAMP(floorf(src.top), 0, height - 1);
		int x1 = CLAMP(ceilf(src.right), x0 + 1, width), y1 = CLAMP(ceilf(src.bottom), y0 + 1, height);
		int pad = CLAMP(ceilf(pixels_margin(snapshot) / fminf(fabsf(xscale), fabsf(yscale))), 1, 64);
		GdkTexture *padded = padded_texture(bitmap->bitmap, x0, y0, x1, y1, pad);
		drawn = GRAPHENE_RECT_INIT(dst->origin.x - (src.left - x0 + pad) * xscale, dst->origin.y - (src.top - y0 + pad) * yscale,
		                           (x1 - x0 + 2 * pad) * xscale, (y1 - y0 + 2 * pad) * yscale);
		gtk_snapshot_append_texture(snapshot, padded, &drawn);
		g_object_unref(padded);
	} else if (!bitmap->src) {
		gtk_snapshot_append_texture(snapshot, bitmap->texture, dst);
	} else {
		gtk_snapshot_push_clip(snapshot, dst);
		gtk_snapshot_append_texture(snapshot, bitmap->texture, &GRAPHENE_RECT_INIT(dst->origin.x - src.left * xscale, dst->origin.y - src.top * yscale, width * xscale, height * yscale));
		gtk_snapshot_pop(snapshot);
	}
	if (mask) {
		gtk_snapshot_pop(snapshot);
		if (part == GSK_COVERAGE)
			gtk_snapshot_append_color(snapshot, &WHITE, &drawn);
		else
			gsk_filtered(snapshot, draw->paint, &(struct gsk_draw){draw_mask_colors, NULL, draw->paint, &drawn}, part);
		gtk_snapshot_pop(snapshot);
	}
	if (faded)
		gtk_snapshot_pop(snapshot);
}

// the texture at the paint's alpha through its color filter (Skia's order); its rectangle is its coverage
static void draw_bitmap(GtkSnapshot *snapshot, const struct gsk_draw *draw, enum gsk_part part)
{
	const struct bitmap_draw *bitmap = draw->data;
	if (bitmap->bitmap->config == GFX_BITMAP_ALPHA_8)
		draw_texture(snapshot, draw, part);
	else if (part == GSK_COVERAGE)
		gtk_snapshot_append_color(snapshot, &WHITE, &bitmap->dst);
	else
		gsk_filtered(snapshot, draw->paint, &(struct gsk_draw){draw_texture, bitmap, draw->paint, draw->bounds}, part);
}

void gfx_canvas_draw_bitmap(struct gfx_canvas *canvas, struct gfx_bitmap *bitmap, const struct gfx_rect *src, const struct gfx_rect *dst, const struct gfx_paint *gfx_paint)
{
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	struct gsk_painter *painter = gsk_painter_of(GTK_SNAPSHOT(canvas));
	if (painter && !(bitmap->config == GFX_BITMAP_ALPHA_8 && paint->shader)) { // a shader is GSK nodes
		gsk_painter_draw_bitmap(painter, bitmap, src, dst, paint);
		return;
	}
	struct bitmap_draw draw = {bitmap, gsk_bitmap_texture(bitmap), src, GRAPHENE_RECT_INIT(dst->left, dst->top, dst->right - dst->left, dst->bottom - dst->top)};
	gsk_composite(GTK_SNAPSHOT(canvas), paint->blend, &(struct gsk_draw){draw_bitmap, &draw, paint, &draw.dst}, NULL);
	g_object_unref(draw.texture);
}

struct text_draw {
	PangoLayout *layout;
	GskPath *outline; // the glyphs' outline at the origin, for a stroke
	graphene_point_t origin;
};

static void append_layout(GtkSnapshot *snapshot, const struct text_draw *text, const GdkRGBA *color)
{
	gtk_snapshot_save(snapshot);
	gtk_snapshot_translate(snapshot, &text->origin);
	gtk_snapshot_append_layout(snapshot, text->layout, color);
	gtk_snapshot_restore(snapshot);
}

// glyphs fill as GSK draws text (hinted); a stroke follows their outline, as a path's does
static void draw_text(GtkSnapshot *snapshot, const struct gsk_draw *draw, enum gsk_part part)
{
	const struct text_draw *text = draw->data;
	const struct AndroidPaint *paint = draw->paint;
	if (part == GSK_SOURCE) {
		append_source(snapshot, paint, draw->bounds);
	} else if (part == GSK_CONTENT && paint->shader) {
		if (paint->is_fill) { // the glyphs as the mask of the shader's colors
			gtk_snapshot_push_mask(snapshot, GSK_MASK_MODE_ALPHA);
			append_layout(snapshot, text, &WHITE);
			gtk_snapshot_pop(snapshot);
			append_source(snapshot, paint, draw->bounds);
			gtk_snapshot_pop(snapshot);
		}
		if (paint->is_stroke) { // the source in canvas coordinates, inside the stroke at the origin
			gtk_snapshot_save(snapshot);
			gtk_snapshot_translate(snapshot, &text->origin);
			gtk_snapshot_push_stroke(snapshot, text->outline, paint->gsk_stroke);
			gtk_snapshot_translate(snapshot, &GRAPHENE_POINT_INIT(-text->origin.x, -text->origin.y));
			append_source(snapshot, paint, draw->bounds);
			gtk_snapshot_pop(snapshot);
			gtk_snapshot_restore(snapshot);
		}
	} else {
		const GdkRGBA *color = part == GSK_COVERAGE ? &WHITE : &paint->color;
		if (paint->is_fill)
			append_layout(snapshot, text, color);
		if (paint->is_stroke) {
			gtk_snapshot_save(snapshot);
			gtk_snapshot_translate(snapshot, &text->origin);
			gtk_snapshot_append_stroke(snapshot, text->outline, paint->gsk_stroke, color);
			gtk_snapshot_restore(snapshot);
		}
	}
}

void gfx_canvas_draw_text(struct gfx_canvas *canvas, const uint16_t *text, size_t len, float x, float y, const struct gfx_paint *gfx_paint)
{
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	PangoLayout *layout = gsk_text_layout(gfx_paint, text, len, NULL);
	PangoRectangle ink, logical;
	pango_layout_get_extents(layout, &ink, &logical);
	y -= (float)pango_layout_get_baseline(layout) / PANGO_SCALE;
	if (paint->alignment == PANGO_ALIGN_CENTER)
		x -= (float)logical.width / PANGO_SCALE / 2;
	else if (paint->alignment == PANGO_ALIGN_RIGHT)
		x -= (float)logical.width / PANGO_SCALE;
	pango_extents_to_pixels(&ink, NULL);
	pango_extents_to_pixels(&logical, NULL);
	graphene_rect_t bounds, logical_bounds = GRAPHENE_RECT_INIT(x + logical.x, y + logical.y, logical.width, logical.height);
	graphene_rect_union(&GRAPHENE_RECT_INIT(x + ink.x, y + ink.y, ink.width, ink.height), &logical_bounds, &bounds);
	GskPath *outline = NULL;
	if (paint->is_stroke) {
		GskPathBuilder *builder = gsk_path_builder_new();
		gsk_path_builder_add_layout(builder, layout);
		outline = gsk_path_builder_free_to_path(builder);
		graphene_rect_t stroke;
		if (gsk_path_get_stroke_bounds(outline, paint->gsk_stroke, &stroke))
			graphene_rect_union(&bounds, graphene_rect_offset(&stroke, x, y), &bounds);
	}
	paint_draw(GTK_SNAPSHOT(canvas), paint, &bounds, draw_text, &(struct text_draw){layout, outline, GRAPHENE_POINT_INIT(x, y)});
	if (outline)
		gsk_path_unref(outline);
	g_object_unref(layout);
}

#ifdef ATL_TEST_HOOKS
void gfx_canvas_dump(const struct gfx_canvas *canvas, FILE *out) {}
#endif
