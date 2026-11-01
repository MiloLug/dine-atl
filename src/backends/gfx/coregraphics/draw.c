#include <math.h>
#include <stdlib.h>

#include <graphene.h>

#include "src/shared/path.h"
#include "src/shared/shader.h"

#include "../record/record.h"
#include "coregraphics.h"

struct gfx_canvas *cg_list_ref(struct gfx_canvas *list)
{
	return record_canvas_ref(list);
}

struct color {
	float a, r, g, b; // premultiplied
};

static struct color unpack(uint32_t argb)
{
	float a = (argb >> 24) / 255.f;
	return (struct color){a, ((argb >> 16) & 0xff) / 255.f * a, ((argb >> 8) & 0xff) / 255.f * a, (argb & 0xff) / 255.f * a};
}

// one premultiplied channel of PorterDuff mode `mode`, the filter's color over the paint's
static float porter_duff(int mode, float sa, float sc, float da, float dc)
{
	switch (mode) {
	case 0: return 0;                                     // CLEAR
	case 1: return sc;                                    // SRC
	case 2: return dc;                                    // DST
	case 3: return sc + dc * (1 - sa);                    // SRC_OVER
	case 4: return dc + sc * (1 - da);                    // DST_OVER
	case 5: return sc * da;                               // SRC_IN
	case 6: return dc * sa;                               // DST_IN
	case 7: return sc * (1 - da);                         // SRC_OUT
	case 8: return dc * (1 - sa);                         // DST_OUT
	case 9: return sc * da + dc * (1 - sa);               // SRC_ATOP
	case 10: return dc * sa + sc * (1 - da);              // DST_ATOP
	case 11: return sc * (1 - da) + dc * (1 - sa);        // XOR
	case 12: return sc + dc - fmaxf(sc * da, dc * sa);  // DARKEN
	case 13: return sc + dc - fminf(sc * da, dc * sa);  // LIGHTEN
	case 14: return sc * dc;                              // MULTIPLY
	case 15: return sc + dc - sc * dc;                    // SCREEN
	case 16: return fminf(1, sc + dc);                    // ADD
	case 17:                                              // OVERLAY
		return sc * (1 - da) + dc * (1 - sa) + (2 * dc <= da ? 2 * sc * dc : sa * da - 2 * (da - dc) * (sa - sc));
	default: return dc;
	}
}

// a solid paint's color through its color filter
static void set_color(CGContextRef context, const struct gfx_paint *paint)
{
	struct color d = unpack(paint->color);
	if (paint->filter_mode != -1) {
		struct color s = unpack(paint->filter_color);
		int mode = paint->filter_mode;
		// the alpha of the separable modes but MULTIPLY's (Skia's modulate) is SRC_OVER's
		float a = mode <= 11 ? porter_duff(mode, s.a, s.a, d.a, d.a) : mode == 14 ? s.a * d.a : mode == 16 ? fminf(1, s.a + d.a) : s.a + d.a - s.a * d.a;
		d = (struct color){a, porter_duff(mode, s.a, s.r, d.a, d.r), porter_duff(mode, s.a, s.g, d.a, d.g), porter_duff(mode, s.a, s.b, d.a, d.b)};
	}
	float r = d.a ? d.r / d.a : 0, g = d.a ? d.g / d.a : 0, b = d.a ? d.b / d.a : 0;
	CGContextSetRGBFillColor(context, r, g, b, d.a);
	CGContextSetRGBStrokeColor(context, r, g, b, d.a);
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

// fills the clip: the shader's local matrix applies in it, and the paint's alpha over it
static void draw_shader(CGContextRef context, const struct gfx_shader *shader, const struct gfx_paint *paint)
{
	CGContextConcatCTM(context, affine(&shader->matrix));
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
	CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
	CGGradientRef gradient = CGGradientCreateWithColorComponents(space, components, locations, n);
	CGContextSetAlpha(context, (paint->color >> 24) / 255.);
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
	CGColorSpaceRelease(space);
	free(locations);
	free(components);
	free(positions);
	free(colors);
}

// the context's path as a shape the shader fills: the path filled, or its stroke's outline
static void shade(CGContextRef context, CGPathRef path, bool even_odd, bool stroke, const struct gfx_paint *paint)
{
	CGContextSaveGState(context);
	CGContextAddPath(context, path);
	if (stroke)
		CGContextReplacePathWithStrokedPath(context);
	if (even_odd && !stroke)
		CGContextEOClip(context);
	else
		CGContextClip(context);
	draw_shader(context, paint->shader, paint);
	CGContextRestoreGState(context);
}

static void stroke_path(CGContextRef context, CGPathRef path, const struct gfx_paint *paint)
{
	if (paint->shader) {
		shade(context, path, false, true, paint);
		return;
	}
	CGContextAddPath(context, path);
	CGContextStrokePath(context);
}

static void draw_path(CGContextRef context, CGPathRef path, bool even_odd, const struct gfx_paint *paint)
{
	if (paint->style != GFX_PAINT_STROKE) {
		if (paint->shader) {
			shade(context, path, even_odd, false, paint);
		} else {
			CGContextAddPath(context, path);
			if (even_odd)
				CGContextEOFillPath(context);
			else
				CGContextFillPath(context);
		}
	}
	if (paint->style != GFX_PAINT_FILL)
		stroke_path(context, path, paint);
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

static void draw_op(CGContextRef context, const struct op *op, CGFloat scale)
{
	const float *a = op->args;
	CGRect rect = CGRectStandardize(CGRectMake(a[0], a[1], a[2] - a[0], a[3] - a[1]));
	switch (op->kind) {
	case OP_RECT: {
		CGPathRef path = CGPathCreateWithRect(rect, NULL);
		draw_path(context, path, false, &op->paint);
		CGPathRelease(path);
		break;
	}
	case OP_ROUND_RECT: {
		// CG asserts on corners past the middle
		CGFloat rx = fmin(fmax(a[4], 0), rect.size.width / 2), ry = fmin(fmax(a[5], 0), rect.size.height / 2);
		CGPathRef path = CGPathCreateWithRoundedRect(rect, rx, ry, NULL);
		draw_path(context, path, false, &op->paint);
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
		stroke_path(context, path, &op->paint);
		CGPathRelease(path);
		break;
	}
	case OP_PATH: {
		CGPathRef path = cg_path(op->path, NULL);
		draw_path(context, path, a[0], &op->paint);
		CGPathRelease(path);
		break;
	}
	case OP_ARC: {
		struct gfx_path *arc = path_new();
		path_add_draw_arc(arc, a[0], a[1], a[2], a[3], a[4], a[5], a[6]);
		CGPathRef path = cg_path(arc, NULL);
		draw_path(context, path, false, &op->paint);
		CGPathRelease(path);
		path_unref(arc);
		break;
	}
	case OP_CLIP:
	case OP_CLIP_PATH:
	case OP_UNCLIP:
	case OP_LAYER:
	case OP_UNLAYER:
		break; // cg_list_draw's, as they outlive the op
	case OP_BITMAP: {
		CGImageRef image = cg_bitmap_image(op->bitmap);
		if (!image || a[2] == a[0] || a[3] == a[1])
			break;
		CGRect dst = CGRectStandardize(CGRectMake(a[4], a[5], a[6] - a[4], a[7] - a[5]));
		CGFloat xscale = dst.size.width / (a[2] - a[0]), yscale = dst.size.height / (a[3] - a[1]);
		CGRect whole = CGRectMake(dst.origin.x - a[0] * xscale, dst.origin.y - a[1] * yscale,
		                          CGImageGetWidth(image) * xscale, CGImageGetHeight(image) * yscale);
		CGContextClipToRect(context, dst);
		CGContextSetAlpha(context, (op->paint.color >> 24) / 255.);
		// CGContextDrawImage draws y up; the layer's context is y down
		CGContextTranslateCTM(context, 0, CGRectGetMaxY(whole) + whole.origin.y);
		CGContextScaleCTM(context, 1, -1);
		CGContextDrawImage(context, whole, image);
		break;
	}
	case OP_TEXT:
		draw_text(context, op, scale);
		break;
	}
}

// the rect or path under the op's matrix, in the context's space; DIFFERENCE keeps what's outside it
static void clip(CGContextRef context, const struct op *op)
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

// PorterDuff.Mode's nativeInt; DST keeps the destination: the layer draws at alpha 0
static const CGBlendMode blend_modes[] = {
	kCGBlendModeClear, kCGBlendModeCopy, kCGBlendModeNormal, kCGBlendModeNormal, kCGBlendModeDestinationOver,
	kCGBlendModeSourceIn, kCGBlendModeDestinationIn, kCGBlendModeSourceOut, kCGBlendModeDestinationOut,
	kCGBlendModeSourceAtop, kCGBlendModeDestinationAtop, kCGBlendModeXOR, kCGBlendModeDarken, kCGBlendModeLighten,
	kCGBlendModeMultiply, kCGBlendModeScreen, kCGBlendModePlusLighter, kCGBlendModeOverlay,
};

// a gstate, and a transparency layer in it, that the level's restore ends
static void begin_layer(CGContextRef context, const struct op *op)
{
	const float *a = op->args;
	CGContextSaveGState(context);
	CGRect bounds = CGRectInfinite;
	if (a[4]) {
		bounds = CGRectApplyAffineTransform(CGRectStandardize(CGRectMake(a[0], a[1], a[2] - a[0], a[3] - a[1])), affine(&op->matrix));
		CGContextClipToRect(context, bounds);
	}
	int mode = a[6];
	CGContextSetAlpha(context, mode == 2 ? 0 : a[5] / 255.);
	if (mode >= 0 && mode < (int)(sizeof(blend_modes) / sizeof(*blend_modes)))
		CGContextSetBlendMode(context, blend_modes[mode]);
	CGContextBeginTransparencyLayerWithRect(context, a[4] ? bounds : CGContextGetClipBoundingBox(context), NULL);
}

// a clip's or layer's gstate, its layer ended first
static void pop(CGContextRef context, bool layer)
{
	if (layer)
		CGContextEndTransparencyLayer(context);
	CGContextRestoreGState(context);
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
				begin_layer(context, op);
			} else {
				CGContextSaveGState(context);
				clip(context, op);
			}
			continue;
		}
		if (op->kind == OP_UNCLIP || op->kind == OP_UNLAYER) {
			for (int n = op->kind == OP_UNLAYER ? 1 : op->args[0]; n > 0 && n_pushes; n--, n_pushes--)
				pop(context, pushes[n_pushes - 1]);
			continue;
		}
		CGContextSaveGState(context);
		CGContextConcatCTM(context, affine(&op->matrix));
		set_color(context, &op->paint);
		set_stroke(context, &op->paint);
		draw_op(context, op, scale);
		CGContextRestoreGState(context);
	}
	while (n_pushes--)
		pop(context, pushes[n_pushes]);
	free(pushes);
	CGContextRestoreGState(context);
}
