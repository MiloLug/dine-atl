#ifndef SHARED_PAINT_H
#define SHARED_PAINT_H

#include <jni.h>

#include "src/slots/gfx.h"

/* A ColorFilter's natives as the gfx slot takes them: a blend mode and color, or matrix's 20
 * floats (NULL: none). */
struct gfx_color_filter atl_color_filter(JNIEnv *env, jint mode, jint color, jfloatArray matrix);

#endif
