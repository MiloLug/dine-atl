#ifndef UI_RECORDS_H
#define UI_RECORDS_H

#include <stdbool.h>

/* View records without a toolkit (ui.c), shared by the ui backends that draw no views. Each of
 * them defines struct ui_dialog_state and the ui_dialog_* functions. */

struct ui_view;
struct ui_dialog_state;

struct ui_window {
	struct ui_view *shown;
	bool layout_pending;
	int passes_in_a_row;
	bool ready;
	struct ui_dialog_state *dialog; // NULL for the app's window
};

#endif
