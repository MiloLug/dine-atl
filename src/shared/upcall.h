#ifndef SHARED_UPCALL_H
#define SHARED_UPCALL_H

/* What backends call in shared code: upcalls into Java and app state. Backends see no JNI. */

#include <stdbool.h>

struct atl_ref;
struct ui_display;

/* Another weak reference to the same object; atl_ref_free drops one. */
struct atl_ref *atl_ref_dup(struct atl_ref *ref);
void atl_ref_free(struct atl_ref *ref);

/* The app window's size the host asked for, px. */
void atl_window_size(int *width, int *height);

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
/* The app's window got its first layout: the activities regain focus, ATL_DUMP_VIEWTREE dumps. */
void atl_upcall_window_ready(void);
/* A dialog's button (DialogInterface.BUTTON_*), item index or UI_DIALOG_CANCEL. */
void atl_upcall_dialog_result(struct atl_ref *dialog, int which);

struct ui_alert;
/* With ATL_DIALOG_ANSWERS set (button or item labels or "!cancel", one per shown alert, separated
 * by ';'): the next answer as a result for alert; exits 1 when it doesn't match. Else false. */
bool atl_dialog_scripted(const struct ui_alert *alert, int *which);

#endif
