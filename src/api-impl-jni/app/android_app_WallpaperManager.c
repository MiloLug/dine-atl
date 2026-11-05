#include <gdk/gdk.h>
#include <stdlib.h>
#include <libportal/portal.h>

#include "../../slots/gfx.h"
#include "../defines.h"
#include "../generated_headers/android_app_WallpaperManager.h"

static void wallpaper_ready_cb(GObject *source, GAsyncResult *res, gpointer user_data)
{
	xdp_portal_set_wallpaper_finish(XDP_PORTAL(source), res, NULL);
	GFile *file = user_data;
	g_file_delete(file, NULL, NULL);
	g_object_unref(file);
}

JNIEXPORT void JNICALL Java_android_app_WallpaperManager_set_1bitmap(JNIEnv *env, jclass clazz, jlong bitmap)
{
	size_t size;
	void *png = gfx_bitmap_encode_png(_PTR(bitmap), &size);
	if (!png)
		return;
	GFileIOStream *stream;
	GFile *file = g_file_new_tmp("XXXXXX.png", &stream, NULL);
	g_output_stream_write_all(g_io_stream_get_output_stream(G_IO_STREAM(stream)), png, size, NULL, NULL, NULL);
	g_io_stream_close(G_IO_STREAM(stream), NULL, NULL);
	g_object_unref(stream);
	free(png);
	XdpPortal *portal = xdp_portal_new();
	xdp_portal_set_wallpaper(portal, NULL, g_file_get_uri(file), XDP_WALLPAPER_FLAG_NONE, NULL, wallpaper_ready_cb, file);
	g_object_unref(portal);
}
