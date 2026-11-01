#include <gtk/gtk.h>

#include "../defines.h"

#include "../generated_headers/android_atl_ATLCanvas.h"

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1drawRenderNode(JNIEnv *env, jclass this_class, jlong snapshot_ptr, jlong node_ptr)
{
	GdkSnapshot *snapshot = GTK_SNAPSHOT(_PTR(snapshot_ptr));
	GskRenderNode *node = _PTR(node_ptr);
	gtk_snapshot_append_node(snapshot, node);
}
