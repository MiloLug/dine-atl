#include <gtk/gtk.h>

#include "../defines.h"
#include "src/backends/gfx/gsk/canvas.h"

#include "../generated_headers/android_atl_ATLCanvas.h"

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1drawRenderNode(JNIEnv *env, jclass this_class, jlong snapshot_ptr, jlong node_ptr)
{
	GtkSnapshot *snapshot = GTK_SNAPSHOT(_PTR(snapshot_ptr));
	GskRenderNode *node = _PTR(node_ptr);
	struct gsk_painter *painter = gsk_painter_of(snapshot);
	if (painter)
		gsk_painter_draw_node(painter, node);
	else
		gtk_snapshot_append_node(snapshot, node);
}
