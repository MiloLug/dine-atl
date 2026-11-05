#ifndef SHARED_UPCALL_H
#define SHARED_UPCALL_H

/* What backends call in shared code: upcalls into Java and app state. Backends see no JNI. */

#include <stdbool.h>

struct atl_app;
struct atl_ref;
struct loop_main;
struct ui_window;
struct ui_display;
struct gfx_canvas;

/* Another weak reference to the same object; atl_ref_free drops one. */
struct atl_ref *atl_ref_dup(struct atl_ref *ref);
void atl_ref_free(struct atl_ref *ref);

/* A window's size and density, as a host asks for it. */
struct atl_window_config {
	int width, height; // px
	int density_dpi;
	int night; // Configuration.UI_MODE_NIGHT_* value, or UI_DISPLAY_UNKNOWN
};
/* The screen the host gave the VM: every app's window has its size until a window per task (MA6). */
const struct atl_window_config *atl_screen_get(void);

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
/* View.atlDraw: the view's draw() into canvas, which only that call uses; an exception is printed
 * and cleared. */
void atl_upcall_view_draw(struct atl_ref *view, struct gfx_canvas *canvas);
/* The app ended: its window closed or its instrumentation finished. Any thread. */
void atl_app_end(struct atl_app *app);
/* The app's main loop, NULL before its start. Any thread. */
struct loop_main *atl_app_loop(struct atl_app *app);
/* The ui backend's state for the app, NULL until the backend sets it. */
void *atl_app_ui(struct atl_app *app);
void atl_app_set_ui(struct atl_app *app, void *ui);
/* The window of the app the calling thread belongs to, NULL on a thread of no app: for the views an
 * app makes. */
struct ui_window *atl_thread_app_window(void);
struct ui_window *atl_app_window(struct atl_app *app);
/* The app's window got its first layout: the activities regain focus. */
void atl_upcall_window_ready(struct atl_app *app);
/* A dialog's button (DialogInterface.BUTTON_*), item index or UI_DIALOG_CANCEL. Returns whether the
 * dialog is still drawn afterwards; false after an exception, which is printed and cleared. */
bool atl_upcall_dialog_result(struct atl_ref *dialog, int which);
/* The toolkit's own activation of a control (iOS accessibility): View.performClick, as AOSP's
 * ACTION_CLICK, only for a clickable view. */
void atl_upcall_click(struct atl_ref *view);
/* ViewTreeObserver.dispatchOnGlobalLayout and dispatchOnPreDraw (ui_window_observe). */
void atl_upcall_global_layout(struct atl_ref *observer);
void atl_upcall_pre_draw(struct atl_ref *observer);

#endif
