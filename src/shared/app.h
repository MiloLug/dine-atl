#ifndef SHARED_APP_H
#define SHARED_APP_H

#include <jni.h>
#include <pthread.h>
#include <stdbool.h>
#include <stddef.h>

#include "nav.h"

struct ui_window;

/* An app ATL runs: the C half of its record (ATLLoadedApp.native_app). A host makes it before the
 * app starts and keeps it for the process's life. */
struct atl_app {
	jobject record; // ATLLoadedApp, global ref; NULL until atl_app_bind
	bool running;   // from atl_app_create until its window closes
	struct ui_window *window;
	char *apk_class_path; // its APKs, the base first
	char *data_dir;
	const struct atl_embedded_listener *listener; // the embedded host's, or NULL

	// the activity stack; the last element is the top (activity.c)
	jobject *backlog;
	size_t backlog_len, backlog_cap;
	jobject activity_current;
	bool task_front; // in the back, no activity is focused; the stack and the window stay
	bool keep_screen_on;

	// insets.c
	pthread_mutex_t insets_lock;
	struct atl_window_insets insets; // set from any thread
	int *exclusion_rects; // px in the window: left, top, right, bottom per rect; the main thread's
	int n_exclusion_rects;
	struct atl_gesture_exclusion exclusion_sent;
};

struct atl_app *atl_app_new(void);
/* Ties app to its record: record's native_app and its thread group's tag (art_embedder.h). */
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

#endif
