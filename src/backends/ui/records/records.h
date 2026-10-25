#ifndef UI_RECORDS_H
#define UI_RECORDS_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "src/slots/ui.h"

/* View records (ui.c) for headless and uikit: tree, layout pass, dump. A backend adds the hooks
 * below, struct ui_dialog_state and ui_dialog_*. No field may depend on ATL_TEST_HOOKS: ObjC
 * sources don't get that define. */

struct atl_ref;
struct atl_touch;
struct atl_key;
struct atl_edit_report;
struct ui_dialog_state;

enum node_kind {
	NODE_VIEW, // ui_view_new: laid out by Java
	NODE_TEXT, // ui_text_new, ui_button_new, ui_edit_new: measured by the backend
	NODE_IMAGE,    // ui_image_new: the drawable's intrinsic size; nothing draws it before M5b
	NODE_PROGRESS, // ui_progress_new
	NODE_SCROLL,   // ui_scroll_new: laid out by Java; the toolkit scrolls its child
};

struct ui_view {
	enum node_kind kind;
	char *class_name;
	struct atl_ref *ref;
	struct ui_view **children;
	size_t n_children;
	int padding_left, padding_top, padding_right, padding_bottom;
	int left, top, right, bottom;
	char *text;
	float text_size; // px
	int text_style;  // UI_TEXT_BOLD, UI_TEXT_ITALIC
	bool markup;
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
	struct gfx_drawable *background; // held; nothing draws it before M5b
	bool has_background;             // a color or a drawable: a push button drops its border
	struct gfx_drawable *image;      // NODE_IMAGE, held
	int image_width, image_height;   // px, -1: none
	int scale_type;
	float fraction; // NODE_PROGRESS
	bool indeterminate;
	int scroll_x, scroll_y; // px, View.scrollTo's or the toolkit's
	bool horizontal;        // NODE_SCROLL's axis
	bool draws;                 // its class draws (traits): the draw pass runs its draw()
	bool dirty;                 // draws: new or invalidated since its last draw
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
	struct ui_view *shown;
	struct ui_observation *observations;
	bool layout_pending;
	bool draw_pending;
	int passes_in_a_row;
	bool ready;
	struct ui_frame frame;
	struct ui_dialog_state *dialog; // NULL for the app's window
};

/* What the backend does besides keeping the records; on the Android main thread, after the
 * records changed. */
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
void records_text_changed(struct ui_view *view); // text, size, style or alignment
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
/* The text's size in px within max_width (-1: unbounded), without padding. */
void records_text_extent(struct ui_view *view, int max_width, int *width, int *height);
/* A layout pass ended; the shown root's frames are current. */
void records_layout_done(struct ui_window *window);
/* View.scrollTo moved scroll_x and scroll_y (animated: smoothScrollTo). The backend reports the
 * offsets its toolkit reaches with atl_upcall_scrolled. */
void records_scroll_changed(struct ui_view *view, bool animated);

/* The app's window, on the Android main thread: input goes to its shown activity. */
struct ui_window *records_app_window(void);
void records_touch(const struct atl_touch *touch);
void records_key(const struct atl_key *key);

#ifdef ATL_TEST_HOOKS
/* With ATL_DUMP_VIEWTREE set, prints a "viewfreed: " line with the view's class and text. */
void records_test_view_freed(const struct ui_view *view);
/* With ATL_DUMP_DISPLAYLIST set, prints the display list the view's draw just recorded. */
void records_test_view_drawn(const struct ui_view *view);
#else
static inline void records_test_view_freed(const struct ui_view *view) {}
static inline void records_test_view_drawn(const struct ui_view *view) {}
#endif

#endif
