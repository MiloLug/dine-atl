#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/input.h"
#include "src/shared/upcall.h"
#include "src/shared/windows.h"
#include "src/slots/loop.h"

#include "../records/records.h"
#include "input.h"

/* No toolkit pans here: a gesture that starts on a scroll node and moves past the touch slop along
 * its axis is taken as UIKit's and GTK's scroll views take it. The app's touch is canceled and the
 * offset follows the first pointer, clamped to the child. */

#define TOUCH_SLOP_DP 8 // ViewConfiguration's

struct posted_touch {
	struct ui_window *window;
	enum headless_touch what;
	int id;
	float x, y;
};

// each app's Android main thread has its own
static __thread struct atl_touch touch; // the pointers that are down
static __thread struct ui_view *scroll; // the gesture's scroll node, or NULL
static __thread bool panning;
static __thread float start_x, start_y;
static __thread int start_scroll_x, start_scroll_y;
static __thread int64_t key_down_time;

static bool contains(const struct ui_view *view, float x, float y)
{
	return view->visibility == UI_VIEW_VISIBLE && x >= view->left && x < view->right && y >= view->top && y < view->bottom;
}

// the innermost scroll node holding the view the point hits; the point is in the parent's content
static struct ui_view *scroll_at(struct ui_view *view, float x, float y)
{
	float local_x = x - view->left + view->scroll_x;
	float local_y = y - view->top + view->scroll_y;
	for (size_t i = view->n_children; i-- > 0;) {
		struct ui_view *child = view->children[i];
		if (contains(child, local_x, local_y)) {
			struct ui_view *found = scroll_at(child, local_x, local_y);
			if (found)
				return found;
			break;
		}
	}
	return view->kind == NODE_SCROLL ? view : NULL;
}

// how far the child and the node's padding reach past the scroll node, px; margins don't count
static int scroll_range(const struct ui_view *view)
{
	if (!view->n_children)
		return 0;
	const struct ui_view *child = view->children[0];
	int extent = view->horizontal ? view->padding_left + child->right - child->left + view->padding_right
	                              : view->padding_top + child->bottom - child->top + view->padding_bottom;
	int size = view->horizontal ? view->right - view->left : view->bottom - view->top;
	return extent > size ? extent - size : 0;
}

static void pan(float x, float y)
{
	int offset = scroll->horizontal ? start_scroll_x + (int)(start_x - x) : start_scroll_y + (int)(start_y - y);
	int range = scroll_range(scroll);
	offset = offset < 0 ? 0 : offset > range ? range : offset;
	int scroll_x = scroll->horizontal ? offset : scroll->scroll_x;
	int scroll_y = scroll->horizontal ? scroll->scroll_y : offset;
	if (scroll_x == scroll->scroll_x && scroll_y == scroll->scroll_y)
		return;
	scroll->scroll_x = scroll_x;
	scroll->scroll_y = scroll_y;
	atl_upcall_scrolled(scroll->ref, scroll_x, scroll_y);
}

static __thread struct ui_window *touch_window; // the gesture's: its first pointer's

static void deliver(int action)
{
	touch.action = action;
	touch.time = atl_uptime_ms();
	records_touch(touch_window, &touch);
}

static int pointer_index(int id)
{
	for (int i = 0; i < touch.count; i++) {
		if (touch.pointers[i].id == id)
			return i;
	}
	return -1;
}

static void pointer_down(const struct posted_touch *posted)
{
	if (pointer_index(posted->id) >= 0 || touch.count == ATL_POINTERS_MAX)
		return;
	int index = touch.count++;
	touch.pointers[index] = (struct atl_pointer){posted->id, posted->x, posted->y};
	if (touch.count > 1) {
		if (!panning)
			deliver(ATL_ACTION_POINTER_DOWN | index << ATL_ACTION_INDEX_SHIFT);
		return;
	}
	touch.down_time = atl_uptime_ms();
	struct ui_window *window = touch_window = posted->window;
	// the root the DOWN goes to, as records_touch picks it
	const struct ui_root *root = windows_touch_target(window->roots, window->n_roots, posted->x, posted->y);
	if (root) {
		const struct atl_window_frame *frame = windows_frame(root->window);
		scroll = scroll_at(root->view, posted->x - frame->left, posted->y - frame->top);
	} else {
		scroll = window->shown ? scroll_at(window->shown, posted->x, posted->y) : NULL;
	}
	panning = false;
	start_x = posted->x;
	start_y = posted->y;
	if (scroll) {
		start_scroll_x = scroll->scroll_x;
		start_scroll_y = scroll->scroll_y;
	}
	deliver(ATL_ACTION_DOWN);
}

static void pointer_move(const struct posted_touch *posted, int index)
{
	touch.pointers[index].x = posted->x;
	touch.pointers[index].y = posted->y;
	if (scroll && !panning && index == 0) {
		float moved = scroll->horizontal ? posted->x - start_x : posted->y - start_y;
		if (fabsf(moved) > TOUCH_SLOP_DP * atl_screen_get()->density_dpi / 160.f) {
			panning = true;
			deliver(ATL_ACTION_CANCEL);
		}
	}
	if (!panning)
		deliver(ATL_ACTION_MOVE);
	else if (index == 0 && scroll)
		pan(posted->x, posted->y);
}

static void pointer_up(const struct posted_touch *posted, int index)
{
	touch.pointers[index].x = posted->x;
	touch.pointers[index].y = posted->y;
	if (posted->what == HEADLESS_TOUCH_CANCEL) {
		if (!panning)
			deliver(ATL_ACTION_CANCEL);
		touch.count = 0;
	} else {
		if (!panning)
			deliver(touch.count == 1 ? ATL_ACTION_UP : ATL_ACTION_POINTER_UP | index << ATL_ACTION_INDEX_SHIFT);
		memmove(&touch.pointers[index], &touch.pointers[index + 1], (touch.count - index - 1) * sizeof(*touch.pointers));
		touch.count--;
	}
	if (!touch.count) {
		scroll = NULL;
		panning = false;
	}
}

static void on_touch(void *data)
{
	struct posted_touch *posted = data;
	int index = pointer_index(posted->id);
	if (posted->what == HEADLESS_TOUCH_DOWN)
		pointer_down(posted);
	else if (index < 0)
		; // not down
	else if (posted->what == HEADLESS_TOUCH_MOVE)
		pointer_move(posted, index);
	else
		pointer_up(posted, index);
	free(posted);
}

void headless_touch(struct ui_window *window, enum headless_touch what, int id, float x, float y)
{
	struct posted_touch *posted = malloc(sizeof(*posted));
	*posted = (struct posted_touch){window, what, id, x, y};
	if (!loop_main_post(atl_app_loop(window->app), on_touch, posted))
		free(posted);
}

struct posted_tap {
	struct ui_window *window;
	char *label;
};

// x, y: the parent's content origin in the window
static bool find_text(const struct ui_view *view, const char *label, float x, float y, float *found_x, float *found_y)
{
	if (view->visibility != UI_VIEW_VISIBLE)
		return false;
	float left = x + view->left, top = y + view->top;
	if (view->kind == NODE_TEXT && view->text && !strcmp(view->text, label)) {
		*found_x = left + (view->right - view->left) / 2.f;
		*found_y = top + (view->bottom - view->top) / 2.f;
		return true;
	}
	for (size_t i = view->n_children; i-- > 0;) {
		if (find_text(view->children[i], label, left - view->scroll_x, top - view->scroll_y, found_x, found_y))
			return true;
	}
	return false;
}

static void on_tap(void *data)
{
	struct posted_tap *tap = data;
	struct ui_window *window = tap->window;
	float x, y;
	bool found = false;
	for (size_t i = window->n_roots; i-- > 0 && !found;) {
		if (!window->roots[i].view) // a plain alert: answer, not tap
			continue;
		const struct atl_window_frame *frame = windows_frame(window->roots[i].window);
		found = find_text(window->roots[i].view, tap->label, frame->left, frame->top, &x, &y);
	}
	if (!found && window->shown)
		found = find_text(window->shown, tap->label, 0, 0, &x, &y);
	if (found) {
		printf("tap: %s %.0f,%.0f\n", tap->label, x, y);
		fflush(stdout);
		for (enum headless_touch what = HEADLESS_TOUCH_DOWN; what <= HEADLESS_TOUCH_UP; what += HEADLESS_TOUCH_UP - HEADLESS_TOUCH_DOWN) {
			struct posted_touch *posted = malloc(sizeof(*posted));
			*posted = (struct posted_touch){window, what, 0, x, y};
			on_touch(posted);
		}
	} else {
		printf("tap: no %s\n", tap->label);
		fflush(stdout);
	}
	free(tap->label);
	free(tap);
}

void headless_tap(struct ui_window *window, const char *label)
{
	struct posted_tap *posted = malloc(sizeof(*posted));
	*posted = (struct posted_tap){window, strdup(label)};
	if (!loop_main_post(atl_app_loop(window->app), on_tap, posted)) {
		free(posted->label);
		free(posted);
	}
}

static void on_dump(void *data)
{
#ifdef ATL_TEST_HOOKS
	ui_window_dump(data, stdout);
#endif
}

void headless_dump(struct ui_window *window)
{
	loop_main_post(atl_app_loop(window->app), on_dump, window);
}

struct posted_key {
	struct ui_window *window;
	struct atl_key key;
};

static void on_key(void *data)
{
	struct posted_key *posted = data;
	struct atl_key *key = &posted->key;
	key->time = atl_uptime_ms();
	if (key->action == ATL_KEY_DOWN)
		key_down_time = key->time;
	key->down_time = key_down_time;
	records_key(posted->window, key);
	free(posted);
}

void headless_key(struct ui_window *window, int down, int code, int meta, int unicode)
{
	struct posted_key *posted = malloc(sizeof(*posted));
	*posted = (struct posted_key){window, {.action = down ? ATL_KEY_DOWN : ATL_KEY_UP, .code = code, .meta = meta, .unicode = unicode}};
	if (!loop_main_post(atl_app_loop(window->app), on_key, posted))
		free(posted);
}

void headless_input_view_freed(struct ui_view *view)
{
	if (scroll == view)
		scroll = NULL;
}
