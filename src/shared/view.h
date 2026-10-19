#ifndef SHARED_VIEW_H
#define SHARED_VIEW_H

#include <jni.h>

struct ui_view_traits;

void atl_view_traits_get(JNIEnv *env, jobject view, struct ui_view_traits *traits);

#endif
