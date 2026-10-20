#ifndef ATL_NAV_H
#define ATL_NAV_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* System navigation for the host, from any thread. Each posts to the Android main thread and is
 * false when ATL isn't running. */

/* Back: the current activity's onBackPressed. Nothing while the task is in the back. */
bool atl_nav_back(void);
/* Home: the task to the back (onPause, onStop). Nothing while it is in the back. */
bool atl_nav_home(void);
/* The task to the front (onRestart, onStart, onResume). Nothing while it is in front. */
bool atl_nav_front(void);

#ifdef __cplusplus
}
#endif

#endif
