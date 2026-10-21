#ifndef HEADLESS_INPUT_H
#define HEADLESS_INPUT_H

/* The cli host's -c input for the shown activity, from any thread: posted to the Android main
 * thread in order. */

enum headless_touch {
	HEADLESS_TOUCH_DOWN,
	HEADLESS_TOUCH_MOVE,
	HEADLESS_TOUCH_UP,
	HEADLESS_TOUCH_CANCEL,
};

/* One pointer changed: id, px in the window. */
void headless_touch(enum headless_touch what, int id, float x, float y);
/* down: KeyEvent.ACTION_DOWN, else ACTION_UP; unicode: the character it types, or 0. */
void headless_key(int down, int code, int meta, int unicode);

struct ui_view;
/* views.c: a record is freed; a gesture on it stops panning it. */
void headless_input_view_freed(struct ui_view *view);

#endif
