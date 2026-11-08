#ifndef UI_RECORDS_H
#define UI_RECORDS_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <time.h>

#include "src/slots/ui.h"

/* View records (ui.c) for headless and uikit: tree, layout pass, dump. A backend adds the hooks
 * below, struct ui_dialog_state and ui_dialog_*. */

struct atl_ref;
struct atl_touch;
struct atl_key;
struct atl_edit_report;
struct ui_dialog_state;

enum node_kind {
	NODE_VIEW, // ui_view_new: laid out by Java
	NODE_TEXT, // ui_text_new, ui_button_new, ui_edit_new: measured by the backend
	NODE_IMAGE,    // ui_image_new: the drawable's intrinsic size
	NODE_PROGRESS, // ui_progress_new
	NODE_SCROLL,   // ui_scroll_new: laid out by Java; the toolkit scrolls its child
};

struct atl_app;

struct ui_view {
	struct ui_window *window; // its app's, from the thread that made it
	enum node_kind kind;
	char *class_name;
	struct atl_ref *ref;
	struct ui_view **children;
	size_t n_children;
	int padding_left, padding_top, padding_right, padding_bottom;
	int left, top, right, bottom;
	char *text;
	float text_size; // px
	int min_lines, max_lines; // ui_text_set_lines'
	int text_style;  // UI_TEXT_BOLD, UI_TEXT_ITALIC
	struct ui_text_run *runs; // ui_text_set_styled's, owned
	size_t n_runs;
	int alignment;   // UI_TEXT_ALIGN_*
	bool is_button;  // ui_button_new: button, pressed and checked mean something
	enum ui_button button;
	bool pressed, checked;
	bool edit;              // ui_edit_new: text and selection are the field's, as last known here
	int sel_start, sel_end; // UTF-16 code units
	uint32_t edit_seq;      // headless: the field's last report; uikit counts on UIKit's thread
	struct ui_edit_config edit_config; // hint: owned
	bool selectable;
	int visibility;  // UI_VIEW_VISIBLE, INVISIBLE or GONE
	float alpha;
	bool enabled;
	struct gfx_drawable *background; // held; Java's draw() draws it into the list
	bool has_background;             // a color or a drawable: a push button drops its border
	struct gfx_drawable *image;      // NODE_IMAGE, held; ImageView's draw() draws it
	int image_width, image_height;   // px, -1: none
	int scale_type;
	float fraction; // NODE_PROGRESS
	bool indeterminate;
	int scroll_x, scroll_y; // px, View.scrollTo's or the toolkit's
	bool horizontal;        // NODE_SCROLL's axis
	bool draws;                 // its class draws (traits); a background or image draws too
	bool dirty;                 // new or invalidated since its last draw
	int drawn_width, drawn_height;
	struct gfx_canvas *list;    // what its last draw recorded; NULL before one
	void *native;    // the backend's object; uikit: a UIView, touched only on UIKit's thread
};

struct ui_observation {
	struct ui_observation *next;
	struct ui_window *window; // NULL once the window is freed
	struct atl_ref *observer;
	enum ui_observe what;
};

struct ui_window {
	struct atl_app *app;
	struct ui_view *shown;
	struct ui_observation *observations;
	bool layout_pending;
	bool draw_pending;
	bool frames_moved; // a view laid out or moved since the frames last went out (a list's scroll or fill)
	int passes_in_a_row;
	bool ready;
	struct ui_frame frame;
	struct ui_dialog_state *dialog; // an alert's (ui_dialog_new); NULL for the app's window
	/* The windows over the shown activity (ui_window_set_roots), laid out and drawn in the app
	 * window's pass after the activity, their views' window still the app's (view_new). */
	struct ui_root *roots;
	size_t n_roots;
	struct ui_view *touched; // the root the gesture's DOWN went to; NULL: the activity
	bool touch_dropped;      // that root went mid-gesture: the rest goes nowhere
	void *surface; // an app window's platform view (uikit: a retained UIView), else NULL
};

/* What the backend does besides keeping the records, after the records changed. The view and
 * property hooks also run on app threads (apps build views off the main one); the window, layout
 * and draw hooks only on the window's app's main thread. */
void records_window_created(struct ui_window *window, void *surface);
void records_view_created(struct ui_view *view);
void records_child_added(struct ui_view *parent, struct ui_view *child, size_t index);
void records_child_removed(struct ui_view *parent, struct ui_view *child);
/* The record's fields are freed; the backend frees the record once nothing it queued reads it. */
void records_view_freed(struct ui_view *view);
void records_root_added(struct ui_window *window, struct ui_view *root);
void records_root_shown(struct ui_window *window, struct ui_view *root);
void records_root_removed(struct ui_window *window, struct ui_view *root);
void records_frame_changed(struct ui_window *window); // window->frame
void records_window_closed(struct ui_window *window); // the last hook for this window
void records_padding_changed(struct ui_view *view);
void records_background_changed(struct ui_view *view, uint32_t argb);
void records_visibility_changed(struct ui_view *view); // visibility, alpha, enabled
void records_progress_changed(struct ui_view *view);   // NODE_PROGRESS: fraction, indeterminate
// NODE_TEXT only
void records_text_changed(struct ui_view *view); // text, size, style, alignment or lines
void records_button_changed(struct ui_view *view); // pressed or checked
void records_text_color_changed(struct ui_view *view, uint32_t argb);
void records_selectable_changed(struct ui_view *view);
// edit nodes only
void records_edit_config_changed(struct ui_view *view);
/* Java's snapshot (ui_edit_set_state); the backend puts it in the record with records_edit_set, at
 * once (uikit: a dropped one is followed by the newer report) or once its field takes it. */
void records_edit_state_changed(struct ui_view *view, const char *text, int start, int end, uint32_t ack);
void records_edit_set(struct ui_view *view, const char *text, int start, int end);
bool records_edit_keyboard(struct ui_view *view, bool shown);
/* On the Android main thread: the field's report goes into the record, then to Java. */
void records_edit_report(struct ui_view *view, const struct atl_edit_report *report);
/* ui_dialog_free's part: the window's observations wait for their ui_window_unobserve. */
void records_observations_orphan(struct ui_window *window);
/* The windows over the activity changed (window->roots): the backend shows them. */
void records_roots_changed(struct ui_window *window);
/* An alert the backend keeps (ui_dialog_show's is valid only during the call): copy owns its
 * strings until clear. */
void records_alert_copy(struct ui_alert *copy, const struct ui_alert *alert);
void records_alert_clear(struct ui_alert *alert);
/* The text's size in px within max_width (-1: unbounded), without padding. */
void records_text_extent(struct ui_view *view, int max_width, int *width, int *height);
/* How often ui_view_measure found a text's size cached, and computed it. */
void records_measure_stats(unsigned long *hits, unsigned long *misses);
/* A layout pass ended; the shown root's frames are current. */
void records_layout_done(struct ui_window *window);
/* A view was invalidated: the backend runs records_draw_pass once, at once (headless) or at the
 * next frame (uikit). */
void records_draw_wanted(struct ui_window *window);
void records_draw_pass(struct ui_window *window); // ui.c
/* The view's list is new, or NULL: it shows nothing now. */
void records_view_drawn(struct ui_view *view);
void records_draw_done(struct ui_window *window); // a draw pass ended
/* View.scrollTo moved scroll_x and scroll_y (animated: smoothScrollTo). The backend reports the
 * offsets its toolkit reaches with atl_upcall_scrolled. */
void records_scroll_changed(struct ui_view *view, bool animated);

/* The app's window, on the Android main thread: input goes to its shown activity. */
void records_touch(struct ui_window *window, const struct atl_touch *touch);
/* A DOWN that a native control took (not records_touch's): the windows that watch for it get
 * ACTION_OUTSIDE. */
void records_touch_outside(struct ui_window *window, const struct atl_touch *down);
void records_key(struct ui_window *window, const struct atl_key *key);

#ifdef ATL_TEST_HOOKS
/* With ATL_DUMP_VIEWTREE set, prints a "viewfreed: " line with the view's class and text. */
void records_test_view_freed(const struct ui_view *view);
/* With ATL_DUMP_DISPLAYLIST set, prints the display list the view's draw just recorded. */
void records_test_view_drawn(const struct ui_view *view);
/* A pass that drew a view, timed on the Android thread: while such passes come at most 500 ms
 * apart, an "atl frames" line every 5 s with their count and draw times. */
void records_test_frame_drawn(const struct timespec *start, const struct timespec *end);
#else
static inline void records_test_view_freed(const struct ui_view *view) {}
static inline void records_test_view_drawn(const struct ui_view *view) {}
static inline void records_test_frame_drawn(const struct timespec *start, const struct timespec *end) {}
#endif

#endif
