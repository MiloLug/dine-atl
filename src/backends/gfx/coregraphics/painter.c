#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/bitmap.h"
#include "src/shared/blend.h"
#include "src/shared/path.h"

#include "../record/record.h"
#include "coregraphics.h"

/* A Canvas(Bitmap) on CoreGraphics: ops draw at once into a CGBitmapContext over the words, y down.
 * A layer is a context of its own so every destination is readable, and the modes CG lacks
 * (cg_blend_exact) blend per pixel. CG can't hand a clip on: each level keeps its clip ops to replay. */

struct target {
	CGContextRef context;
	uint32_t *words; // the device rect x, y, width x height, rows width words apart
	int x, y, width, height;
	struct op layer; // an emulated layer's OP_LAYER; the bitmap's: unused
};

struct level {
	bool layer;      // it pushed a target
	struct op *clips; // its OP_CLIP and OP_CLIP_PATH ops, paths held
	size_t n_clips, clips_size;
};

struct painter {
	struct gfx_bitmap *bitmap; // held
	struct target *targets;    // [0]: the bitmap's
	size_t n_targets, targets_size;
	struct level *levels; // [0]: the base level
	size_t n_levels, levels_size;
};

// a context over words for the device rect, y down, its user space the device's
static CGContextRef context_over(uint32_t *words, int x, int y, int width, int height)
{
	CGContextRef context = CGBitmapContextCreate(words, width, height, 8, width * 4, cg_srgb(), kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
	if (context) {
		CGContextTranslateCTM(context, 0, height);
		CGContextScaleCTM(context, 1, -1);
		CGContextTranslateCTM(context, -x, -y);
	}
	return context;
}

static struct target *top(struct painter *painter)
{
	return &painter->targets[painter->n_targets - 1];
}

static void push_target(struct painter *painter, struct target target)
{
	if (painter->n_targets == painter->targets_size) {
		painter->targets_size = painter->targets_size ? painter->targets_size * 2 : 4;
		painter->targets = realloc(painter->targets, painter->targets_size * sizeof(*painter->targets));
	}
	painter->targets[painter->n_targets++] = target;
}

static void push_level(struct painter *painter, bool layer)
{
	if (painter->n_levels == painter->levels_size) {
		painter->levels_size = painter->levels_size ? painter->levels_size * 2 : 4;
		painter->levels = realloc(painter->levels, painter->levels_size * sizeof(*painter->levels));
	}
	painter->levels[painter->n_levels++] = (struct level){layer};
}

struct painter *painter_new(struct gfx_bitmap *bitmap)
{
	struct painter *painter = calloc(1, sizeof(*painter));
	gfx_bitmap_ref(bitmap);
	painter->bitmap = bitmap;
	push_target(painter, (struct target){context_over(bitmap->words, 0, 0, bitmap->width, bitmap->height), bitmap->words, 0, 0, bitmap->width, bitmap->height});
	push_level(painter, false);
	return painter;
}

void painter_free(struct painter *painter)
{
	while (painter->n_levels > 1)
		painter_restore(painter);
	for (size_t i = 0; i < painter->levels[0].n_clips; i++)
		path_unref(painter->levels[0].clips[i].path);
	free(painter->levels[0].clips);
	if (painter->targets[0].context)
		CGContextRelease(painter->targets[0].context);
	free(painter->targets);
	free(painter->levels);
	gfx_bitmap_unref(painter->bitmap);
	free(painter);
}

static bool invertible(const graphene_matrix_t *matrix)
{
	float a = graphene_matrix_get_value(matrix, 0, 0), b = graphene_matrix_get_value(matrix, 0, 1);
	float c = graphene_matrix_get_value(matrix, 1, 0), d = graphene_matrix_get_value(matrix, 1, 1);
	return a * d - b * c != 0 && isfinite(a * d - b * c);
}

static CGAffineTransform affine(const graphene_matrix_t *m)
{
	return CGAffineTransformMake(graphene_matrix_get_value(m, 0, 0), graphene_matrix_get_value(m, 0, 1), graphene_matrix_get_value(m, 1, 0),
	                             graphene_matrix_get_value(m, 1, 1), graphene_matrix_get_value(m, 3, 0), graphene_matrix_get_value(m, 3, 1));
}

// what a singular matrix maps to nothing: INTERSECT empties the clip, DIFFERENCE keeps it
static void apply_clip(CGContextRef context, const struct op *op)
{
	if (invertible(&op->matrix))
		cg_clip(context, op);
	else if ((op->kind == OP_CLIP ? op->args[4] : op->args[1]) != GFX_CLIP_DIFFERENCE)
		CGContextClipToRect(context, CGRectZero);
}

// the clips of levels [0, n) into a context
static void replay_clips(struct painter *painter, CGContextRef context, size_t n)
{
	for (size_t l = 0; l < n; l++)
		for (size_t i = 0; i < painter->levels[l].n_clips; i++)
			apply_clip(context, &painter->levels[l].clips[i]);
}

// a transparent context of the device rect with the clips of levels [0, n)
static CGContextRef scratch(struct painter *painter, CGRect device, size_t n, uint32_t **words)
{
	*words = calloc((size_t)device.size.width * device.size.height ?: 1, 4);
	CGContextRef context = context_over(*words, device.origin.x, device.origin.y, device.size.width, device.size.height);
	if (context)
		replay_clips(painter, context, n);
	return context;
}

// rounds what changed in the bitmap, the op's part of the clip's box (NULL: all of it), and counts the change
static void changed(struct painter *painter, const struct op *op)
{
	if (painter->n_targets > 1)
		return;
	CGRect box = CGContextGetClipBoundingBox(top(painter)->context);
	struct gfx_rect bounds;
	if (op && record_op_bounds(op, &bounds))
		box = CGRectIntersection(box, CGRectApplyAffineTransform(CGRectMake(bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top), affine(&op->matrix)));
	if (CGRectIsNull(box) || CGRectIsEmpty(box)) {
		bitmap_changed(painter->bitmap);
		return;
	}
	const CGFloat margin = 2; // antialiasing
	bitmap_round(painter->bitmap, floor(CGRectGetMinX(box) - margin), floor(CGRectGetMinY(box) - margin), ceil(CGRectGetMaxX(box) + margin), ceil(CGRectGetMaxY(box) + margin));
}

/* lerp(d, mode(s, d), m) per pixel of the target's region, as Skia blends at a coverage: m the
 * coverage words' alpha; s the content words, which hold a paint's draw at its coverage (shaped) or
 * a layer's pixels, taken at alpha */
static void blend_region(struct target *target, CGRect device, const uint32_t *content, const uint32_t *coverage, bool shaped, float alpha, enum gfx_blend mode)
{
	int width = device.size.width, height = device.size.height;
	for (int y = 0; y < height; y++) {
		uint32_t *row = target->words + (size_t)(device.origin.y - target->y + y) * target->width + (int)device.origin.x - target->x;
		for (int x = 0; x < width; x++) {
			float m = (coverage[(size_t)y * width + x] >> 24) / 255.f;
			if (!m)
				continue;
			struct blend_color c = blend_from_word(content[(size_t)y * width + x]), d = blend_from_word(row[x]);
			float k = shaped ? 1 / m : alpha;
			struct blend_color s = {c.a * k, c.r * k, c.g * k, c.b * k};
			row[x] = blend_to_word(blend_mix(blend(mode, s, d), m, d, 1 - m));
		}
	}
}

// the device rect of r in the target, whole pixels
static CGRect device_rect(const struct target *target, CGRect r)
{
	r = CGRectIntersection(CGRectIntegral(r), CGRectMake(target->x, target->y, target->width, target->height));
	return CGRectIsNull(r) ? CGRectZero : r;
}

void painter_save(struct painter *painter)
{
	if (top(painter)->context)
		CGContextSaveGState(top(painter)->context);
	push_level(painter, false);
}

void painter_save_layer(struct painter *painter, const struct op *op)
{
	const float *a = op->args;
	struct target *parent = top(painter);
	CGRect box = parent->context ? CGContextGetClipBoundingBox(parent->context) : CGRectZero;
	if (!invertible(&op->matrix))
		box = CGRectZero;
	else if (a[4])
		box = CGRectIntersection(box, CGRectApplyAffineTransform(CGRectStandardize(CGRectMake(a[0], a[1], a[2] - a[0], a[3] - a[1])), affine(&op->matrix)));
	CGRect device = device_rect(parent, box);
	struct target layer = {NULL, calloc((size_t)device.size.width * device.size.height ?: 1, 4), device.origin.x, device.origin.y, device.size.width, device.size.height, *op};
	layer.context = context_over(layer.words, layer.x, layer.y, layer.width, layer.height);
	if (parent->context)
		CGContextSaveGState(parent->context);
	push_level(painter, true);
	push_target(painter, layer);
	if (layer.context)
		replay_clips(painter, layer.context, painter->n_levels - 1);
}

// the layer onto its parent under the parent's clip: CG's blend where it is Skia's, else per pixel
static void composite_layer(struct painter *painter, struct target *layer)
{
	struct target *parent = &painter->targets[painter->n_targets - 2];
	enum gfx_blend mode = (enum gfx_blend)layer->layer.args[6];
	float alpha = layer->layer.args[5] / 255.f;
	CGRect device = CGRectMake(layer->x, layer->y, layer->width, layer->height);
	if (mode == GFX_BLEND_DST || CGRectIsEmpty(device) || !layer->context)
		return;
	if (cg_blend_exact(mode)) {
		CGImageRef image = CGBitmapContextCreateImage(layer->context);
		CGContextSaveGState(parent->context);
		CGContextSetAlpha(parent->context, alpha);
		CGContextSetBlendMode(parent->context, cg_blend(mode));
		// CGContextDrawImage draws y up; the context is y down
		CGContextTranslateCTM(parent->context, 0, CGRectGetMaxY(device) + CGRectGetMinY(device));
		CGContextScaleCTM(parent->context, 1, -1);
		CGContextDrawImage(parent->context, device, image);
		CGContextRestoreGState(parent->context);
		CGImageRelease(image);
		return;
	}
	uint32_t *coverage;
	CGContextRef cover = scratch(painter, device, painter->n_levels, &coverage); // the levels below the layer's
	if (cover) {
		CGContextSetRGBFillColor(cover, 1, 1, 1, 1);
		CGContextFillRect(cover, device);
		CGContextRelease(cover);
		blend_region(parent, device, layer->words, coverage, false, alpha, mode);
	}
	free(coverage);
}

void painter_restore(struct painter *painter)
{
	if (painter->n_levels <= 1)
		return;
	struct level *level = &painter->levels[--painter->n_levels];
	for (size_t i = 0; i < level->n_clips; i++)
		path_unref(level->clips[i].path);
	free(level->clips);
	if (level->layer) {
		struct target *layer = top(painter);
		composite_layer(painter, layer);
		if (layer->context)
			CGContextRelease(layer->context);
		free(layer->words);
		painter->n_targets--;
		if (top(painter)->context) {
			CGContextRestoreGState(top(painter)->context);
			changed(painter, NULL);
		}
	} else if (top(painter)->context) {
		CGContextRestoreGState(top(painter)->context);
	}
}

void painter_clip(struct painter *painter, const struct op *op)
{
	struct level *level = &painter->levels[painter->n_levels - 1];
	if (level->n_clips == level->clips_size) {
		level->clips_size = level->clips_size ? level->clips_size * 2 : 4;
		level->clips = realloc(level->clips, level->clips_size * sizeof(*level->clips));
	}
	struct op *kept = &level->clips[level->n_clips++];
	*kept = *op;
	memset(&kept->paint, 0, sizeof(kept->paint));
	kept->bitmap = NULL;
	kept->text = NULL;
	if (kept->path)
		path_ref(kept->path);
	if (top(painter)->context)
		apply_clip(top(painter)->context, op);
}

// the op in mode at its coverage, per pixel: its content and its coverage drawn apart, then blended
static void draw_pixels(struct painter *painter, const struct op *op)
{
	struct target *target = top(painter);
	struct gfx_rect bounds;
	CGRect box = CGContextGetClipBoundingBox(target->context);
	if (record_op_bounds(op, &bounds))
		box = CGRectIntersection(box, CGRectInset(CGRectApplyAffineTransform(CGRectMake(bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top), affine(&op->matrix)), -2, -2));
	CGRect device = device_rect(target, box);
	if (CGRectIsEmpty(device))
		return;
	struct op content = *op, cover = *op;
	content.paint.blend = GFX_BLEND_SRC_OVER;
	cover.paint.blend = GFX_BLEND_SRC_OVER;
	cover.paint.color = 0xffffffff;
	cover.paint.shader = NULL;
	cover.paint.filter = GFX_NO_COLOR_FILTER;
	if (op->kind == OP_BITMAP) { // its destination rectangle
		cover.kind = OP_RECT;
		memcpy(cover.args, op->args + 4, 4 * sizeof(*cover.args));
		cover.paint.style = GFX_PAINT_FILL;
		cover.bitmap = NULL;
	}
	uint32_t *content_words, *coverage_words;
	CGContextRef content_context = scratch(painter, device, painter->n_levels, &content_words);
	CGContextRef cover_context = scratch(painter, device, painter->n_levels, &coverage_words);
	if (content_context && cover_context) {
		cg_draw(content_context, &content, 1, true);
		cg_draw(cover_context, &cover, 1, true);
		blend_region(target, device, content_words, coverage_words, true, 1, op->paint.blend);
	}
	if (content_context)
		CGContextRelease(content_context);
	if (cover_context)
		CGContextRelease(cover_context);
	free(content_words);
	free(coverage_words);
}

void painter_draw(struct painter *painter, const struct op *op)
{
	if (!top(painter)->context || !invertible(&op->matrix) || op->paint.blend == GFX_BLEND_DST)
		return;
	struct op drawn = *op;
	struct gfx_bitmap *own = NULL;
	if (op->kind == OP_BITMAP && op->bitmap == painter->bitmap) // into itself: from a copy
		drawn.bitmap = own = bitmap_snapshot(op->bitmap);
	if (cg_blend_exact(op->paint.blend))
		cg_draw(top(painter)->context, &drawn, 1, true);
	else
		draw_pixels(painter, &drawn);
	if (own)
		gfx_bitmap_unref(own);
	changed(painter, op);
}
