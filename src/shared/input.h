#ifndef SHARED_INPUT_H
#define SHARED_INPUT_H

/* Input from the ui backends: Android's events, built here and handed to the window's root view
 * (android.view.ATLInput, ViewRootImpl's part). Backends see no JNI. */

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

struct atl_ref;

#define ATL_POINTERS_MAX 16

struct atl_pointer {
	int id;     // Android's pointer id: the lowest one free when the pointer went down
	float x, y; // px in the window
};

/* A MotionEvent from the touchscreen: every pointer that is down, the changed one at the action's
 * index. */
struct atl_touch {
	int action;        // MotionEvent.ACTION_*, with the pointer index for POINTER_DOWN/UP
	int64_t down_time; // atl_uptime_ms of the gesture's first DOWN
	int64_t time;
	int count;
	struct atl_pointer pointers[ATL_POINTERS_MAX];
};

#define ATL_ACTION_DOWN         0
#define ATL_ACTION_UP           1
#define ATL_ACTION_MOVE         2
#define ATL_ACTION_CANCEL       3
#define ATL_ACTION_POINTER_DOWN 5
#define ATL_ACTION_POINTER_UP   6
#define ATL_ACTION_INDEX_SHIFT  8

/* A KeyEvent from a keyboard. */
struct atl_key {
	int action;  // KeyEvent.ACTION_DOWN or ACTION_UP
	int code;    // KeyEvent.KEYCODE_*
	int meta;    // KeyEvent.META_* flags
	int unicode; // the character it types, or 0
	int repeat;
	int64_t down_time, time; // atl_uptime_ms
};

#define ATL_KEY_DOWN     0
#define ATL_KEY_UP       1
#define ATL_KEYCODE_BACK 4

/* SystemClock.uptimeMillis, from any thread. */
int64_t atl_uptime_ms(void);

/* On the Android main thread; root: the root view of the window the event came to. True when the
 * app consumed it. */
bool atl_upcall_touch(struct atl_ref *root, const struct atl_touch *touch);
bool atl_upcall_key(struct atl_ref *root, const struct atl_key *key);
/* The toolkit's scroll offset of the view changed (View.atlScrolled), px. */
void atl_upcall_scrolled(struct atl_ref *view, int x, int y);

/* An EditText's field went from prev to text by the user's edit; positions are UTF-16 code units,
 * as Java's. seq counts the field's reports: Java's snapshot (ui_edit_set_state) acks the last one
 * it applied. */
struct atl_edit_report {
	const char *prev;
	int prev_start, prev_end;
	const char *text;
	int start, end;
	uint32_t seq;
};
void atl_upcall_edit_report(struct atl_ref *view, const struct atl_edit_report *report);
/* The return key of a single-line field: the IME's editor action. */
void atl_upcall_edit_action(struct atl_ref *view);
/* The toolkit gave the field its input target, a tap for one (keyboard: a keyboard shows), or took
 * it away; Java's focus follows the first. */
void atl_upcall_edit_focus(struct atl_ref *view, bool focused, bool keyboard);

/* The byte offset in UTF-8 text of a UTF-16 position, clamped to the text's end. */
size_t atl_utf8_offset(const char *text, int utf16);
/* The UTF-16 length of text's first bytes. */
int atl_utf16_length(const char *text, size_t bytes);

#endif
