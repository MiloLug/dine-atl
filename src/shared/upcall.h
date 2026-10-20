#ifndef SHARED_UPCALL_H
#define SHARED_UPCALL_H

/* What backends call in shared code: upcalls into Java and app state. Backends see no JNI. */

#include <stdbool.h>

struct atl_ref;
struct ui_display;

/* Another weak reference to the same object; atl_ref_free drops one. */
struct atl_ref *atl_ref_dup(struct atl_ref *ref);
void atl_ref_free(struct atl_ref *ref);

/* The app window the host asked for. */
struct atl_window_config {
	int width, height; // px
	int density_dpi;
	int night; // Configuration.UI_MODE_NIGHT_* value, or UI_DISPLAY_UNKNOWN
};
const struct atl_window_config *atl_window_get(void);

void atl_upcall_looper_loop(void);
/* Applies the known fields to Context.sys_config. */
void atl_upcall_display_changed(const struct ui_display *display);

/* View.measure; an exception is printed and cleared. */
void atl_upcall_view_measure(struct atl_ref *view, int width_spec, int height_spec);
int atl_upcall_view_get_measured(struct atl_ref *view, bool vertical);
int atl_upcall_view_get_suggested_min(struct atl_ref *view, bool vertical);
/* The specs of the view's last measure. */
void atl_upcall_view_get_old_specs(struct atl_ref *view, int *width_spec, int *height_spec);
/* View.layoutInternal: the view's own onLayout at width x height, which lays out its children. */
void atl_upcall_view_layout(struct atl_ref *view, int width, int height);
/* The app's window got its first layout: the activities regain focus. */
void atl_upcall_window_ready(void);
/* A dialog's button (DialogInterface.BUTTON_*), item index or UI_DIALOG_CANCEL. */
void atl_upcall_dialog_result(struct atl_ref *dialog, int which);

#endif
