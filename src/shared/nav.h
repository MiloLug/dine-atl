#ifndef ATL_NAV_H
#define ATL_NAV_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

struct atl_app; // a host's handle of an app (atl_embedded.h, startup.h)

/* System navigation for the host, from any thread, for one app. Each posts to the app's Android
 * main thread and is false when the app isn't running. */

/* Back: the BACK key, for the shown dialog, then the focused views and the current activity
 * (onBackPressed). Nothing while the task is in the back. */
bool atl_nav_back(struct atl_app *app);
/* Home: the task to the back (onPause, onStop). Nothing while it is in the back. */
bool atl_nav_home(struct atl_app *app);
/* The task to the front (onRestart, onStart, onResume). Nothing while it is in front. */
bool atl_nav_front(struct atl_app *app);

/* dp from the window's edges */
struct atl_edges {
	float left, top, right, bottom;
};
/* What the host takes from the window's edges, as WindowInsets types; apps decide what to avoid. */
struct atl_window_insets {
	struct atl_edges system_gestures;    // the host's gesture strips
	struct atl_edges mandatory_gestures; // the part apps can't exclude
	struct atl_edges navigation_bars;    // also the system window insets apps read before API 30
};
/* From any thread, also before the app starts: the shown activity gets them now, later ones when
 * shown. */
void atl_nav_set_insets(struct atl_app *app, const struct atl_window_insets *insets);

/* The window's side edges where the app takes the gestures back (View.setSystemGestureExclusionRects):
 * dp in the window, within the system gesture strips, the bottom-most 200 dp per edge. */
struct atl_span {
	float top, bottom;
};
#define ATL_EXCLUSION_SPANS_MAX 16
struct atl_gesture_exclusion {
	int left_count, right_count;
	struct atl_span left[ATL_EXCLUSION_SPANS_MAX], right[ATL_EXCLUSION_SPANS_MAX];
};

/* What ATL tells an embedding host about an app, on its Android main thread. NULL callbacks are
 * skipped. */
struct atl_embedded_listener {
	void *context;
	/* by_host: atl_nav_home or atl_nav_front moved it, else the app (Back on the task root) */
	void (*task_moved)(void *context, struct atl_app *app, bool front, bool by_host);
	/* right before atl_embedded_main returns its status */
	void (*exited)(void *context, struct atl_app *app, int status);
	/* the shown activity's window keeps the screen on (FLAG_KEEP_SCREEN_ON, View.setKeepScreenOn) or stops */
	void (*keep_screen_on)(void *context, struct atl_app *app, bool on);
	/* the shown activity's exclusion changed; valid during the call */
	void (*exclusion_changed)(void *context, struct atl_app *app, const struct atl_gesture_exclusion *exclusion);
};

#ifdef __cplusplus
}
#endif

#endif
