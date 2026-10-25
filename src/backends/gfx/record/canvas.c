#include <stdlib.h>
#include <string.h>

#include <graphene.h>

#include "src/slots/gfx.h"

#include "record.h"

/* A display list of plain data: each op holds a copy of its paint and the matrix it was drawn
 * under, since Paint and Matrix free their native state on the FinalizerDaemon. */

enum op_kind {
	OP_RECT,       // left, top, right, bottom
	OP_ROUND_RECT, // left, top, right, bottom, rx, ry
	OP_LINE,       // x0, y0, x1, y1
	OP_DRAWABLE,   // width, height; no paint
};

struct op {
	graphene_matrix_t matrix;
	struct gfx_paint paint;
	enum op_kind kind;
	float args[6];
};

struct gfx_canvas {
	graphene_matrix_t matrix;
	graphene_matrix_t *saved; // save()'s stack
	size_t n_saved, saved_size;
	struct op *ops;
	size_t n_ops, ops_size;
};

struct gfx_canvas *gfx_canvas_new(void)
{
	struct gfx_canvas *canvas = calloc(1, sizeof(*canvas));
	graphene_matrix_init_identity(&canvas->matrix);
	return canvas;
}

void gfx_canvas_free(struct gfx_canvas *canvas)
{
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
	canvas->saved[canvas->n_saved++] = canvas->matrix;
}

void gfx_canvas_restore(struct gfx_canvas *canvas)
{
	if (canvas->n_saved)
		canvas->matrix = canvas->saved[--canvas->n_saved];
}

// row-vector form: `m` applies first, then the current matrix
static void pre_concat(struct gfx_canvas *canvas, const graphene_matrix_t *m)
{
	graphene_matrix_multiply(m, &canvas->matrix, &canvas->matrix);
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

static void add(struct gfx_canvas *canvas, enum op_kind kind, const struct gfx_paint *paint, const float *args, size_t n_args)
{
	if (canvas->n_ops == canvas->ops_size) {
		canvas->ops_size = canvas->ops_size ? canvas->ops_size * 2 : 8;
		canvas->ops = realloc(canvas->ops, canvas->ops_size * sizeof(*canvas->ops));
	}
	struct op *op = &canvas->ops[canvas->n_ops++];
	memset(op, 0, sizeof(*op));
	op->matrix = canvas->matrix;
	if (paint)
		op->paint = *paint;
	op->kind = kind;
	memcpy(op->args, args, n_args * sizeof(*args));
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

void gfx_canvas_draw_drawable(struct gfx_canvas *canvas, struct gfx_drawable *drawable, int width, int height)
{
	if (!drawable->drawable_ref) // only containers here: the others draw through Java's draw()
		add(canvas, OP_DRAWABLE, NULL, (float[]){width, height}, 2);
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
		case OP_DRAWABLE:
			fputs("drawable ", out);
			print_numbers(out, op->args, 2, "x");
			break;
		}
		print_matrix(out, &op->matrix);
		fputc('\n', out);
	}
}
#endif
