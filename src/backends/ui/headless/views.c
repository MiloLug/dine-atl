#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/input.h"
#include "src/slots/gfx.h"
#include "src/slots/loop.h"

#include "../records/records.h"
#include "input.h"

/* No toolkit: the records are all there is. Text measures in the record gfx backend's fixed
 * metrics, which keep dumps exact. */

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
void records_view_drawn(struct ui_view *view) {}
void records_draw_done(struct ui_window *window) {}

static void draw_pass(void *window)
{
	records_draw_pass(window);
}

// unpaced: no display to wait for
void records_draw_wanted(struct ui_window *window)
{
	loop_main_post(draw_pass, window);
}

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

// a paragraph's width and line count, broken as a Layout breaks it
static void paragraph_extent(const struct gfx_paint *paint, const char *text, const char *end, int max_width, int *width, int *lines)
{
	size_t len;
	uint16_t *units = atl_utf16_from_utf8(text, end - text, &len);
	*width = ceilf(gfx_text_measure(paint, units, len));
	*lines = 1;
	if (max_width >= 0 && *width > max_width) {
		size_t *ends;
		*lines = gfx_text_break_lines(paint, units, len, max_width, &ends);
		free(ends);
		*width = max_width;
	}
	free(units);
}

// paragraphs end at each newline, but a single-line edit's don't; a button's control is beside them
static void text_extent(struct ui_view *view, const char *text, int max_width, int *width, int *height)
{
	struct gfx_paint *paint = gfx_paint_new();
	gfx_paint_set_text_size(paint, view->text_size);
	struct gfx_font_metrics metrics;
	gfx_text_font_metrics(paint, &metrics);
	int line_height = ceilf(metrics.descent - metrics.ascent);
	int control = 0;
	if (view->is_button && (view->button == UI_BUTTON_CHECK || view->button == UI_BUTTON_RADIO))
		control = line_height;
	else if (view->is_button && view->button == UI_BUTTON_SWITCH)
		control = 2 * line_height;
	if (max_width >= 0)
		max_width = max_width > control ? max_width - control : 0;
	bool paragraphs = !view->edit || view->edit_config.multiline;
	int lines = 0;
	*width = 0;
	for (const char *start = text;;) {
		const char *end = paragraphs ? strchr(start, '\n') : NULL;
		if (!end)
			end = start + strlen(start);
		int paragraph_width, paragraph_lines;
		// a "\r\n" ends the paragraph as one, as in a Layout
		const char *content_end = *end && end > start && end[-1] == '\r' ? end - 1 : end;
		paragraph_extent(paint, start, content_end, max_width, &paragraph_width, &paragraph_lines);
		*width = paragraph_width > *width ? paragraph_width : *width;
		lines += paragraph_lines;
		if (!*end)
			break;
		start = end + 1;
	}
	gfx_paint_free(paint);
	if (view->max_lines >= 0 && lines > view->max_lines)
		lines = view->max_lines;
	if (lines < view->min_lines)
		lines = view->min_lines;
	*width += control;
	*height = lines * line_height;
}

// an edit node is at least as wide and tall as its hint
void records_text_extent(struct ui_view *view, int max_width, int *width, int *height)
{
	text_extent(view, view->text, max_width, width, height);
	const char *hint = view->edit ? view->edit_config.hint : NULL;
	if (hint) {
		int hint_width, hint_height;
		text_extent(view, hint, max_width, &hint_width, &hint_height);
		*width = hint_width > *width ? hint_width : *width;
		*height = hint_height > *height ? hint_height : *height;
	}
}
