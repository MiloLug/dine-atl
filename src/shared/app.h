#ifndef SHARED_APP_H
#define SHARED_APP_H

#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stddef.h>

#include "src/slots/ui.h"

#include "nav.h"

struct loop_main;
struct ui_window;

#define ATL_ADJUST_RESIZE 0x10 // WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

/* What a window asks of the host: the shown activity's, or one over it (windows.c) */
struct atl_window_state {
	bool keep_screen_on;
	bool fullscreen;
	bool hide_navigation, immersive;
	float brightness;     // negative: the screen's own
	int *exclusion_rects; // px in the window: left, top, right, bottom per rect
	int n_exclusion_rects;
	int adjust; // its SOFT_INPUT_ADJUST_* for the keyboard, unspecified resolved
	int pan;    // px its root moves up for the keyboard (adjustPan)
};

/* An app ATL runs: the C half of its record (ATLLoadedApp.native_app). A host makes it before the
 * app starts and keeps it for the process's life. */
struct atl_app {
	jobject record; // ATLLoadedApp, global ref; NULL until atl_app_bind
	struct loop_main *loop; // its Android main thread's, set before running
	atomic_bool running;    // from atl_app_create until its window closes
	struct ui_window *window;
	void *ui; // the ui backend's state for the app, or NULL
	char *apk_class_path; // its APKs, the base first
	char *data_dir;
	const struct atl_embedded_listener *listener; // the embedded host's, or NULL

	// the activity stack; the last element is the top (activity.c)
	jobject *backlog;
	size_t backlog_len, backlog_cap;
	jobject activity_current;
	bool task_front; // in the back, no activity is focused; the stack and the window stay
	struct atl_subwindow *subwindows; // windows.c's
	jobject token_shown;              // windows.c's: the activity whose windows show, the backlog's ref

	// the shown activity's window (android_view_Window.c); its status bar and top are its own
	struct atl_window_state activity_state;
	bool bar_set, bar_has_color, bar_light, cover_top;
	int bar_color;
	uint32_t background; // ARGB, 0: none
	// the drawn windows' together, as last sent (windows_state_changed)
	bool keep_screen_on;
	float screen_brightness;
	struct ui_frame frame;
	bool frame_sent; // frame went to the ui slot
	bool toast_shown; // a text toast is up (android_widget_Toast.c)

	// insets.c and night.c; set from any thread, under insets_lock
	pthread_mutex_t insets_lock;
	struct atl_window_insets insets;
	int window_width, window_height; // px; 0 until the host sets them or the app starts (the screen's)
	int night;                       // Configuration.UI_MODE_NIGHT_*, or UI_DISPLAY_UNKNOWN
	struct atl_gesture_exclusion exclusion_sent;
};

struct atl_app *atl_app_new(void);
/* The host's insets, as last set (insets.c). */
struct atl_window_insets atl_app_insets(struct atl_app *app);
/* Ties app to its record (which ATLInstaller.prepareApp gave app): its thread group's tag
 * (art_embedder.h). */
void atl_app_bind(JNIEnv *env, struct atl_app *app, jobject record);
/* Moves the calling thread into app's thread group. -1 on error, after printing it. */
int atl_app_join(JNIEnv *env, struct atl_app *app);
/* Threads that attach through JNI without a group belong to app: for hosts that run one app per
 * process. */
void atl_app_take_attaches(JNIEnv *env, struct atl_app *app);
/* The app's Application, a local ref. */
jobject atl_app_application(JNIEnv *env, struct atl_app *app);
/* The running app a Context lives in (Context.atl_process_app), or a Java Window's through its
 * Context. */
struct atl_app *atl_app_of(JNIEnv *env, jobject context);
struct atl_app *atl_app_of_window(JNIEnv *env, jobject window);
/* The calling thread's app, NULL for a thread of no app. */
struct atl_app *atl_app_current(void);

/* windows.c: an activity's windows show (from its start) or hide (from its pause), its own and
 * those it owns; windows_update after the task moved; an activity's close takes its windows. */
void windows_token_visible(JNIEnv *env, struct atl_app *app, jobject activity, bool visible);
void windows_update(JNIEnv *env, struct atl_app *app);
void windows_owner_closed(JNIEnv *env, struct atl_app *app, jobject activity);
/* android_view_Window.c: no activity shows: the host's defaults */
void atl_window_state_reset(struct atl_app *app);
/* night.c: the app's configuration and Resources take its night (atl_nav_set_night), on its main
 * thread; also at its start, as its configuration copies the VM's. */
void atl_night_apply(JNIEnv *env, struct atl_app *app);
/* The state a Java Window's natives set: its sub-window's, the shown activity's, else NULL. */
struct atl_window_state *atl_window_state_of(JNIEnv *env, struct atl_app *app, jobject window, jlong subwindow);
/* android_widget_Toast.c: the task went to the back: a text toast up goes */
void atl_toast_hide(struct atl_app *app);
/* insets.c: the drawn windows' exclusion rects changed, or where those windows are */
void atl_exclusion_update(struct atl_app *app);

#endif
