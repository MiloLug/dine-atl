#include <jni.h>
#include <stdbool.h>

struct atl_app;

void activity_start(JNIEnv *env, struct atl_app *app, jobject activity_object);
void activity_window_ready(struct atl_app *app);
bool activity_task_front(struct atl_app *app);
/* Whether window (a Java Window) is the shown activity's, or the one being shown. */
bool activity_window_shown(JNIEnv *env, struct atl_app *app, jobject window);
/* That window, a local ref, or NULL. */
jobject activity_shown_window(JNIEnv *env, struct atl_app *app);
void activity_close_all(struct atl_app *app);
