#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/upcall.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

/* View records without a toolkit. Layout mirrors GTK's: the root at the window size, then each
 * ui_view_new view lays out its children. Fixed text metrics keep dumps exact. */

#define MEASURE_SPEC_EXACTLY (1 << 30)
#define MEASURE_SPEC_AT_MOST (2 << 30)
#define MEASURE_SPEC_MASK    (0x3 << 30)

// Paint.measureText's estimate of a character's width
#define CHAR_WIDTH_PER_SIZE  0.6f
#define LINE_HEIGHT_PER_SIZE 1.2f
// TextView's default text size, 14sp at density 1
#define DEFAULT_TEXT_SIZE    14.f

#define MAX_PASSES_IN_A_ROW  8

enum node_kind {
	NODE_VIEW, // ui_view_new: laid out by Java
	NODE_TEXT, // ui_text_new: measured here
};

struct ui_view {
	enum node_kind kind;
	char *class_name;
	struct atl_ref *ref;
	struct ui_view **children;
	size_t n_children;
	int padding_left, padding_top, padding_right, padding_bottom;
	int left, top, right, bottom;
	char *text;
	float text_size;
};

struct ui_window {
	struct ui_view *shown;
	bool layout_pending;
	int passes_in_a_row;
	bool ready;
};

static struct ui_window *the_window;

void ui_display_get(struct ui_display *display)
{
	atl_window_size(&display->width, &display->height);
	display->night = UI_DISPLAY_UNKNOWN;
}

static void layout_pass(void *data)
{
	struct ui_window *window = data;
	window->layout_pending = false;
	struct ui_view *root = window->shown;
	if (!root)
		return;
	if (++window->passes_in_a_row > MAX_PASSES_IN_A_ROW) {
		fprintf(stderr, "headless ui: layout still requested after %d passes, giving up\n", MAX_PASSES_IN_A_ROW);
		return;
	}

	int width, height;
	atl_window_size(&width, &height);
	root->left = root->top = 0;
	root->right = width;
	root->bottom = height;
	atl_upcall_view_measure(root->ref, width | MEASURE_SPEC_EXACTLY, height | MEASURE_SPEC_EXACTLY);
	atl_upcall_view_layout(root->ref, width, height);

	if (!window->layout_pending)
		window->passes_in_a_row = 0;
	if (!window->ready) {
		window->ready = true;
		atl_upcall_window_ready();
	}
}

static void schedule_layout(struct ui_window *window)
{
	if (window->layout_pending)
		return;
	window->layout_pending = true;
	loop_main_post(layout_pass, window);
}

struct ui_window *ui_window_new(void)
{
	the_window = calloc(1, sizeof(*the_window));
	return the_window;
}

void ui_window_set_title(struct ui_window *window, const char *title) {}

void ui_window_set_java(struct ui_window *window, struct atl_ref *window_ref)
{
	atl_ref_free(window_ref);
}

void ui_window_clear_background(struct ui_window *window) {}

void ui_window_add_activity(struct ui_window *window, struct ui_view *root) {}

void ui_window_show_activity(struct ui_window *window, struct ui_view *root)
{
	window->shown = root;
	schedule_layout(window);
}

void ui_window_remove_activity(struct ui_window *window, struct ui_view *root)
{
	if (window->shown == root)
		window->shown = NULL;
}

void ui_window_set_back_enabled(struct ui_window *window, bool enabled) {}

void ui_window_close(struct ui_window *window)
{
	loop_main_quit();
}

void ui_window_get_size(struct ui_window *window, int *width, int *height)
{
	atl_window_size(width, height);
}

static void dump_view(FILE *out, struct ui_view *view, int depth)
{
	fprintf(out, "viewtree: %*s%s %d,%d %dx%d", depth * 2, "", view->class_name,
	        view->left, view->top, view->right - view->left, view->bottom - view->top);
	if (view->kind == NODE_TEXT)
		fprintf(out, " \"%s\"", view->text);
	fputc('\n', out);
	for (size_t i = 0; i < view->n_children; i++)
		dump_view(out, view->children[i], depth + 1);
}

void ui_window_dump(struct ui_window *window, FILE *out)
{
	if (window->shown)
		dump_view(out, window->shown, 0);
	fprintf(out, "viewtree: end\n");
	fflush(out);
}

static struct ui_view *view_new(enum node_kind kind, struct atl_ref *view_ref, const char *class_name)
{
	struct ui_view *view = calloc(1, sizeof(*view));
	view->kind = kind;
	view->ref = view_ref;
	view->class_name = strdup(class_name);
	return view;
}

struct ui_view *ui_view_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits)
{
	return view_new(NODE_VIEW, view_ref, class_name);
}

void ui_view_add_child(struct ui_view *parent, struct ui_view *child, int index)
{
	if (index < 0 || index > parent->n_children)
		index = parent->n_children;
	parent->children = realloc(parent->children, (parent->n_children + 1) * sizeof(*parent->children));
	memmove(&parent->children[index + 1], &parent->children[index], (parent->n_children - index) * sizeof(*parent->children));
	parent->children[index] = child;
	parent->n_children++;
}

void ui_view_set_layout_params(struct ui_view *view, const struct ui_layout_params *params) {}

void ui_view_set_padding(struct ui_view *view, int left, int top, int right, int bottom)
{
	view->padding_left = left;
	view->padding_top = top;
	view->padding_right = right;
	view->padding_bottom = bottom;
}

void ui_view_set_background_color(struct ui_view *view, uint32_t argb) {}

void ui_view_request_layout(struct ui_view *view)
{
	if (the_window)
		schedule_layout(the_window);
}

static int utf8_length(const char *text)
{
	int length = 0;
	for (; *text; text++)
		length += (*text & 0xC0) != 0x80;
	return length;
}

// size of the text box with at most max_width (-1: unbounded) for the text, greedily wrapped
static void text_extent(struct ui_view *view, int max_width, int *width, int *height)
{
	float char_width = CHAR_WIDTH_PER_SIZE * view->text_size;
	int length = view->text ? utf8_length(view->text) : 0;
	int lines = 1;
	*width = ceilf(length * char_width);
	if (max_width >= 0 && *width > max_width) {
		int chars_per_line = floorf(max_width / char_width);
		if (chars_per_line > 0)
			lines = (length + chars_per_line - 1) / chars_per_line;
		*width = max_width;
	}
	*height = lines * (int)ceilf(LINE_HEIGHT_PER_SIZE * view->text_size);
}

static int resolve(int natural, int spec)
{
	int size = spec & ~MEASURE_SPEC_MASK;
	switch (spec & MEASURE_SPEC_MASK) {
		case MEASURE_SPEC_EXACTLY:
			return size;
		case MEASURE_SPEC_AT_MOST:
			return natural < size ? natural : size;
		default:
			return natural;
	}
}

void ui_view_measure(struct ui_view *view, int width_spec, int height_spec, int *width, int *height)
{
	int padding_h = view->padding_left + view->padding_right;
	int padding_v = view->padding_top + view->padding_bottom;
	int content_width = 0, content_height = 0;
	if (view->kind == NODE_TEXT) {
		int max_width = -1; // UNSPECIFIED
		if (width_spec & MEASURE_SPEC_MASK) {
			max_width = (width_spec & ~MEASURE_SPEC_MASK) - padding_h;
			if (max_width < 0)
				max_width = 0;
		}
		text_extent(view, max_width, &content_width, &content_height);
	}
	*width = resolve(content_width + padding_h, width_spec);
	*height = resolve(content_height + padding_v, height_spec);
}

void ui_view_layout(struct ui_view *view, int left, int top, int right, int bottom, float translation_x, float translation_y)
{
	view->left = left + (int)translation_x;
	view->top = top + (int)translation_y;
	view->right = right + (int)translation_x;
	view->bottom = bottom + (int)translation_y;
	if (view->kind == NODE_VIEW)
		atl_upcall_view_layout(view->ref, right - left, bottom - top);
}

struct ui_view *ui_text_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits, const char *text)
{
	struct ui_view *view = view_new(NODE_TEXT, view_ref, class_name);
	view->text = strdup(text ?: "");
	view->text_size = DEFAULT_TEXT_SIZE;
	return view;
}

void ui_text_set_text(struct ui_view *view, const char *text)
{
	free(view->text);
	view->text = strdup(text);
}

void ui_text_set_color(struct ui_view *view, uint32_t argb) {}

void ui_text_set_size(struct ui_view *view, float size)
{
	view->text_size = size;
}
