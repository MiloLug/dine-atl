#include <stdlib.h>
#include <string.h>

#include "src/shared/upcall.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "records.h"

/* Layout mirrors GTK's: the root at the window size, then each ui_view_new view lays out its
 * children. The backend measures text. */

#define MEASURE_SPEC_EXACTLY (1 << 30)
#define MEASURE_SPEC_AT_MOST (2 << 30)
#define MEASURE_SPEC_MASK    (0x3 << 30)

#define DEFAULT_TEXT_SIZE_SP 14.f

#define MAX_PASSES_IN_A_ROW  8

static struct ui_window *the_window;

void ui_display_get(struct ui_display *display)
{
	const struct atl_window_config *config = atl_window_get();
	display->width = config->width;
	display->height = config->height;
	display->night = config->night;
}

static void lay_out(struct ui_window *window)
{
	struct ui_view *root = window->shown;
	if (!root)
		return;
	if (++window->passes_in_a_row > MAX_PASSES_IN_A_ROW) {
		fprintf(stderr, "records ui: layout still requested after %d passes, giving up\n", MAX_PASSES_IN_A_ROW);
		return;
	}

	const struct atl_window_config *config = atl_window_get();
	root->left = root->top = 0;
	root->right = config->width;
	root->bottom = config->height;
	atl_upcall_view_measure(root->ref, config->width | MEASURE_SPEC_EXACTLY, config->height | MEASURE_SPEC_EXACTLY);
	atl_upcall_view_layout(root->ref, config->width, config->height);

	if (!window->layout_pending)
		window->passes_in_a_row = 0;
	if (!window->ready) {
		window->ready = true;
		atl_upcall_window_ready();
	}
}

static void layout_pass(void *data)
{
	struct ui_window *window = data;
	window->layout_pending = false;
	lay_out(window);
	records_layout_done(window);
}

static void schedule_layout(struct ui_window *window)
{
	if (window->layout_pending)
		return;
	window->layout_pending = true;
	loop_main_post(layout_pass, window);
}

struct ui_window *ui_window_new(void *surface)
{
	the_window = calloc(1, sizeof(*the_window));
	records_window_created(the_window, surface);
	return the_window;
}

void ui_window_set_title(struct ui_window *window, const char *title) {}

void ui_window_set_java(struct ui_window *window, struct atl_ref *window_ref)
{
	atl_ref_free(window_ref);
}

void ui_window_clear_background(struct ui_window *window) {}

void ui_window_add_activity(struct ui_window *window, struct ui_view *root)
{
	records_root_added(window, root);
}

void ui_window_show_activity(struct ui_window *window, struct ui_view *root)
{
	window->shown = root;
	records_root_shown(window, root);
	schedule_layout(window);
}

void ui_window_remove_activity(struct ui_window *window, struct ui_view *root)
{
	if (window->shown == root)
		window->shown = NULL;
	records_root_removed(window, root);
}

void ui_window_set_back_enabled(struct ui_window *window, bool enabled) {}

void ui_window_set_frame(struct ui_window *window, const struct ui_frame *frame)
{
	struct ui_frame *current = &window->frame;
	if (current->has_color == frame->has_color && (!frame->has_color || (current->color == frame->color && current->light == frame->light)))
		return;
	*current = *frame;
	records_frame_changed(window);
}

void ui_window_close(struct ui_window *window)
{
	records_window_closed(window);
	loop_main_quit();
}

void ui_window_get_size(struct ui_window *window, int *width, int *height)
{
	*width = atl_window_get()->width;
	*height = atl_window_get()->height;
}

static struct ui_view *view_new(enum node_kind kind, struct atl_ref *view_ref, const char *class_name)
{
	struct ui_view *view = calloc(1, sizeof(*view));
	view->kind = kind;
	view->ref = view_ref;
	view->class_name = strdup(class_name);
	if (kind == NODE_TEXT) {
		view->text = strdup("");
		view->text_size = DEFAULT_TEXT_SIZE_SP * atl_window_get()->density_dpi / 160;
	}
	records_view_created(view);
	return view;
}

struct ui_view *ui_view_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits)
{
	return view_new(NODE_VIEW, view_ref, class_name);
}

void ui_view_add_child(struct ui_view *parent, struct ui_view *child, int index)
{
	if (index < 0 || (size_t)index > parent->n_children)
		index = parent->n_children;
	parent->children = realloc(parent->children, (parent->n_children + 1) * sizeof(*parent->children));
	memmove(&parent->children[index + 1], &parent->children[index], (parent->n_children - index) * sizeof(*parent->children));
	parent->children[index] = child;
	parent->n_children++;
	records_child_added(parent, child, index);
}

void ui_view_remove_child(struct ui_view *parent, struct ui_view *child)
{
	for (size_t i = 0; i < parent->n_children; i++) {
		if (parent->children[i] == child) {
			memmove(&parent->children[i], &parent->children[i + 1], (parent->n_children - i - 1) * sizeof(*parent->children));
			parent->n_children--;
			records_child_removed(parent, child);
			return;
		}
	}
}

void ui_view_free(struct ui_view *view)
{
	records_test_view_freed(view);
	free(view->children); // the children may be freed already
	free(view->class_name);
	free(view->text);
	atl_ref_free(view->ref);
	records_view_freed(view);
}

void ui_view_set_layout_params(struct ui_view *view, const struct ui_layout_params *params) {}

void ui_view_set_padding(struct ui_view *view, int left, int top, int right, int bottom)
{
	view->padding_left = left;
	view->padding_top = top;
	view->padding_right = right;
	view->padding_bottom = bottom;
	records_padding_changed(view);
}

void ui_view_set_background_color(struct ui_view *view, uint32_t argb)
{
	records_background_changed(view, argb);
}

void ui_view_request_layout(struct ui_view *view)
{
	if (the_window)
		schedule_layout(the_window);
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
		records_text_extent(view, max_width, &content_width, &content_height);
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
	if (text)
		ui_text_set_text(view, text);
	return view;
}

void ui_text_set_text(struct ui_view *view, const char *text)
{
	free(view->text);
	view->text = strdup(text);
	if (view->kind == NODE_TEXT)
		records_text_changed(view);
}

void ui_text_set_color(struct ui_view *view, uint32_t argb)
{
	if (view->kind == NODE_TEXT)
		records_text_color_changed(view, argb);
}

void ui_text_set_size(struct ui_view *view, float size)
{
	view->text_size = size;
	if (view->kind == NODE_TEXT)
		records_text_changed(view);
}

void ui_text_set_style(struct ui_view *view, int style)
{
	view->text_style = style;
	if (view->kind == NODE_TEXT)
		records_text_changed(view);
}
