#ifndef HEADLESS_INPUT_H
#define HEADLESS_INPUT_H

/* The cli host's -c input for the shown activity of a window, from any thread: posted to the
 * Android main thread in order. */

struct ui_window;

enum headless_touch {
	HEADLESS_TOUCH_DOWN,
	HEADLESS_TOUCH_MOVE,
	HEADLESS_TOUCH_UP,
	HEADLESS_TOUCH_CANCEL,
};

/* One pointer changed: id, px in the window. */
void headless_touch(struct ui_window *window, enum headless_touch what, int id, float x, float y);
/* A DOWN and an UP at the middle of the shown text view whose text is label, a dialog's before the
 * activity's; prints "tap: <label> <x>,<y>" or "tap: no <label>". */
void headless_tap(struct ui_window *window, const char *label);
/* The view-tree dump of the window and its shown dialogs (ui_window_dump), now. */
void headless_dump(struct ui_window *window);
/* down: KeyEvent.ACTION_DOWN, else ACTION_UP; unicode: the character it types, or 0. */
void headless_key(struct ui_window *window, int down, int code, int meta, int unicode);

/* A text line for the keyboard's field (edit.c): text for COMMIT and COMPOSE, a and b for DELETE
 * (before, after) and SELECT (start, end) in UTF-16 code units, x and y for TAP (px in the window).
 * HOLD keeps the field's reports from Java until RELEASE. */
enum headless_text_what {
	HEADLESS_TEXT_COMMIT,
	HEADLESS_TEXT_COMPOSE,
	HEADLESS_TEXT_DELETE,
	HEADLESS_TEXT_SELECT,
	HEADLESS_TEXT_ACTION,
	HEADLESS_TEXT_DISMISS,
	HEADLESS_TEXT_TAP,
	HEADLESS_TEXT_HOLD,
	HEADLESS_TEXT_RELEASE,
};
struct headless_text {
	enum headless_text_what what;
	int a, b;
	float x, y;
	const char *text; // not NULL
};
void headless_text(struct ui_window *window, const struct headless_text *line);

struct ui_view;
/* views.c: a record is freed; a gesture on it stops panning it. */
void headless_input_view_freed(struct ui_view *view);
void headless_edit_view_freed(struct ui_view *view);

#endif
