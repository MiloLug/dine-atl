#include <jni.h>
#include <stdbool.h>

void activity_start(JNIEnv *env, jobject activity_object);
void activity_window_ready(void);
bool activity_task_front(void);
void activity_close_all(void);
