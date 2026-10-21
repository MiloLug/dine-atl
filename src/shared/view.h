#ifndef SHARED_VIEW_H
#define SHARED_VIEW_H

#include <jni.h>

struct ui_view_traits;

void atl_view_traits_get(JNIEnv *env, jobject view, struct ui_view_traits *traits);
/* The view's Java class name, for ui_*_new; free() it. */
char *atl_view_class_name(JNIEnv *env, jobject view);

#endif
