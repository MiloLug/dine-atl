#ifndef JAVA_PAINTABLE_H
#define JAVA_PAINTABLE_H

#include <gdk/gdk.h>
#include <jni.h>

/* A GdkPaintable drawn by the Drawable's own draw(); takes over the weak reference. */
G_DECLARE_FINAL_TYPE(JavaPaintable, java_paintable, JAVA, PAINTABLE, GObject)
GdkPaintable *java_paintable_new(jweak drawable);

#endif
