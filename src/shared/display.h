#ifndef SHARED_DISPLAY_H
#define SHARED_DISPLAY_H

#include <jni.h>

struct ui_display;

/* Writes the known fields into config, or into Context.sys_config when config is NULL. */
void atl_display_apply(JNIEnv *env, jobject config, const struct ui_display *display);

#endif
