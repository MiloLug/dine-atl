#include <math.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_text_Layout.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/gfx.h"

#include "text.h"

/* android.text.Layout: lines broken by the gfx slot, left to right, every line one font height.
 * Offsets are UTF-16 units; a line's end includes its '\n'. */

enum {
	ALIGN_NORMAL,
	ALIGN_OPPOSITE,
	ALIGN_CENTER,
	ALIGN_LEFT,
	ALIGN_RIGHT,
};

// TruncateAt's ordinal + 1, as StaticLayout passes it
enum {
	ELLIPSIZE_NONE,
	ELLIPSIZE_START,
	ELLIPSIZE_MIDDLE,
	ELLIPSIZE_END,
	ELLIPSIZE_MARQUEE,
	ELLIPSIZE_END_SMALL,
};

#define ELLIPSIS 0x2026

struct line {
	size_t start, end, content_end; // content_end: before the '\n'
	size_t ellipsis_start, ellipsis_count; // in the line
	float width;
};

struct layout {
	struct gfx_paint *paint;
	uint16_t *text;
	size_t len;
	int width; // -1: unbounded
	int align;
	int ellipsize;
	float ellipsize_width;
	struct atl_font_metrics_int metrics;
	struct line *lines;
	size_t n_lines, lines_size;
};

static int line_height(const struct layout *layout)
{
	return layout->metrics.descent - layout->metrics.ascent;
}

static struct line *add_line(struct layout *layout, size_t start, size_t content_end, size_t end)
{
	if (layout->n_lines == layout->lines_size) {
		layout->lines_size = layout->lines_size ? layout->lines_size * 2 : 4;
		layout->lines = realloc(layout->lines, layout->lines_size * sizeof(*layout->lines));
	}
	struct line *line = &layout->lines[layout->n_lines++];
	*line = (struct line){.start = start, .end = end, .content_end = content_end};
	line->width = gfx_text_measure(layout->paint, layout->text + start, content_end - start);
	return line;
}

static bool is_low_surrogate(uint16_t unit)
{
	return unit >= 0xDC00 && unit < 0xE000;
}

// the cut that fits ellipsize_width with the ellipsis, never inside a surrogate pair
static void ellipsize(struct layout *layout, struct line *line)
{
	const uint16_t *text = layout->text + line->start;
	size_t len = line->content_end - line->start;
	if (line->width <= layout->ellipsize_width)
		return;
	float *x = malloc((len + 1) * sizeof(*x));
	gfx_text_carets(layout->paint, text, len, x);
	float room = layout->ellipsize_width - gfx_text_measure(layout->paint, (uint16_t[]){ELLIPSIS}, 1);
	size_t keep_start = 0, keep_end = len; // text kept: [0, keep_start) and [keep_end, len)
	if (layout->ellipsize == ELLIPSIZE_START) {
		while (keep_end > 0 && x[len] - x[keep_end - 1] <= room)
			keep_end--;
	} else {
		float left_room = layout->ellipsize == ELLIPSIZE_MIDDLE ? room / 2 : room;
		while (keep_start < len && x[keep_start + 1] <= left_room)
			keep_start++;
		if (layout->ellipsize == ELLIPSIZE_MIDDLE) {
			float right_room = room - x[keep_start];
			while (keep_end > keep_start && x[len] - x[keep_end - 1] <= right_room)
				keep_end--;
		}
	}
	if (keep_start && keep_start < len && is_low_surrogate(text[keep_start]))
		keep_start--;
	if (keep_end < len && is_low_surrogate(text[keep_end]))
		keep_end++;
	line->ellipsis_start = keep_start;
	line->ellipsis_count = keep_end - keep_start;
	line->width = x[keep_start] + (layout->ellipsize_width - room) + (x[len] - x[keep_end]);
	free(x);
}

// an ellipsizing layout keeps each paragraph on one line, as Pango does
static void build(struct layout *layout)
{
	layout->n_lines = 0;
	size_t paragraph = 0;
	for (size_t i = 0; i <= layout->len; i++) {
		if (i < layout->len && layout->text[i] != '\n')
			continue;
		size_t end = i < layout->len ? i + 1 : i;
		// a "\r\n" ends the paragraph as one; the backends would break at the '\r' too
		size_t content_end = i < layout->len && i > paragraph && layout->text[i - 1] == '\r' ? i - 1 : i;
		if (layout->ellipsize != ELLIPSIZE_NONE && layout->ellipsize_width > 0) {
			ellipsize(layout, add_line(layout, paragraph, content_end, end));
		} else if (layout->width < 0) {
			add_line(layout, paragraph, content_end, end);
		} else {
			size_t *ends;
			size_t n = gfx_text_break_lines(layout->paint, layout->text + paragraph, content_end - paragraph, layout->width, &ends);
			for (size_t k = 0, start = paragraph; k < n; k++) {
				size_t line_end = paragraph + ends[k];
				add_line(layout, start, line_end, k == n - 1 ? end : line_end);
				start = line_end;
			}
			free(ends);
		}
		paragraph = end;
	}
}

static struct layout *get(jlong layout)
{
	return _PTR(layout);
}

static const struct line *line_of(jlong layout, jint line)
{
	return &get(layout)->lines[line];
}

static float line_left(const struct layout *layout, const struct line *line)
{
	if (layout->width < 0)
		return 0;
	switch (layout->align) {
	case ALIGN_OPPOSITE:
	case ALIGN_RIGHT:
		return layout->width - line->width;
	case ALIGN_CENTER:
		return (layout->width - line->width) / 2;
	default:
		return 0;
	}
}

JNIEXPORT jlong JNICALL Java_android_text_Layout_native_1constructor(JNIEnv *env, jclass class, jstring text, jlong paint, jint width, jint align)
{
	struct layout *layout = calloc(1, sizeof(*layout));
	layout->paint = gfx_paint_clone(_PTR(paint));
	layout->len = (*env)->GetStringLength(env, text);
	layout->text = malloc((layout->len ?: 1) * sizeof(*layout->text));
	(*env)->GetStringRegion(env, text, 0, layout->len, layout->text);
	layout->width = width;
	layout->align = align;
	atl_font_metrics_int(layout->paint, &layout->metrics);
	build(layout);
	return _INTPTR(layout);
}

JNIEXPORT void JNICALL Java_android_text_Layout_native_1free(JNIEnv *env, jclass class, jlong layout_ptr)
{
	struct layout *layout = get(layout_ptr);
	gfx_paint_free(layout->paint);
	free(layout->text);
	free(layout->lines);
	free(layout);
}

JNIEXPORT void JNICALL Java_android_text_Layout_native_1set_1width(JNIEnv *env, jobject object, jlong layout, jint width)
{
	get(layout)->width = width;
	build(get(layout));
}

JNIEXPORT void JNICALL Java_android_text_Layout_native_1set_1ellipsize(JNIEnv *env, jobject object, jlong layout, jint mode, jfloat width)
{
	get(layout)->ellipsize = mode == ELLIPSIZE_END_SMALL ? ELLIPSIZE_END : mode <= ELLIPSIZE_END ? mode : ELLIPSIZE_NONE;
	get(layout)->ellipsize_width = width;
	build(get(layout));
}

JNIEXPORT jfloat JNICALL Java_android_text_Layout_native_1get_1desired_1width(JNIEnv *env, jclass class, jlong layout_ptr)
{
	const struct layout *layout = get(layout_ptr);
	float width = 0;
	for (size_t i = 0; i < layout->n_lines; i++)
		width = fmaxf(width, layout->lines[i].width);
	return width;
}

// unbounded: as wide as its widest line
JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1width(JNIEnv *env, jobject object, jlong layout)
{
	return get(layout)->width >= 0 ? get(layout)->width : ceilf(Java_android_text_Layout_native_1get_1desired_1width(env, NULL, layout));
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1height(JNIEnv *env, jobject object, jlong layout)
{
	return get(layout)->n_lines * line_height(get(layout));
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1count(JNIEnv *env, jobject object, jlong layout)
{
	return get(layout)->n_lines;
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1start(JNIEnv *env, jobject object, jlong layout, jint line)
{
	return line_of(layout, line)->start;
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1end(JNIEnv *env, jobject object, jlong layout, jint line)
{
	return line_of(layout, line)->end;
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1top(JNIEnv *env, jobject object, jlong layout, jint line)
{
	return line * line_height(get(layout));
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1bottom(JNIEnv *env, jobject object, jlong layout, jint line)
{
	return (line + 1) * line_height(get(layout));
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1baseline(JNIEnv *env, jobject object, jlong layout, jint line)
{
	return line * line_height(get(layout)) - get(layout)->metrics.ascent;
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1ascent(JNIEnv *env, jobject object, jlong layout, jint line)
{
	return get(layout)->metrics.ascent;
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1descent(JNIEnv *env, jobject object, jlong layout, jint line)
{
	return get(layout)->metrics.descent;
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1left(JNIEnv *env, jobject object, jlong layout, jint line)
{
	return line_left(get(layout), line_of(layout, line));
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1right(JNIEnv *env, jobject object, jlong layout, jint line)
{
	return line_left(get(layout), line_of(layout, line)) + line_of(layout, line)->width;
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1width(JNIEnv *env, jobject object, jlong layout, jint line)
{
	return line_of(layout, line)->width;
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1for_1vertical(JNIEnv *env, jobject object, jlong layout, jint y)
{
	int line = line_height(get(layout)) > 0 && y > 0 ? y / line_height(get(layout)) : 0;
	return line < (int)get(layout)->n_lines ? line : (int)get(layout)->n_lines - 1;
}

// an offset at a line's end starts the next line, but the last line's end is its own
JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1line_1for_1offset(JNIEnv *env, jclass class, jlong layout_ptr, jint offset)
{
	const struct layout *layout = get(layout_ptr);
	size_t line = 0;
	while (line + 1 < layout->n_lines && (size_t)offset >= layout->lines[line].end)
		line++;
	return line;
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1ellipsis_1start(JNIEnv *env, jobject object, jlong layout, jint line)
{
	return line_of(layout, line)->ellipsis_start;
}

JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1ellipsis_1count(JNIEnv *env, jobject object, jlong layout, jint line)
{
	return line_of(layout, line)->ellipsis_count;
}

// x of each content position; *len gets the content length
static float *carets(const struct layout *layout, const struct line *line, size_t *len)
{
	*len = line->content_end - line->start;
	float *x = malloc((*len + 1) * sizeof(*x));
	gfx_text_carets(layout->paint, layout->text + line->start, *len, x);
	return x;
}

JNIEXPORT jfloat JNICALL Java_android_text_Layout_native_1get_1primary_1horizontal(JNIEnv *env, jclass class, jlong layout_ptr, jint offset)
{
	const struct layout *layout = get(layout_ptr);
	const struct line *line = &layout->lines[Java_android_text_Layout_native_1get_1line_1for_1offset(env, class, layout_ptr, offset)];
	size_t len;
	float *x = carets(layout, line, &len);
	size_t in_line = offset < (jint)line->start ? 0 : offset - line->start;
	float position = line_left(layout, line) + x[in_line < len ? in_line : len];
	free(x);
	return position;
}

JNIEXPORT jfloat JNICALL Java_android_text_Layout_native_1get_1secondary_1horizontal(JNIEnv *env, jclass class, jlong layout, jint offset)
{
	return Java_android_text_Layout_native_1get_1primary_1horizontal(env, class, layout, offset);
}

// the nearest caret
JNIEXPORT jint JNICALL Java_android_text_Layout_native_1get_1offset_1for_1horizontal(JNIEnv *env, jclass class, jlong layout_ptr, jint line_index, jfloat x_pos)
{
	const struct layout *layout = get(layout_ptr);
	const struct line *line = &layout->lines[line_index];
	size_t len;
	float *x = carets(layout, line, &len);
	x_pos -= line_left(layout, line);
	size_t best = 0;
	for (size_t i = 1; i <= len; i++) {
		if (fabsf(x[i] - x_pos) < fabsf(x[best] - x_pos))
			best = i;
	}
	free(x);
	return line->start + best;
}

// the line's text with the ellipsis in place of what it cut; *len gets its length
static uint16_t *shown(const struct layout *layout, const struct line *line, size_t *len)
{
	size_t content = line->content_end - line->start;
	*len = content - line->ellipsis_count + (line->ellipsis_count ? 1 : 0);
	uint16_t *text = malloc((*len ?: 1) * sizeof(*text));
	const uint16_t *source = layout->text + line->start;
	if (!line->ellipsis_count) {
		memcpy(text, source, content * sizeof(*text));
		return text;
	}
	memcpy(text, source, line->ellipsis_start * sizeof(*text));
	text[line->ellipsis_start] = ELLIPSIS;
	size_t rest = line->ellipsis_start + line->ellipsis_count;
	memcpy(text + line->ellipsis_start + 1, source + rest, (content - rest) * sizeof(*text));
	return text;
}

JNIEXPORT void JNICALL Java_android_text_Layout_native_1draw(JNIEnv *env, jobject object, jlong layout_ptr, jlong canvas, jlong paint)
{
	const struct layout *layout = get(layout_ptr);
	if (!canvas)
		return;
	for (size_t i = 0; i < layout->n_lines; i++) {
		const struct line *line = &layout->lines[i];
		size_t len;
		uint16_t *text = shown(layout, line, &len);
		if (len)
			gfx_canvas_draw_text(_PTR(canvas), text, len, line_left(layout, line), i * line_height(layout) - layout->metrics.ascent, _PTR(paint));
		free(text);
	}
}

JNIEXPORT void JNICALL Java_android_text_Layout_native_1draw_1custom_1canvas(JNIEnv *env, jobject object, jlong layout_ptr, jobject canvas, jobject paint)
{
	const struct layout *layout = get(layout_ptr);
	for (size_t i = 0; i < layout->n_lines; i++) {
		const struct line *line = &layout->lines[i];
		size_t len;
		uint16_t *text = shown(layout, line, &len);
		jstring string = (*env)->NewString(env, text, len);
		free(text);
		(*env)->CallVoidMethod(env, canvas, handle_cache.canvas.drawText, string, (jint)0, (jint)len, (jfloat)line_left(layout, line), (jfloat)(i * line_height(layout) - layout->metrics.ascent), paint);
		(*env)->DeleteLocalRef(env, string);
		if ((*env)->ExceptionCheck(env))
			return;
	}
}
