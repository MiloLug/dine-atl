#ifndef SHARED_TEST_HOOKS_H
#define SHARED_TEST_HOOKS_H

/* Hooks for test scripts, built with the test_hooks option (ATL_TEST_HOOKS); else no-ops. */

#include <stdbool.h>

struct ui_alert;
struct ui_window;

#ifdef ATL_TEST_HOOKS
/* With ATL_DIALOG_ANSWERS set (button or item labels or "!cancel", separated by ';'): the next answer
 * as a result for alert as it is now, a disabled or absent button matching nothing; exits 1 when
 * it doesn't match. Else false. Backends ask after each show and each result that leaves the
 * dialog drawn, once onShow and the result's listener have changed the alert. */
bool atl_dialog_scripted(const struct ui_alert *alert, int *which);
/* The app's window got its first layout: with ATL_DUMP_VIEWTREE set, prints its view tree. */
void atl_test_window_ready(struct ui_window *window);
/* A Dialog's window is being freed: with ATL_DUMP_VIEWTREE set, prints "dialogfreed:". */
void atl_test_dialog_freed(void);
/* A Drawable's native object was freed: with ATL_DUMP_VIEWTREE set, prints
 * "drawablefreed: <kind>". */
void atl_test_drawable_freed(const char *kind);
#else
static inline bool atl_dialog_scripted(const struct ui_alert *alert, int *which) { return false; }
static inline void atl_test_window_ready(struct ui_window *window) {}
static inline void atl_test_dialog_freed(void) {}
static inline void atl_test_drawable_freed(const char *kind) {}
#endif

#endif
