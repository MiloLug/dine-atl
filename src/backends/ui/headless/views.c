#include <math.h>
#include <stdio.h>
#include <stdlib.h>

#include "../records/records.h"

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
void records_text_color_changed(struct ui_view *view, uint32_t argb) {}
void records_layout_done(struct ui_window *window) {}
void records_window_closed(struct ui_window *window) {}

void records_view_freed(struct ui_view *view)
{
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

static int utf8_length(const char *text)
{
	int length = 0;
	for (; *text; text++)
		length += (*text & 0xC0) != 0x80;
	return length;
}

// greedily wrapped
void records_text_extent(struct ui_view *view, int max_width, int *width, int *height)
{
	float char_width = CHAR_WIDTH_PER_SIZE * view->text_size;
	int length = utf8_length(view->text);
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
