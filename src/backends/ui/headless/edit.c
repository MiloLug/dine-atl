#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/input.h"
#include "src/slots/loop.h"

#include "../records/records.h"
#include "input.h"

/* The keyboard's field, edited by the cli host's text lines as a toolkit edits its own: at once,
 * then reported. The record holds what the field shows, marked text included. */

struct held_report {
	struct held_report *next;
	struct ui_view *view;
	struct atl_edit_report report;
};

// the Android main thread's
static struct ui_view *target;
static bool marked; // marked text spans [mark_start, mark_end)
static int mark_start, mark_end;
static char *before_marked; // the state the composition started from
static int before_start, before_end;
static bool holding;
static struct held_report *held;

// a newline as \n, to keep one line
static void print_state(const struct ui_view *view)
{
	printf("edit: \"");
	for (const char *c = view->text; *c; c++) {
		if (*c == '\n')
			fputs("\\n", stdout);
		else
			putchar(*c);
	}
	printf("\" %d,%d\n", view->sel_start, view->sel_end);
	fflush(stdout);
}

static void print_keyboard(const struct ui_view *view, bool shown)
{
	printf("keyboard: %s %s \"%s\"\n", shown ? "on" : "off", view->class_name, view->edit_config.hint ?: "");
	fflush(stdout);
}

static void deliver(struct ui_view *view, struct atl_edit_report *report)
{
	records_edit_report(view, report);
	free((char *)report->prev);
	free((char *)report->text);
}

static void report(struct ui_view *view, const char *prev, int prev_start, int prev_end)
{
	struct atl_edit_report report = {strdup(prev), prev_start, prev_end, strdup(view->text), view->sel_start, view->sel_end, ++view->edit_seq};
	if (!holding) {
		deliver(view, &report);
		return;
	}
	struct held_report **tail = &held;
	while (*tail)
		tail = &(*tail)->next;
	*tail = malloc(sizeof(**tail));
	**tail = (struct held_report){NULL, view, report};
}

static void release(void)
{
	holding = false;
	while (held) {
		struct held_report *entry = held;
		held = entry->next;
		deliver(entry->view, &entry->report);
		free(entry);
	}
}

// the marked text stays as it is and goes to Java, from the state before the composition
static void unmark(struct ui_view *view)
{
	if (!marked)
		return;
	marked = false;
	report(view, before_marked, before_start, before_end);
	free(before_marked);
	before_marked = NULL;
}

void records_edit_state_changed(struct ui_view *view, const char *text, int start, int end, uint32_t ack)
{
	if (ack == view->edit_seq && !(marked && view == target)) {
		records_edit_set(view, text, start, end);
		print_state(view);
		return;
	}
	printf("edit: drop %u\n", ack);
	fflush(stdout);
}

bool records_edit_keyboard(struct ui_view *view, bool shown)
{
	if (shown && target != view) {
		if (target) {
			unmark(target);
			print_keyboard(target, false);
		}
		target = view;
		print_keyboard(view, true);
	} else if (!shown && target == view) {
		unmark(view);
		target = NULL;
		print_keyboard(view, false);
	}
	return true;
}

void records_edit_config_changed(struct ui_view *view) {}

void headless_edit_view_freed(struct ui_view *view)
{
	if (target == view) {
		target = NULL;
		marked = false;
		free(before_marked);
		before_marked = NULL;
	}
	for (struct held_report **link = &held; *link;) {
		struct held_report *entry = *link;
		if (entry->view != view) {
			link = &entry->next;
			continue;
		}
		*link = entry->next;
		free((char *)entry->report.prev);
		free((char *)entry->report.text);
		free(entry);
	}
}

static int edit_length(const struct ui_view *view)
{
	return atl_utf16_length(view->text, strlen(view->text));
}

static int clamp(int value, int max)
{
	return value < 0 ? 0 : value > max ? max : value;
}

// [start, end) in UTF-16 becomes insert; the caret goes after it
static void replace(struct ui_view *view, int start, int end, const char *insert)
{
	const char *text = view->text;
	size_t from = atl_utf8_offset(text, start), to = atl_utf8_offset(text, end);
	size_t insert_size = strlen(insert);
	char *replaced = malloc(strlen(text) - (to - from) + insert_size + 1);
	memcpy(replaced, text, from);
	memcpy(replaced + from, insert, insert_size);
	strcpy(replaced + from + insert_size, text + to);
	free(view->text);
	view->text = replaced;
	view->sel_start = view->sel_end = atl_utf16_length(replaced, from + insert_size);
}

static void edit(struct ui_view *view, const struct headless_text *line)
{
	if (line->what != HEADLESS_TEXT_COMPOSE && line->what != HEADLESS_TEXT_COMMIT)
		unmark(view);
	char *prev = strdup(view->text);
	int prev_start = view->sel_start, prev_end = view->sel_end;
	int start = prev_start < prev_end ? prev_start : prev_end;
	int end = prev_start < prev_end ? prev_end : prev_start;
	int length = edit_length(view);
	switch (line->what) {
		case HEADLESS_TEXT_COMPOSE:
			if (!marked) {
				before_marked = strdup(prev);
				before_start = prev_start;
				before_end = prev_end;
			} else {
				start = mark_start, end = mark_end;
			}
			replace(view, start, end, line->text);
			marked = true;
			mark_start = start;
			mark_end = view->sel_end;
			break;
		case HEADLESS_TEXT_COMMIT:
			if (marked)
				start = mark_start, end = mark_end;
			replace(view, start, end, line->text);
			break;
		case HEADLESS_TEXT_DELETE:
			if (start == end) // else the selection goes alone, as with backspace
				start = clamp(start - line->a, length), end = clamp(end + line->b, length);
			replace(view, start, end, "");
			break;
		case HEADLESS_TEXT_SELECT:
			view->sel_start = clamp(line->a, length);
			view->sel_end = clamp(line->b, length);
			break;
		default:
			break;
	}
	print_state(view);
	if (line->what == HEADLESS_TEXT_COMMIT && marked)
		unmark(view);
	else if (line->what != HEADLESS_TEXT_COMPOSE)
		report(view, prev, prev_start, prev_end);
	free(prev);
}

// the edit node a point in the parent's content hits
static struct ui_view *edit_at(struct ui_view *view, float x, float y)
{
	if (view->edit)
		return view;
	float local_x = x - view->left + view->scroll_x;
	float local_y = y - view->top + view->scroll_y;
	for (size_t i = view->n_children; i-- > 0;) {
		struct ui_view *child = view->children[i];
		if (child->visibility == UI_VIEW_VISIBLE && local_x >= child->left && local_x < child->right && local_y >= child->top && local_y < child->bottom)
			return edit_at(child, local_x, local_y);
	}
	return NULL;
}

static void tap(float x, float y)
{
	struct ui_window *window = records_app_window();
	struct ui_view *view = window && window->shown ? edit_at(window->shown, x, y) : NULL;
	if (!view) {
		printf("text: no field at %g,%g\n", x, y);
		fflush(stdout);
		return;
	}
	if (!view->enabled)
		return;
	records_edit_keyboard(view, true);
	atl_upcall_edit_focus(view->ref, true, true);
}

static void on_text(void *data)
{
	struct headless_text *line = data;
	struct ui_view *view = target;
	if (line->what == HEADLESS_TEXT_HOLD) {
		holding = true;
	} else if (line->what == HEADLESS_TEXT_RELEASE) {
		release();
	} else if (line->what == HEADLESS_TEXT_TAP) {
		tap(line->x, line->y);
	} else if (!view) {
		printf("text: no field\n");
		fflush(stdout);
	} else if (line->what == HEADLESS_TEXT_ACTION && !view->edit_config.multiline) {
		unmark(view);
		atl_upcall_edit_action(view->ref);
	} else if (line->what == HEADLESS_TEXT_ACTION) {
		edit(view, &(struct headless_text){.what = HEADLESS_TEXT_COMMIT, .text = "\n"});
	} else if (line->what == HEADLESS_TEXT_DISMISS) {
		records_edit_keyboard(view, false);
		atl_upcall_edit_focus(view->ref, false, false);
	} else {
		edit(view, line);
	}
	free(line);
}

void headless_text(const struct headless_text *line)
{
	size_t size = strlen(line->text) + 1;
	struct headless_text *posted = malloc(sizeof(*posted) + size);
	*posted = *line;
	posted->text = memcpy((char *)(posted + 1), line->text, size);
	if (!loop_main_post(on_text, posted))
		free(posted);
}
