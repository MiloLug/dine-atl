#include <gtk/gtk.h>

#include "src/slots/gfx.h"

#include "memory_format.h"

/* A bitmap is a GdkTexture, which is also the GdkPaintable a BitmapDrawable draws. */

struct gfx_bitmap *gfx_bitmap_new(int width, int height, enum gfx_bitmap_config config)
{
	int format = gsk_memory_format(config);
	if (format == -1)
		format = GDK_MEMORY_R8G8B8A8;
	size_t stride = width * (format == GDK_MEMORY_A8 ? 1 : format == GDK_MEMORY_R16G16B16A16_FLOAT ? 8 : 4);
	GBytes *bytes = g_bytes_new_take(g_malloc0(height * stride), height * stride);
	GdkTexture *texture = gdk_memory_texture_new(width, height, format, bytes, stride);
	g_bytes_unref(bytes);
	return (struct gfx_bitmap *)texture;
}

struct gfx_bitmap *gfx_bitmap_decode(const void *data, size_t size)
{
	GdkPixbufLoader *loader = gdk_pixbuf_loader_new();
	bool written = gdk_pixbuf_loader_write(loader, data, size, NULL);
	bool closed = gdk_pixbuf_loader_close(loader, NULL); // after a failed write too
	GdkPixbuf *pixbuf = written && closed ? gdk_pixbuf_loader_get_pixbuf(loader) : NULL;
	GdkTexture *texture = pixbuf ? gdk_texture_new_for_pixbuf(pixbuf) : NULL;
	g_object_unref(loader);
	return (struct gfx_bitmap *)texture;
}

int gfx_bitmap_get_width(const struct gfx_bitmap *bitmap)
{
	return gdk_texture_get_width((GdkTexture *)bitmap);
}

int gfx_bitmap_get_height(const struct gfx_bitmap *bitmap)
{
	return gdk_texture_get_height((GdkTexture *)bitmap);
}

void gfx_bitmap_ref(struct gfx_bitmap *bitmap)
{
	g_object_ref(bitmap);
}

void gfx_bitmap_unref(struct gfx_bitmap *bitmap)
{
	g_object_unref(bitmap);
}

struct gfx_drawable *gfx_bitmap_drawable_new(struct gfx_bitmap *bitmap)
{
	return (struct gfx_drawable *)g_object_ref(bitmap);
}
