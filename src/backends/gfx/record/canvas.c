#include <stdlib.h>
#include <string.h>

#include <graphene.h>

#include "src/slots/gfx.h"

#include "record.h"

struct gfx_canvas *gfx_canvas_new(void)
{
	struct gfx_canvas *canvas = calloc(1, sizeof(*canvas));
	canvas->refs = 1;
	graphene_matrix_init_identity(&canvas->state.matrix);
	return canvas;
}

struct gfx_canvas *record_canvas_ref(struct gfx_canvas *canvas)
{
	atomic_fetch_add(&canvas->refs, 1);
	return canvas;
}

void gfx_canvas_free(struct gfx_canvas *canvas)
{
	if (atomic_fetch_sub(&canvas->refs, 1) != 1)
		return;
	for (size_t i = 0; i < canvas->n_ops; i++) {
		if (canvas->ops[i].bitmap)
			gfx_bitmap_unref(canvas->ops[i].bitmap);
	}
	free(canvas->saved);
	free(canvas->ops);
	free(canvas);
}

void gfx_canvas_save(struct gfx_canvas *canvas)
{
	if (canvas->n_saved == canvas->saved_size) {
		canvas->saved_size = canvas->saved_size ? canvas->saved_size * 2 : 4;
		canvas->saved = realloc(canvas->saved, canvas->saved_size * sizeof(*canvas->saved));
	}
	canvas->saved[canvas->n_saved++] = canvas->state;
	canvas->state.clips = 0;
}

static struct op *add(struct gfx_canvas *canvas, enum op_kind kind, const struct gfx_paint *paint, const float *args, size_t n_args);

void gfx_canvas_restore(struct gfx_canvas *canvas)
{
	if (!canvas->n_saved)
		return;
	if (canvas->state.clips)
		add(canvas, OP_UNCLIP, NULL, (float[]){canvas->state.clips}, 1);
	canvas->state = canvas->saved[--canvas->n_saved];
}

void gfx_canvas_clip_rect(struct gfx_canvas *canvas, float left, float top, float right, float bottom, enum gfx_clip_op op)
{
	add(canvas, OP_CLIP, NULL, (float[]){left, top, right, bottom, op}, 5);
	canvas->state.clips++;
}

// row-vector form: `m` applies first, then the current matrix
static void pre_concat(struct gfx_canvas *canvas, const graphene_matrix_t *m)
{
	graphene_matrix_multiply(m, &canvas->state.matrix, &canvas->state.matrix);
}

void gfx_canvas_translate(struct gfx_canvas *canvas, float dx, float dy)
{
	graphene_matrix_t m;
	pre_concat(canvas, graphene_matrix_init_translate(&m, &GRAPHENE_POINT3D_INIT(dx, dy, 0)));
}

void gfx_canvas_rotate(struct gfx_canvas *canvas, float degrees)
{
	graphene_matrix_t m;
	pre_concat(canvas, graphene_matrix_init_rotate(&m, degrees, graphene_vec3_z_axis()));
}

void gfx_canvas_scale(struct gfx_canvas *canvas, float sx, float sy)
{
	graphene_matrix_t m;
	pre_concat(canvas, graphene_matrix_init_scale(&m, sx, sy, 1));
}

void gfx_canvas_concat(struct gfx_canvas *canvas, const graphene_matrix_t *matrix)
{
	pre_concat(canvas, matrix);
}

static struct op *add(struct gfx_canvas *canvas, enum op_kind kind, const struct gfx_paint *paint, const float *args, size_t n_args)
{
	if (canvas->n_ops == canvas->ops_size) {
		canvas->ops_size = canvas->ops_size ? canvas->ops_size * 2 : 8;
		canvas->ops = realloc(canvas->ops, canvas->ops_size * sizeof(*canvas->ops));
	}
	struct op *op = &canvas->ops[canvas->n_ops++];
	memset(op, 0, sizeof(*op));
	op->matrix = canvas->state.matrix;
	if (paint)
		op->paint = *paint;
	op->kind = kind;
	memcpy(op->args, args, n_args * sizeof(*args));
	return op;
}

void gfx_canvas_draw_rect(struct gfx_canvas *canvas, float left, float top, float right, float bottom, const struct gfx_paint *paint)
{
	add(canvas, OP_RECT, paint, (float[]){left, top, right, bottom}, 4);
}

void gfx_canvas_draw_round_rect(struct gfx_canvas *canvas, float left, float top, float right, float bottom, float rx, float ry, const struct gfx_paint *paint)
{
	add(canvas, OP_ROUND_RECT, paint, (float[]){left, top, right, bottom, rx, ry}, 6);
}

void gfx_canvas_draw_line(struct gfx_canvas *canvas, float x0, float y0, float x1, float y1, const struct gfx_paint *paint)
{
	add(canvas, OP_LINE, paint, (float[]){x0, y0, x1, y1}, 4);
}

void gfx_canvas_draw_bitmap(struct gfx_canvas *canvas, struct gfx_bitmap *bitmap, const struct gfx_rect *src, const struct gfx_rect *dst, const struct gfx_paint *paint)
{
	struct gfx_rect all = {0, 0, gfx_bitmap_get_width(bitmap), gfx_bitmap_get_height(bitmap)};
	if (!src)
		src = &all;
	gfx_bitmap_ref(bitmap);
	add(canvas, OP_BITMAP, paint, (float[]){src->left, src->top, src->right, src->bottom, dst->left, dst->top, dst->right, dst->bottom}, 8)->bitmap = bitmap;
}

// a Java-drawn one recorded itself in its draw(); a container draws its child
void gfx_canvas_draw_drawable(struct gfx_canvas *canvas, struct gfx_drawable *drawable, int width, int height)
{
	if (drawable->bitmap) {
		struct gfx_paint opaque = {.color = 0xff000000, .filter_mode = -1};
		gfx_canvas_draw_bitmap(canvas, drawable->bitmap, NULL, &(struct gfx_rect){0, 0, width, height}, &opaque);
	} else if (!drawable->drawable_ref && drawable->selected) {
		gfx_canvas_draw_drawable(canvas, drawable->selected, width, height);
	}
}

#ifdef ATL_TEST_HOOKS
static const char *const style_names[] = {"fill", "stroke", "fill_and_stroke"};

// 3 decimals without trailing zeros; rounding noise around 0 prints as 0
static void print_number(FILE *out, float value)
{
	char text[32];
	snprintf(text, sizeof(text), "%.3f", value);
	if (!strcmp(text, "-0.000"))
		strcpy(text, "0");
	char *end = text + strlen(text) - 1;
	if (strchr(text, '.')) {
		while (*end == '0')
			*end-- = '\0';
		if (*end == '.')
			*end = '\0';
	}
	fputs(text, out);
}

static void print_numbers(FILE *out, const float *values, size_t n, const char *separator)
{
	for (size_t i = 0; i < n; i++) {
		if (i)
			fputs(separator, out);
		print_number(out, values[i]);
	}
}

static void print_paint(FILE *out, const struct gfx_paint *paint)
{
	fprintf(out, " #%08x %s", paint->color, style_names[paint->style]);
	if (paint->style != GFX_PAINT_FILL) {
		fputs(" width ", out);
		print_number(out, paint->stroke_width);
		fprintf(out, " cap %d join %d", paint->stroke_cap, paint->stroke_join);
	}
	if (paint->filter_mode != -1)
		fprintf(out, " filter %d #%08x", paint->filter_mode, paint->filter_color);
}

// Android's 3x3, in getValues' order
static void print_matrix(FILE *out, const graphene_matrix_t *m)
{
	if (graphene_matrix_is_identity(m))
		return;
	const float values[9] = {
		graphene_matrix_get_value(m, 0, 0), graphene_matrix_get_value(m, 1, 0), graphene_matrix_get_value(m, 3, 0),
		graphene_matrix_get_value(m, 0, 1), graphene_matrix_get_value(m, 1, 1), graphene_matrix_get_value(m, 3, 1),
		graphene_matrix_get_value(m, 0, 3), graphene_matrix_get_value(m, 1, 3), graphene_matrix_get_value(m, 3, 3),
	};
	fputs(" matrix [", out);
	for (int row = 0; row < 3; row++) {
		if (row)
			fputc(';', out);
		print_numbers(out, values + row * 3, 3, ",");
	}
	fputc(']', out);
}

void gfx_canvas_dump(const struct gfx_canvas *canvas, FILE *out)
{
	for (size_t i = 0; i < canvas->n_ops; i++) {
		const struct op *op = &canvas->ops[i];
		fputs("displaylist:   ", out);
		switch (op->kind) {
		case OP_RECT:
		case OP_ROUND_RECT:
			fputs(op->kind == OP_RECT ? "rect " : "round_rect ", out);
			print_numbers(out, op->args, 2, ",");
			fputc(' ', out);
			print_numbers(out, op->args + 2, 2, ",");
			if (op->kind == OP_ROUND_RECT) {
				fputc(' ', out);
				print_numbers(out, op->args + 4, 2, ",");
			}
			print_paint(out, &op->paint);
			break;
		case OP_LINE:
			fputs("line ", out);
			print_numbers(out, op->args, 2, ",");
			fputc(' ', out);
			print_numbers(out, op->args + 2, 2, ",");
			print_paint(out, &op->paint);
			break;
		case OP_CLIP:
			fputs("clip ", out);
			print_numbers(out, op->args, 2, ",");
			fputc(' ', out);
			print_numbers(out, op->args + 2, 2, ",");
			if (op->args[4] == GFX_CLIP_DIFFERENCE)
				fputs(" difference", out);
			break;
		case OP_UNCLIP:
			fprintf(out, "unclip %d", (int)op->args[0]);
			break;
		case OP_BITMAP:
			fprintf(out, "bitmap %dx%d src ", gfx_bitmap_get_width(op->bitmap), gfx_bitmap_get_height(op->bitmap));
			print_numbers(out, op->args, 2, ",");
			fputc(' ', out);
			print_numbers(out, op->args + 2, 2, ",");
			fputs(" dst ", out);
			print_numbers(out, op->args + 4, 2, ",");
			fputc(' ', out);
			print_numbers(out, op->args + 6, 2, ",");
			fprintf(out, " #%08x", op->paint.color);
			break;
		}
		if (op->kind != OP_UNCLIP)
			print_matrix(out, &op->matrix);
		fputc('\n', out);
	}
}
#endif
