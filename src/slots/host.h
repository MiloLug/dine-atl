#ifndef SLOTS_HOST_H
#define SLOTS_HOST_H

#include <stdbool.h>

/* host slot: what runs ATL. Called on the Android main thread. */

/* Whether the task may move to the back (Activity.moveTaskToBack); else the activity finishes. */
bool host_task_to_back(void);
/* The task moved to the front or back: by_host for atl_nav_home and atl_nav_front, else by the app.
 * Not for the first start. */
void host_task_moved(bool front, bool by_host);

#endif
