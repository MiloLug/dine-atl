#ifndef _GTK_UTIL_H_
#define _GTK_UTIL_H_

#include <gtk/gtk.h>

#include "util.h"

void atl_ensure_widget_snapshotability(GtkWidget *widget);
void atl_safe_gtk_label_set_text(GtkLabel *label, const char *str);
void atl_safe_gtk_widget_set_visible(GtkWidget *widget, gboolean visible);
void atl_safe_gtk_widget_queue_allocate(GtkWidget *widget);
void atl_safe_gtk_widget_queue_resize(GtkWidget *widget);

#define INTENT_G_VARIANT_TYPE_STRING "(sssa{sv}s)" // (action, className, data, extras, sender_dbus_name)
GVariant *intent_serialize(JNIEnv *env, jobject intent);
jobject intent_deserialize(JNIEnv *env, GVariant *variant);
const char *intent_actionname_from_type(int type);

#endif
