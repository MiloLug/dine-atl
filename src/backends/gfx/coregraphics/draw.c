#include <math.h>

#include <graphene.h>

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

static void draw_path(CGContextRef context, CGPathRef path, const struct gfx_paint *paint)
{
	if (paint->style != GFX_PAINT_STROKE) {
		CGContextAddPath(context, path);
		CGContextFillPath(context);
	}
	if (paint->style != GFX_PAINT_FILL) {
		CGContextAddPath(context, path);
		CGContextStrokePath(context);
	}
}

static void draw_op(CGContextRef context, const struct op *op)
{
	const float *a = op->args;
	CGRect rect = CGRectStandardize(CGRectMake(a[0], a[1], a[2] - a[0], a[3] - a[1]));
	switch (op->kind) {
	case OP_RECT: {
		CGPathRef path = CGPathCreateWithRect(rect, NULL);
		draw_path(context, path, &op->paint);
		CGPathRelease(path);
		break;
	}
	case OP_ROUND_RECT: {
		// CG asserts on corners past the middle
		CGFloat rx = fmin(fmax(a[4], 0), rect.size.width / 2), ry = fmin(fmax(a[5], 0), rect.size.height / 2);
		CGPathRef path = CGPathCreateWithRoundedRect(rect, rx, ry, NULL);
		draw_path(context, path, &op->paint);
		CGPathRelease(path);
		break;
	}
	case OP_LINE: { // stroked whatever the style
		CGPoint points[2] = {CGPointMake(a[0], a[1]), CGPointMake(a[2], a[3])};
		CGContextStrokeLineSegments(context, points, 2);
		break;
	}
	case OP_CLIP:
	case OP_UNCLIP:
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
	}
}

// the rect under the op's matrix, in the context's space; DIFFERENCE keeps what's outside it
static void clip(CGContextRef context, const struct op *op)
{
	const float *a = op->args;
	CGAffineTransform transform = affine(&op->matrix);
	CGPathRef path = CGPathCreateWithRect(CGRectStandardize(CGRectMake(a[0], a[1], a[2] - a[0], a[3] - a[1])), &transform);
	if (a[4] == GFX_CLIP_DIFFERENCE) {
		CGContextAddRect(context, CGContextGetClipBoundingBox(context));
		CGContextAddPath(context, path);
		CGContextEOClip(context);
	} else {
		CGContextAddPath(context, path);
		CGContextClip(context);
	}
	CGPathRelease(path);
}

void cg_list_draw(const struct gfx_canvas *list, CGContextRef context, CGFloat scale)
{
	CGContextSaveGState(context);
	CGContextScaleCTM(context, 1 / scale, 1 / scale);
	int clips = 0; // the clips' gstates, each restored by an OP_UNCLIP or at the end
	for (size_t i = 0; i < list->n_ops; i++) {
		const struct op *op = &list->ops[i];
		if (op->kind == OP_CLIP) {
			CGContextSaveGState(context);
			clip(context, op);
			clips++;
			continue;
		}
		if (op->kind == OP_UNCLIP) {
			for (int n = op->args[0]; n > 0 && clips; n--, clips--)
				CGContextRestoreGState(context);
			continue;
		}
		CGContextSaveGState(context);
		CGContextConcatCTM(context, affine(&op->matrix));
		set_color(context, &op->paint);
		set_stroke(context, &op->paint);
		draw_op(context, op);
		CGContextRestoreGState(context);
	}
	while (clips--)
		CGContextRestoreGState(context);
	CGContextRestoreGState(context);
}
