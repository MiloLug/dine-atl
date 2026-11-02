#ifndef SLOTS_HOST_H
#define SLOTS_HOST_H

#include <stdbool.h>

/* host slot: what runs ATL. Called on the app's Android main thread, for that app. */

struct atl_app;

/* Whether the task may move to the back (Activity.moveTaskToBack); else the activity finishes. */
bool host_task_to_back(struct atl_app *app);
/* The task moved to the front or back: by_host for atl_nav_home and atl_nav_front, else by the app.
 * Not for the first start. */
void host_task_moved(struct atl_app *app, bool front, bool by_host);
/* Whether the shown activity's window keeps the screen on (FLAG_KEEP_SCREEN_ON, View.setKeepScreenOn). */
void host_keep_screen_on(struct atl_app *app, bool on);
struct atl_gesture_exclusion;
/* The shown activity's gesture exclusion changed (nav.h). */
void host_gesture_exclusion_changed(struct atl_app *app, const struct atl_gesture_exclusion *exclusion);

#endif
