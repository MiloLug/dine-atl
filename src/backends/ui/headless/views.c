#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/input.h"

#include "../records/records.h"
#include "input.h"

/* No toolkit: the records are all there is. Fixed text metrics keep dumps exact. */

// Paint.measureText's estimate of a character's width
#define CHAR_WIDTH_PER_SIZE  0.6f
#define LINE_HEIGHT_PER_SIZE 1.2f

void records_window_created(struct ui_window *window, void *surface) {}
void records_view_created(struct ui_view *view) {}
void records_child_added(struct ui_view *parent, struct ui_view *child, size_t index) {}
void records_child_removed(struct ui_view *parent, struct ui_view *child) {}
void records_root_added(struct ui_window *window, struct ui_view *root) {}
void records_root_shown(struct ui_window *window, struct ui_view *root) {}
void records_root_removed(struct ui_window *window, struct ui_view *root) {}
void records_padding_changed(struct ui_view *view) {}
void records_background_changed(struct ui_view *view, uint32_t argb) {}
void records_visibility_changed(struct ui_view *view) {}
void records_progress_changed(struct ui_view *view) {}
void records_text_changed(struct ui_view *view) {}
void records_button_changed(struct ui_view *view) {}
void records_text_color_changed(struct ui_view *view, uint32_t argb) {}
void records_selectable_changed(struct ui_view *view) {}
void records_layout_done(struct ui_window *window) {}

// no toolkit animates or clamps: the offset is reached at once
void records_scroll_changed(struct ui_view *view, bool animated)
{
	atl_upcall_scrolled(view->ref, view->scroll_x, view->scroll_y);
}
void records_window_closed(struct ui_window *window) {}

void records_view_freed(struct ui_view *view)
{
	headless_input_view_freed(view);
	headless_edit_view_freed(view);
	free(view);
}

void records_frame_changed(struct ui_window *window)
{
	const struct ui_frame *frame = &window->frame;
	if (frame->has_color)
		printf("window: frame #%08x%s", frame->color, frame->light ? " light" : "");
	else
		printf("window: frame none");
	printf("%s\n", frame->fullscreen ? " fullscreen" : "");
	fflush(stdout);
}

static int utf8_length(const char *text, const char *end)
{
	int length = 0;
	for (; text < end && *text; text++)
		length += (*text & 0xC0) != 0x80;
	return length;
}

// greedily wrapped, with a button's control beside the text
static void line_extent(struct ui_view *view, const char *text, const char *end, int max_width, int *width, int *height)
{
	float char_width = CHAR_WIDTH_PER_SIZE * view->text_size;
	int line_height = ceilf(LINE_HEIGHT_PER_SIZE * view->text_size);
	int control = 0;
	if (view->is_button && (view->button == UI_BUTTON_CHECK || view->button == UI_BUTTON_RADIO))
		control = line_height;
	else if (view->is_button && view->button == UI_BUTTON_SWITCH)
		control = 2 * line_height;
	if (max_width >= 0)
		max_width = max_width > control ? max_width - control : 0;
	int length = utf8_length(text, end);
	int lines = 1;
	*width = ceilf(length * char_width);
	if (max_width >= 0 && *width > max_width) {
		int chars_per_line = floorf(max_width / char_width);
		if (chars_per_line > 0)
			lines = (length + chars_per_line - 1) / chars_per_line;
		*width = max_width;
	}
	*width += control;
	*height = lines * line_height;
}

// an edit node is at least as wide as its hint, and its lines break at each newline
void records_text_extent(struct ui_view *view, int max_width, int *width, int *height)
{
	if (!view->edit) {
		line_extent(view, view->text, view->text + strlen(view->text), max_width, width, height);
		return;
	}
	const char *hint = view->edit_config.hint;
	*width = *height = 0;
	for (const char *line = view->text;;) {
		const char *end = strchr(line, '\n');
		if (!end || !view->edit_config.multiline)
			end = line + strlen(line);
		int line_width, line_height;
		line_extent(view, line, end, max_width, &line_width, &line_height);
		*width = line_width > *width ? line_width : *width;
		*height += line_height;
		if (!*end)
			break;
		line = end + 1;
	}
	if (hint) {
		int hint_width, hint_height;
		line_extent(view, hint, hint + strlen(hint), max_width, &hint_width, &hint_height);
		*width = hint_width > *width ? hint_width : *width;
		*height = hint_height > *height ? hint_height : *height;
	}
}
