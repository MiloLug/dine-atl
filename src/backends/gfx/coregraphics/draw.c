#include <math.h>
#include <pthread.h>
#include <stdlib.h>

#include <graphene.h>

#include "src/shared/bitmap.h"
#include "src/shared/path.h"
#include "src/shared/shader.h"

#include "src/shared/blend.h"
#include "../record/record.h"
#include "coregraphics.h"

struct gfx_canvas *cg_list_ref(struct gfx_canvas *list)
{
	return record_canvas_ref(list);
}

/* enum gfx_blend's; MODULATE has none: multiply stands in, and COLOR_DODGE, COLOR_BURN and
 * SOFT_LIGHT are CG's, not Skia's: a painter blends those per pixel, a view's replay keeps CG's
 * (ATL-52). DST keeps the destination: callers skip it. */
static const CGBlendMode blend_modes[GFX_BLEND_COUNT] = {
	[GFX_BLEND_CLEAR] = kCGBlendModeClear, [GFX_BLEND_SRC] = kCGBlendModeCopy, [GFX_BLEND_DST] = kCGBlendModeNormal,
	[GFX_BLEND_SRC_OVER] = kCGBlendModeNormal, [GFX_BLEND_DST_OVER] = kCGBlendModeDestinationOver,
	[GFX_BLEND_SRC_IN] = kCGBlendModeSourceIn, [GFX_BLEND_DST_IN] = kCGBlendModeDestinationIn,
	[GFX_BLEND_SRC_OUT] = kCGBlendModeSourceOut, [GFX_BLEND_DST_OUT] = kCGBlendModeDestinationOut,
	[GFX_BLEND_SRC_ATOP] = kCGBlendModeSourceAtop, [GFX_BLEND_DST_ATOP] = kCGBlendModeDestinationAtop,
	[GFX_BLEND_XOR] = kCGBlendModeXOR, [GFX_BLEND_PLUS] = kCGBlendModePlusLighter, [GFX_BLEND_MODULATE] = kCGBlendModeMultiply,
	[GFX_BLEND_SCREEN] = kCGBlendModeScreen, [GFX_BLEND_OVERLAY] = kCGBlendModeOverlay, [GFX_BLEND_DARKEN] = kCGBlendModeDarken,
	[GFX_BLEND_LIGHTEN] = kCGBlendModeLighten, [GFX_BLEND_COLOR_DODGE] = kCGBlendModeColorDodge,
	[GFX_BLEND_COLOR_BURN] = kCGBlendModeColorBurn, [GFX_BLEND_HARD_LIGHT] = kCGBlendModeHardLight,
	[GFX_BLEND_SOFT_LIGHT] = kCGBlendModeSoftLight, [GFX_BLEND_DIFFERENCE] = kCGBlendModeDifference,
	[GFX_BLEND_EXCLUSION] = kCGBlendModeExclusion, [GFX_BLEND_MULTIPLY] = kCGBlendModeMultiply, [GFX_BLEND_HUE] = kCGBlendModeHue,
	[GFX_BLEND_SATURATION] = kCGBlendModeSaturation, [GFX_BLEND_COLOR] = kCGBlendModeColor, [GFX_BLEND_LUMINOSITY] = kCGBlendModeLuminosity,
};

CGBlendMode cg_blend(enum gfx_blend mode)
{
	return mode < GFX_BLEND_COUNT ? blend_modes[mode] : kCGBlendModeNormal;
}

bool cg_blend_exact(enum gfx_blend mode)
{
	return mode != GFX_BLEND_MODULATE && mode != GFX_BLEND_COLOR_DODGE && mode != GFX_BLEND_COLOR_BURN && mode != GFX_BLEND_SOFT_LIGHT;
}

// a bitmap context of the clip's device box, drawn in the context's space; NULL: nothing to draw
static CGContextRef device_offscreen(CGContextRef context, CGAffineTransform *ctm, CGRect *device)
{
	*ctm = CGContextGetCTM(context);
	*device = CGRectIntegral(CGRectApplyAffineTransform(CGContextGetClipBoundingBox(context), *ctm));
	if (CGRectIsEmpty(*device) || CGRectIsInfinite(*device))
		return NULL;
	size_t width = device->size.width, height = device->size.height;
	CGContextRef offscreen = CGBitmapContextCreate(NULL, width, height, 8, width * 4, cg_srgb(), kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
	if (!offscreen)
		return NULL;
	CGContextClearRect(offscreen, CGRectMake(0, 0, width, height));
	CGContextTranslateCTM(offscreen, -device->origin.x, -device->origin.y);
	CGContextConcatCTM(offscreen, *ctm);
	return offscreen;
}

// an offscreen drawn back pixel for pixel into the context's space
static void draw_offscreen(CGContextRef context, CGContextRef offscreen, CGAffineTransform ctm, CGRect device)
{
	CGImageRef image = CGBitmapContextCreateImage(offscreen);
	CGContextSaveGState(context);
	CGContextConcatCTM(context, CGAffineTransformInvert(ctm));
	CGContextDrawImage(context, device, image);
	CGContextRestoreGState(context);
	CGImageRelease(image);
}

// a solid paint's color through its color filter
static void set_color(CGContextRef context, const struct gfx_paint *paint)
{
	struct blend_color d = blend_filter_word(&paint->filter, paint->color);
	float r = d.a ? d.r / d.a : 0, g = d.a ? d.g / d.a : 0, b = d.a ? d.b / d.a : 0;
	CGContextSetRGBFillColor(context, r, g, b, d.a);
	CGContextSetRGBStrokeColor(context, r, g, b, d.a);
}

// the image at alpha through a color filter, made where the op draws (the Android thread for a
// bitmap's pixels, UIKit's for a layer)
static CGImageRef filtered_image(CGImageRef image, const struct gfx_color_filter *filter, float alpha)
{
	size_t width = CGImageGetWidth(image), height = CGImageGetHeight(image);
	CGContextRef context = CGBitmapContextCreate(NULL, width, height, 8, width * 4, cg_srgb(), kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
	if (!context)
		return CGImageRetain(image);
	CGContextDrawImage(context, CGRectMake(0, 0, width, height), image);
	uint32_t *pixels = CGBitmapContextGetData(context);
	size_t row_words = CGBitmapContextGetBytesPerRow(context) / 4;
	for (size_t y = 0; y < height; y++)
		for (size_t x = 0; x < width; x++)
			pixels[y * row_words + x] = blend_filter_pixel(filter, pixels[y * row_words + x], alpha);
	CGImageRef filtered = CGBitmapContextCreateImage(context);
	CGContextRelease(context);
	return filtered;
}

static void set_stroke(CGContextRef context, const struct gfx_paint *paint)
{
	// Paint.Cap and Join share CGLineCap's and CGLineJoin's order; width 0 is a hairline
	CGContextSetLineWidth(context, paint->stroke_width > 0 ? paint->stroke_width : 1);
	CGContextSetLineCap(context, (CGLineCap)paint->stroke_cap);
	CGContextSetLineJoin(context, (CGLineJoin)paint->stroke_join);
}

// Android's affine part; CG can't apply a perspective row
static CGAffineTransform affine(const graphene_matrix_t *m)
{
	return CGAffineTransformMake(graphene_matrix_get_value(m, 0, 0), graphene_matrix_get_value(m, 0, 1),
	                             graphene_matrix_get_value(m, 1, 0), graphene_matrix_get_value(m, 1, 1),
	                             graphene_matrix_get_value(m, 3, 0), graphene_matrix_get_value(m, 3, 1));
}

static void draw_shader(CGContextRef context, const struct gfx_shader *shader, bool live);

// the bitmap at 0, 0 in its pixels; CLAMP doesn't extend its edges, MIRROR repeats (ATL-48)
static void draw_bitmap_shader(CGContextRef context, const struct gfx_shader *shader, bool live)
{
	unsigned generation;
	CGImageRef image = cg_bitmap_image(shader->bitmap, live, &generation);
	if (!image)
		return;
	CGRect tile = CGRectMake(0, 0, CGImageGetWidth(image), CGImageGetHeight(image));
	// CGContextDrawImage draws y up; the context is y down
	CGContextTranslateCTM(context, 0, tile.size.height);
	CGContextScaleCTM(context, 1, -1);
	if (shader->tile == SHADER_CLAMP && shader->tile_y == SHADER_CLAMP)
		CGContextDrawImage(context, tile, image);
	else
		CGContextDrawTiledImage(context, tile, image);
	CGImageRelease(image);
}

// a painter's: src blended onto dst per pixel, as CG has no blend mode for it
static void draw_compose_pixels(CGContextRef context, const struct gfx_shader *shader)
{
	CGAffineTransform ctm;
	CGRect device;
	CGContextRef dst = device_offscreen(context, &ctm, &device);
	if (!dst)
		return;
	CGContextRef src = device_offscreen(context, &ctm, &device);
	if (!src) {
		CGContextRelease(dst);
		return;
	}
	draw_shader(dst, shader->dst, true);
	draw_shader(src, shader->src, true);
	uint32_t *d = CGBitmapContextGetData(dst), *s = CGBitmapContextGetData(src);
	size_t d_row = CGBitmapContextGetBytesPerRow(dst) / 4, s_row = CGBitmapContextGetBytesPerRow(src) / 4;
	for (size_t y = 0; y < (size_t)device.size.height; y++)
		for (size_t x = 0; x < (size_t)device.size.width; x++)
			d[y * d_row + x] = blend_to_word(blend(shader->mode, blend_from_word(s[y * s_row + x]), blend_from_word(d[y * d_row + x])));
	draw_offscreen(context, dst, ctm, device);
	CGContextRelease(src);
	CGContextRelease(dst);
}

// dst, then src over it with the mode's blend, in a transparency layer
static void draw_compose(CGContextRef context, const struct gfx_shader *shader, bool live)
{
	if (live && shader->dst && shader->src && !cg_blend_exact(shader->mode)) {
		draw_compose_pixels(context, shader);
		return;
	}
	CGContextBeginTransparencyLayer(context, NULL);
	if (shader->dst) {
		CGContextSaveGState(context);
		draw_shader(context, shader->dst, live);
		CGContextRestoreGState(context);
	}
	int mode = shader->mode;
	if (shader->src && mode != GFX_BLEND_DST) {
		CGContextSaveGState(context);
		CGContextSetBlendMode(context, cg_blend(mode));
		draw_shader(context, shader->src, live);
		CGContextRestoreGState(context);
	}
	CGContextEndTransparencyLayer(context);
}

// fills the clip, the shader's local matrix applied in it
static void draw_shader(CGContextRef context, const struct gfx_shader *shader, bool live)
{
	CGContextConcatCTM(context, affine(&shader->matrix));
	if (shader->kind == SHADER_BITMAP) {
		draw_bitmap_shader(context, shader, live);
		return;
	}
	if (shader->kind == SHADER_COMPOSE) {
		draw_compose(context, shader, live);
		return;
	}
	CGRect box = CGContextGetClipBoundingBox(context);
	float t_min, t_max;
	shader_t_range(shader, &(struct gfx_rect){CGRectGetMinX(box), CGRectGetMinY(box), CGRectGetMaxX(box), CGRectGetMaxY(box)}, &t_min, &t_max);
	int k0, k1;
	uint32_t *colors;
	float *positions;
	size_t n = shader_stops(shader, t_min, t_max, &k0, &k1, &colors, &positions);
	CGFloat *components = malloc(n * 4 * sizeof(*components)), *locations = malloc(n * sizeof(*locations));
	for (size_t i = 0; i < n; i++) {
		components[i * 4] = ((colors[i] >> 16) & 0xff) / 255.;
		components[i * 4 + 1] = ((colors[i] >> 8) & 0xff) / 255.;
		components[i * 4 + 2] = (colors[i] & 0xff) / 255.;
		components[i * 4 + 3] = (colors[i] >> 24) / 255.;
		locations[i] = positions[i];
	}
	CGGradientRef gradient = CGGradientCreateWithColorComponents(cg_srgb(), components, locations, n);
	const CGGradientDrawingOptions extend = kCGGradientDrawsBeforeStartLocation | kCGGradientDrawsAfterEndLocation;
	CGPoint center = CGPointMake(shader->x0, shader->y0);
	if (shader->kind == SHADER_LINEAR) {
		float dx = shader->x1 - shader->x0, dy = shader->y1 - shader->y0;
		CGContextDrawLinearGradient(context, gradient, CGPointMake(shader->x0 + dx * k0, shader->y0 + dy * k0),
		                            CGPointMake(shader->x0 + dx * k1, shader->y0 + dy * k1), extend);
	} else if (shader->kind == SHADER_RADIAL) {
		CGContextDrawRadialGradient(context, gradient, center, shader->radius * k0, center, shader->radius * k1, extend);
	} else {
		CGContextDrawConicGradient(context, gradient, center, 0); // 0 on the right, toward +y: clockwise here
	}
	CGGradientRelease(gradient);
	free(locations);
	free(components);
	free(positions);
	free(colors);
}

/* The shader's colors at the paint's alpha through its color filter, in the clip: per pixel at the
 * context's resolution, then drawn back pixel for pixel (Skia's order: shader, alpha, filter, shape). */
static void draw_filtered_shader(CGContextRef context, const struct gfx_paint *paint, bool live)
{
	CGAffineTransform ctm;
	CGRect device;
	CGContextRef offscreen = device_offscreen(context, &ctm, &device);
	if (!offscreen)
		return;
	size_t width = device.size.width, height = device.size.height;
	draw_shader(offscreen, paint->shader, live);
	uint32_t *pixels = CGBitmapContextGetData(offscreen);
	size_t row_words = CGBitmapContextGetBytesPerRow(offscreen) / 4;
	float alpha = (paint->color >> 24) / 255.f;
	for (size_t y = 0; y < height; y++)
		for (size_t x = 0; x < width; x++)
			pixels[y * row_words + x] = blend_filter_pixel(&paint->filter, pixels[y * row_words + x], alpha);
	draw_offscreen(context, offscreen, ctm, device);
	CGContextRelease(offscreen);
}

// the context's path as a shape the shader fills: the path filled, or its stroke's outline
static void shade(CGContextRef context, CGPathRef path, bool even_odd, bool stroke, const struct gfx_paint *paint, bool live)
{
	CGContextSaveGState(context);
	CGContextAddPath(context, path);
	if (stroke)
		CGContextReplacePathWithStrokedPath(context);
	if (even_odd && !stroke)
		CGContextEOClip(context);
	else
		CGContextClip(context);
	if (gfx_color_filter_is_set(&paint->filter) && paint->filter.mode != GFX_BLEND_DST) {
		draw_filtered_shader(context, paint, live);
	} else {
		CGContextSetAlpha(context, (paint->color >> 24) / 255.);
		draw_shader(context, paint->shader, live);
	}
	CGContextRestoreGState(context);
}

static void stroke_path(CGContextRef context, CGPathRef path, const struct gfx_paint *paint, bool live)
{
	if (paint->shader) {
		shade(context, path, false, true, paint, live);
		return;
	}
	CGContextAddPath(context, path);
	CGContextStrokePath(context);
}

static void draw_path(CGContextRef context, CGPathRef path, bool even_odd, const struct gfx_paint *paint, bool live)
{
	if (paint->style != GFX_PAINT_STROKE) {
		if (paint->shader) {
			shade(context, path, even_odd, false, paint, live);
		} else {
			CGContextAddPath(context, path);
			if (even_odd)
				CGContextEOFillPath(context);
			else
				CGContextFillPath(context);
		}
	}
	if (paint->style != GFX_PAINT_FILL)
		stroke_path(context, path, paint, live);
}

static CGPathRef cg_path(const struct gfx_path *path, const CGAffineTransform *transform)
{
	CGMutablePathRef cg = CGPathCreateMutable();
	const float *p = path->points;
	for (size_t i = 0; i < path->n_verbs; i++) {
		switch (path->verbs[i]) {
		case PATH_MOVE: CGPathMoveToPoint(cg, transform, p[0], p[1]); break;
		case PATH_LINE: CGPathAddLineToPoint(cg, transform, p[0], p[1]); break;
		case PATH_QUAD: CGPathAddQuadCurveToPoint(cg, transform, p[0], p[1], p[2], p[3]); break;
		case PATH_CUBIC: CGPathAddCurveToPoint(cg, transform, p[0], p[1], p[2], p[3], p[4], p[5]); break;
		case PATH_CLOSE: CGPathCloseSubpath(cg); break;
		}
		p += path_verb_points(path->verbs[i]) * 2;
	}
	return cg;
}

// the context is in px: the line, laid out in points, scales back to them
static void draw_text(CGContextRef context, const struct op *op, CGFloat scale)
{
	if (op->paint.text_size <= 0) // CoreText's default size; Android draws nothing
		return;
	CTLineRef line = cg_text_line(&op->paint, scale, op->text, op->text_len);
	CGFloat width = CTLineGetTypographicBounds(line, NULL, NULL, NULL);
	static const CGTextDrawingMode modes[] = {[GFX_PAINT_FILL] = kCGTextFill, [GFX_PAINT_STROKE] = kCGTextStroke, [GFX_PAINT_FILL_AND_STROKE] = kCGTextFillStroke};
	CGContextSetTextDrawingMode(context, modes[op->paint.style]);
	CGContextSetLineWidth(context, (op->paint.stroke_width > 0 ? op->paint.stroke_width : 1) / scale);
	CGContextTranslateCTM(context, op->args[0], op->args[1]);
	CGContextScaleCTM(context, scale, scale);
	CGContextSetTextMatrix(context, CGAffineTransformMakeScale(1, -1)); // glyphs are y up
	// Paint.Align: LEFT, CENTER, RIGHT
	CGContextSetTextPosition(context, -width * op->paint.text_align / 2, 0);
	CTLineDraw(line, context);
	CFRelease(line);
}

/* the bitmap op's image through its color filter at its alpha; on UIKit's thread from a cache on the
 * op, which only the bitmap's image lock guards */
static CGImageRef bitmap_op_image(const struct op *op, bool live, float *alpha)
{
	unsigned generation;
	CGImageRef image = cg_bitmap_image(op->bitmap, live, &generation);
	if (!image || !gfx_color_filter_is_set(&op->paint.filter) || op->paint.filter.mode == GFX_BLEND_DST || op->bitmap->config == GFX_BITMAP_ALPHA_8)
		return image; // an ALPHA_8 one's filter is the paint color's (set_color)
	float paint_alpha = *alpha;
	*alpha = 1;
	struct op *cached = (struct op *)op;
	struct gfx_bitmap *bitmap = op->bitmap;
	CGImageRef filtered = NULL;
	if (!live) {
		pthread_mutex_lock(&bitmap->image_lock);
		if (cached->cache && cached->cache_generation == generation)
			filtered = CGImageRetain(cached->cache);
		pthread_mutex_unlock(&bitmap->image_lock);
	}
	if (!filtered) {
		filtered = filtered_image(image, &op->paint.filter, paint_alpha);
		if (!live) {
			pthread_mutex_lock(&bitmap->image_lock);
			CGImageRef old = cached->cache;
			cached->cache = (void *)CGImageRetain(filtered);
			cached->cache_generation = generation;
			pthread_mutex_unlock(&bitmap->image_lock);
			if (old)
				CGImageRelease(old);
		}
	}
	CGImageRelease(image);
	return filtered;
}

void record_op_cache_free(void *cache)
{
	CGImageRelease(cache);
}

static void draw_op(CGContextRef context, const struct op *op, CGFloat scale, bool live)
{
	const float *a = op->args;
	CGRect rect = CGRectStandardize(CGRectMake(a[0], a[1], a[2] - a[0], a[3] - a[1]));
	switch (op->kind) {
	case OP_RECT: {
		CGPathRef path = CGPathCreateWithRect(rect, NULL);
		draw_path(context, path, false, &op->paint, live);
		CGPathRelease(path);
		break;
	}
	case OP_ROUND_RECT: {
		// CG asserts on corners past the middle
		CGFloat rx = fmin(fmax(a[4], 0), rect.size.width / 2), ry = fmin(fmax(a[5], 0), rect.size.height / 2);
		CGPathRef path = CGPathCreateWithRoundedRect(rect, rx, ry, NULL);
		draw_path(context, path, false, &op->paint, live);
		CGPathRelease(path);
		break;
	}
	case OP_LINE: { // stroked whatever the style
		if (!op->paint.shader) {
			CGContextStrokeLineSegments(context, (CGPoint[]){CGPointMake(a[0], a[1]), CGPointMake(a[2], a[3])}, 2);
			break;
		}
		CGMutablePathRef path = CGPathCreateMutable();
		CGPathMoveToPoint(path, NULL, a[0], a[1]);
		CGPathAddLineToPoint(path, NULL, a[2], a[3]);
		stroke_path(context, path, &op->paint, live);
		CGPathRelease(path);
		break;
	}
	case OP_PATH: {
		CGPathRef path = cg_path(op->path, NULL);
		draw_path(context, path, a[0], &op->paint, live);
		CGPathRelease(path);
		break;
	}
	case OP_ARC: {
		struct gfx_path *arc = path_new();
		path_add_draw_arc(arc, a[0], a[1], a[2], a[3], a[4], a[5], a[6]);
		CGPathRef path = cg_path(arc, NULL);
		draw_path(context, path, false, &op->paint, live);
		CGPathRelease(path);
		path_unref(arc);
		break;
	}
	case OP_CLIP:
	case OP_CLIP_PATH:
	case OP_UNCLIP:
	case OP_LAYER:
	case OP_UNLAYER:
		break; // cg_list_draw's and the painter's, as they outlive the op
	case OP_BITMAP: {
		float alpha = (op->paint.color >> 24) / 255.f;
		CGImageRef image = bitmap_op_image(op, live, &alpha);
		if (!image || a[2] == a[0] || a[3] == a[1]) {
			if (image)
				CGImageRelease(image);
			break;
		}
		CGRect dst = CGRectStandardize(CGRectMake(a[4], a[5], a[6] - a[4], a[7] - a[5]));
		CGFloat xscale = dst.size.width / (a[2] - a[0]), yscale = dst.size.height / (a[3] - a[1]);
		CGRect whole = CGRectMake(dst.origin.x - a[0] * xscale, dst.origin.y - a[1] * yscale,
		                          CGImageGetWidth(image) * xscale, CGImageGetHeight(image) * yscale);
		CGContextClipToRect(context, dst);
		bool mask = op->bitmap->config == GFX_BITMAP_ALPHA_8; // its alpha in the paint's shader or color
		if (mask)
			CGContextBeginTransparencyLayerWithRect(context, dst, NULL);
		else
			CGContextSetAlpha(context, alpha);
		CGContextSaveGState(context);
		// CGContextDrawImage draws y up; the layer's context is y down
		CGContextTranslateCTM(context, 0, CGRectGetMaxY(whole) + whole.origin.y);
		CGContextScaleCTM(context, 1, -1);
		CGContextDrawImage(context, whole, image);
		CGContextRestoreGState(context);
		if (mask) { // the shader's colors at the paint's alpha through its filter, else set_color's, where the alpha is
			CGContextSaveGState(context);
			CGContextSetBlendMode(context, kCGBlendModeSourceIn);
			if (op->paint.shader && gfx_color_filter_is_set(&op->paint.filter) && op->paint.filter.mode != GFX_BLEND_DST) {
				draw_filtered_shader(context, &op->paint, live);
			} else if (op->paint.shader) {
				CGContextSetAlpha(context, (op->paint.color >> 24) / 255.);
				draw_shader(context, op->paint.shader, live);
			} else {
				CGContextFillRect(context, dst);
			}
			CGContextRestoreGState(context);
			CGContextEndTransparencyLayer(context);
		}
		CGImageRelease(image);
		break;
	}
	case OP_TEXT:
		draw_text(context, op, scale);
		break;
	}
}

// the rect or path under the op's matrix, in the context's space; DIFFERENCE keeps what's outside it
void cg_clip(CGContextRef context, const struct op *op)
{
	const float *a = op->args;
	CGAffineTransform transform = affine(&op->matrix);
	CGPathRef path;
	bool even_odd = false, difference;
	if (op->kind == OP_CLIP_PATH) {
		path = cg_path(op->path, &transform);
		even_odd = a[0];
		difference = a[1] == GFX_CLIP_DIFFERENCE;
	} else {
		path = CGPathCreateWithRect(CGRectStandardize(CGRectMake(a[0], a[1], a[2] - a[0], a[3] - a[1])), &transform);
		difference = a[4] == GFX_CLIP_DIFFERENCE;
	}
	if (difference) { // even-odd against the clip's box: exact for a path that doesn't overlap itself
		CGContextAddRect(context, CGContextGetClipBoundingBox(context));
		CGContextAddPath(context, path);
		CGContextEOClip(context);
	} else {
		CGContextAddPath(context, path);
		if (even_odd)
			CGContextEOClip(context);
		else
			CGContextClip(context);
	}
	CGPathRelease(path);
}


// a gstate, and a transparency layer in it, that the level's restore ends
static void cg_begin_layer(CGContextRef context, const struct op *op)
{
	const float *a = op->args;
	CGContextSaveGState(context);
	CGRect bounds = CGRectInfinite;
	if (a[4]) {
		bounds = CGRectApplyAffineTransform(CGRectStandardize(CGRectMake(a[0], a[1], a[2] - a[0], a[3] - a[1])), affine(&op->matrix));
		CGContextClipToRect(context, bounds);
	}
	int mode = a[6];
	CGContextSetAlpha(context, mode == GFX_BLEND_DST ? 0 : a[5] / 255.); // DST keeps the destination
	CGContextSetBlendMode(context, cg_blend(mode));
	CGContextBeginTransparencyLayerWithRect(context, a[4] ? bounds : CGContextGetClipBoundingBox(context), NULL);
}

// a clip's or layer's gstate, its layer ended first
static void cg_pop(CGContextRef context, bool layer)
{
	if (layer)
		CGContextEndTransparencyLayer(context);
	CGContextRestoreGState(context);
}

void cg_draw(CGContextRef context, const struct op *op, CGFloat scale, bool live)
{
	if (op->paint.blend == GFX_BLEND_DST)
		return;
	CGContextSaveGState(context);
	CGContextConcatCTM(context, affine(&op->matrix));
	CGContextSetBlendMode(context, cg_blend(op->paint.blend));
	set_color(context, &op->paint);
	set_stroke(context, &op->paint);
	draw_op(context, op, scale, live);
	CGContextRestoreGState(context);
}

static void publish_shader(const struct gfx_shader *shader)
{
	if (!shader)
		return;
	if (shader->bitmap)
		cg_bitmap_publish(shader->bitmap);
	publish_shader(shader->dst);
	publish_shader(shader->src);
}

void cg_list_publish(const struct gfx_canvas *list)
{
	for (size_t i = 0; i < list->n_ops; i++) {
		if (list->ops[i].bitmap)
			cg_bitmap_publish(list->ops[i].bitmap);
		publish_shader(list->ops[i].paint.shader);
	}
}

void cg_list_draw(const struct gfx_canvas *list, CGContextRef context, CGFloat scale)
{
	CGContextSaveGState(context);
	CGContextScaleCTM(context, 1 / scale, 1 / scale);
	// what clips and layers pushed (true: a layer), ended last first by OP_UNCLIP, OP_UNLAYER or the end
	bool *pushes = NULL;
	size_t n_pushes = 0, pushes_size = 0;
	for (size_t i = 0; i < list->n_ops; i++) {
		const struct op *op = &list->ops[i];
		bool layer = op->kind == OP_LAYER;
		if (op->kind == OP_CLIP || op->kind == OP_CLIP_PATH || layer) {
			if (n_pushes == pushes_size)
				pushes = realloc(pushes, (pushes_size = pushes_size ? pushes_size * 2 : 8) * sizeof(*pushes));
			pushes[n_pushes++] = layer;
			if (layer) {
				cg_begin_layer(context, op);
			} else {
				CGContextSaveGState(context);
				cg_clip(context, op);
			}
			continue;
		}
		if (op->kind == OP_UNCLIP || op->kind == OP_UNLAYER) {
			for (int n = op->kind == OP_UNLAYER ? 1 : op->args[0]; n > 0 && n_pushes; n--, n_pushes--)
				cg_pop(context, pushes[n_pushes - 1]);
			continue;
		}
		cg_draw(context, op, scale, false);
	}
	while (n_pushes--)
		cg_pop(context, pushes[n_pushes]);
	free(pushes);
	CGContextRestoreGState(context);
}
