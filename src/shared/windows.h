#ifndef SHARED_WINDOWS_H
#define SHARED_WINDOWS_H

/* Windows an app shows over its activities (a Dialog's, WindowManager.addView's): each belongs to
 * an activity (AOSP's token) and shows while it shows, or to the app (TYPE_APPLICATION_OVERLAY,
 * TYPE_TOAST) while its task is in front; a sub-panel shows with the window it is attached to. The
 * ui slot gets the shown ones (ui_window_set_roots). Android main thread. */

#include <stdbool.h>
#include <stddef.h>

#include "gravity.h"

struct atl_app;
struct atl_subwindow;
struct atl_touch;
struct atl_window_state;
struct ui_root;
struct ui_window;

/* Measures and places the window's root as WindowManager would and tells its Java Window; the
 * backend lays the root out at the frame's size. */
void windows_place(struct atl_subwindow *window, int screen_width, int screen_height, struct atl_window_frame *frame);
/* The frame windows_place last applied the window's gravity in: for a panel alert, its chrome's room. */
const struct atl_window_frame *windows_container(const struct atl_subwindow *window);
/* Where windows_place last put it, moved up by its pan: its root, and with a panel alert's chrome
 * around it the panel. */
const struct atl_window_frame *windows_frame(const struct atl_subwindow *window);
const struct atl_window_frame *windows_panel(const struct atl_subwindow *window);
/* Its pan changed (Window.set_soft_input). */
void windows_panned(struct atl_subwindow *window);
/* The room a window (NULL: the shown activity's) lays out in: the app's window, without the keyboard
 * when it resizes for it (adjustResize); and how far its root moves up for the keyboard (adjustPan). */
void windows_area(struct atl_app *app, const struct atl_subwindow *window, int *width, int *height);
int windows_pan(struct atl_app *app, const struct atl_subwindow *window);
/* AOSP's input dispatch at a DOWN over the roots the backend shows (bottom first): the topmost
 * touchable one under the point (its panel, chrome included), else the topmost touch-modal one;
 * NULL: the shown activity. */
const struct ui_root *windows_touch_target(const struct ui_root *roots, size_t n, float x, float y);
/* AOSP's ACTION_OUTSIDE of a DOWN that went to target (NULL: the shown activity): one event for each
 * window above it that watches outside touches, topmost first, with the DOWN's first pointer in that
 * window's coordinates; the windows' indexes into roots in *indexes. Returns how many (at most n). */
size_t windows_outside_touches(const struct ui_root *roots, size_t n, const struct ui_root *target, const struct atl_touch *down,
                               size_t *indexes, struct atl_touch *touches);
/* The point is on the window's chrome, outside its root: the backend's own, no view's. */
bool windows_in_chrome(const struct atl_subwindow *window, float x, float y);
/* A panel alert's content changed: its chrome is placed again. */
void windows_chrome_changed(struct ui_window *chrome);

struct atl_window_state *windows_state(struct atl_subwindow *window);
/* A window's state, or which windows are drawn, changed: the host gets the drawn windows' together
 * (keep-screen-on and fullscreen if any asks, the topmost brightness, every exclusion rect). */
void windows_state_changed(struct atl_app *app);
/* The drawn windows' exclusion rects in the app's window, the shown activity's first; *n rects,
 * malloc'd. */
int *windows_exclusion_rects(struct atl_app *app, int *n);
/* The host's insets changed: each drawn window over the activities takes them. */
void windows_dispatch_insets(struct atl_app *app);
/* The app's configuration changed: each added window's root hears it (Window.atlConfigurationChanged
 * decides for its owner), a hidden one too. */
void windows_dispatch_configuration(struct atl_app *app);

#endif
