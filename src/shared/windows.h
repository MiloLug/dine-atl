#ifndef SHARED_WINDOWS_H
#define SHARED_WINDOWS_H

/* Windows an app shows over its activities (a Dialog's; popups later): each belongs to an activity
 * (AOSP's token) and shows while it shows, or to the app (TYPE_APPLICATION_OVERLAY) while its task
 * is in front. The ui slot gets the shown ones (ui_window_set_roots). Android main thread. */

#include <stdbool.h>
#include <stddef.h>

struct atl_subwindow;
struct ui_root;
struct ui_window;

struct atl_window_frame {
	int left, top, width, height; // px in the app's window
};

/* Measures and places the window's root as WindowManager would and tells its Java Window; the
 * backend lays the root out at the frame's size. */
void windows_place(struct atl_subwindow *window, int screen_width, int screen_height, struct atl_window_frame *frame);
/* Where windows_place last put it: its root, and with a panel alert's chrome around it the panel. */
const struct atl_window_frame *windows_frame(const struct atl_subwindow *window);
const struct atl_window_frame *windows_panel(const struct atl_subwindow *window);
/* AOSP's input dispatch at a DOWN over the roots the backend shows (bottom first): the topmost
 * touchable one under the point (its panel, chrome included), else the topmost touch-modal one;
 * NULL: the shown activity. */
const struct ui_root *windows_touch_target(const struct ui_root *roots, size_t n, float x, float y);
/* The point is on the window's chrome, outside its root: the backend's own, no view's. */
bool windows_in_chrome(const struct atl_subwindow *window, float x, float y);
/* A panel alert's content changed: its chrome is placed again. */
void windows_chrome_changed(struct ui_window *chrome);

#endif
