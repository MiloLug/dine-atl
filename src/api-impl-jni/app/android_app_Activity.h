#include <jni.h>
#include <stdbool.h>

void activity_start(JNIEnv *env, jobject activity_object);
void activity_window_ready(void);
bool activity_task_front(void);
/* Whether window (a Java Window) is the shown activity's, or the one being shown. */
bool activity_window_shown(JNIEnv *env, jobject window);
/* That window, a local ref, or NULL. */
jobject activity_shown_window(JNIEnv *env);
void activity_close_all(void);
