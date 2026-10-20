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
struct ui_dialog_state;

enum node_kind {
	NODE_VIEW, // ui_view_new: laid out by Java
	NODE_TEXT, // ui_text_new: measured by the backend
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
	void *native;    // the backend's object; uikit: a UIView, touched only on UIKit's thread
};

struct ui_window {
	struct ui_view *shown;
	bool layout_pending;
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
// NODE_TEXT only
void records_text_changed(struct ui_view *view); // text, size or style
void records_text_color_changed(struct ui_view *view, uint32_t argb);
/* The text's size in px within max_width (-1: unbounded), without padding. */
void records_text_extent(struct ui_view *view, int max_width, int *width, int *height);
/* A layout pass ended; the shown root's frames are current. */
void records_layout_done(struct ui_window *window);

#ifdef ATL_TEST_HOOKS
/* With ATL_DUMP_VIEWTREE set, prints a "viewfreed: " line with the view's class and text. */
void records_test_view_freed(const struct ui_view *view);
#else
static inline void records_test_view_freed(const struct ui_view *view) {}
#endif

#endif
