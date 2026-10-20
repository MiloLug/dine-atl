#ifndef UI_RECORDS_H
#define UI_RECORDS_H

#include <stdbool.h>
#include <stddef.h>

/* View records without a toolkit (ui.c), shared by the ui backends that draw no views. Each of
 * them defines struct ui_dialog_state and the ui_dialog_* functions. */

struct atl_ref;
struct ui_dialog_state;

enum node_kind {
	NODE_VIEW, // ui_view_new: laid out by Java
	NODE_TEXT, // ui_text_new: measured here
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
	float text_size;
};

struct ui_window {
	struct ui_view *shown;
	bool layout_pending;
	int passes_in_a_row;
	bool ready;
	struct ui_dialog_state *dialog; // NULL for the app's window
};

#endif
