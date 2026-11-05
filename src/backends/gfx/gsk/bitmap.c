#include <gtk/gtk.h>
#include <stdlib.h>
#include <string.h>

#include "src/api-impl-jni/graphics/AndroidPaint.h"
#include "src/shared/test_hooks.h"
#include "src/slots/gfx.h"

#include "bitmap.h"
#include "canvas.h"

/* A BitmapDrawable draws a BitmapPaintable: the bitmap's texture through the drawable's color
 * filter. */

void gfx_bitmap_image_free(void *image)
{
	g_object_unref(image);
}

static GdkTexture *texture_of_words(const uint32_t *words, int width, int height, bool copy)
{
	size_t size = (size_t)width * height * 4;
	GBytes *bytes = copy ? g_bytes_new(words, size) : g_bytes_new_static(words, size);
	GdkTexture *texture = gdk_memory_texture_new(width, height, GDK_MEMORY_B8G8R8A8_PREMULTIPLIED, bytes, width * 4);
	g_bytes_unref(bytes);
	return texture;
}

GdkTexture *gsk_bitmap_texture(struct gfx_bitmap *bitmap)
{
	unsigned generation = atomic_load(&bitmap->generation);
	pthread_mutex_lock(&bitmap->image_lock);
	GdkTexture *texture = bitmap->image && bitmap->image_generation == generation ? g_object_ref(bitmap->image) : NULL;
	pthread_mutex_unlock(&bitmap->image_lock);
	if (texture)
		return texture;
	texture = texture_of_words(bitmap->words, bitmap->width, bitmap->height, true);
	pthread_mutex_lock(&bitmap->image_lock);
	if (!bitmap->image || (int)(generation - bitmap->image_generation) > 0) {
		if (bitmap->image)
			g_object_unref(bitmap->image);
		bitmap->image = g_object_ref(texture);
		bitmap->image_generation = generation;
	}
	pthread_mutex_unlock(&bitmap->image_lock);
	return texture;
}

uint32_t *gfx_image_decode(const void *data, size_t size, int *width, int *height)
{
	GdkPixbufLoader *loader = gdk_pixbuf_loader_new();
	bool written = gdk_pixbuf_loader_write(loader, data, size, NULL);
	bool closed = gdk_pixbuf_loader_close(loader, NULL); // after a failed write too
	GdkPixbuf *pixbuf = written && closed ? gdk_pixbuf_loader_get_pixbuf(loader) : NULL;
	GdkTexture *texture = pixbuf ? gdk_texture_new_for_pixbuf(pixbuf) : NULL;
	g_object_unref(loader);
	if (!texture)
		return NULL;
	*width = gdk_texture_get_width(texture);
	*height = gdk_texture_get_height(texture);
	uint32_t *words = malloc((size_t)*width * *height * 4);
	GdkTextureDownloader *downloader = gdk_texture_downloader_new(texture);
	gdk_texture_downloader_set_format(downloader, GDK_MEMORY_B8G8R8A8_PREMULTIPLIED);
	gdk_texture_downloader_download_into(downloader, (guchar *)words, *width * 4);
	gdk_texture_downloader_free(downloader);
	g_object_unref(texture);
	return words;
}

void *gfx_image_encode_png(const uint32_t *words, int width, int height, size_t *size)
{
	GdkTexture *texture = texture_of_words(words, width, height, false);
	GBytes *bytes = gdk_texture_save_to_png_bytes(texture);
	g_object_unref(texture);
	gsize length;
	const void *data = g_bytes_get_data(bytes, &length);
	void *png = malloc(length);
	memcpy(png, data, length);
	g_bytes_unref(bytes);
	*size = length;
	return png;
}

#ifdef ATL_TEST_HOOKS
extern GThread *main_thread_id;

bool gfx_test_render_view(struct gfx_canvas *canvas, struct gfx_bitmap *target)
{
	GskRenderNode *node = gtk_snapshot_free_to_node(GTK_SNAPSHOT(canvas));
	if (!node)
		return true;
	if (g_thread_self() != main_thread_id) {
		gsk_render_node_unref(node);
		return false;
	}
	static GType renderer_type = 0;
	static GdkDisplay *off_screen_display;
	GObject *default_display = G_OBJECT(gdk_display_get_default());
	while (default_display->ref_count < 100)
		g_object_ref(default_display); // workaround for https://gitlab.gnome.org/GNOME/gtk/-/issues/7848
	if (!off_screen_display) { // the window's renderer type, from a surface made for it
		off_screen_display = gdk_display_open(NULL);
		GdkSurface *surface = gdk_surface_new_toplevel(off_screen_display);
		GskRenderer *renderer = gsk_renderer_new_for_surface(surface);
		renderer_type = G_OBJECT_TYPE(renderer);
		gsk_renderer_unrealize(renderer);
		g_object_unref(renderer);
		gdk_surface_destroy(surface);
	}
	GskRenderer *renderer = g_object_new(renderer_type, NULL);
	gsk_renderer_realize_for_display(renderer, off_screen_display, NULL);
	GdkTexture *texture = gsk_renderer_render_texture(renderer, node, &GRAPHENE_RECT_INIT(0, 0, target->width, target->height));
	gsk_renderer_unrealize(renderer);
	g_object_unref(renderer);
	gsk_render_node_unref(node);
	GdkTextureDownloader *downloader = gdk_texture_downloader_new(texture);
	gdk_texture_downloader_set_format(downloader, GDK_MEMORY_B8G8R8A8_PREMULTIPLIED);
	gdk_texture_downloader_download_into(downloader, (guchar *)target->words, target->width * 4);
	gdk_texture_downloader_free(downloader);
	g_object_unref(texture);
	bitmap_changed(target);
	return true;
}
#endif

struct _BitmapPaintable {
	GObject parent_instance;
	struct gfx_bitmap *bitmap; // held
	struct gfx_paint *filter;
};
G_DECLARE_FINAL_TYPE(BitmapPaintable, bitmap_paintable, BITMAP, PAINTABLE, GObject)

static void draw_texture(GtkSnapshot *snapshot, const struct gsk_draw *draw, enum gsk_part part)
{
	if (part == GSK_COVERAGE)
		gtk_snapshot_append_color(snapshot, &(GdkRGBA){1, 1, 1, 1}, draw->bounds);
	else
		gtk_snapshot_append_texture(snapshot, (GdkTexture *)draw->data, draw->bounds);
}

static void bitmap_paintable_snapshot(GdkPaintable *paintable, GdkSnapshot *snapshot, double width, double height)
{
	BitmapPaintable *bitmap = BITMAP_PAINTABLE(paintable);
	const struct AndroidPaint *filter = (const struct AndroidPaint *)bitmap->filter;
	GdkTexture *texture = gsk_bitmap_texture(bitmap->bitmap);
	gsk_filtered(GTK_SNAPSHOT(snapshot), filter, &(struct gsk_draw){draw_texture, texture, filter, &GRAPHENE_RECT_INIT(0, 0, width, height)}, GSK_CONTENT);
	g_object_unref(texture);
}

static int bitmap_paintable_get_intrinsic_width(GdkPaintable *paintable)
{
	return gfx_bitmap_get_width(BITMAP_PAINTABLE(paintable)->bitmap);
}

static int bitmap_paintable_get_intrinsic_height(GdkPaintable *paintable)
{
	return gfx_bitmap_get_height(BITMAP_PAINTABLE(paintable)->bitmap);
}

static void bitmap_paintable_init(BitmapPaintable *bitmap)
{
	bitmap->filter = gfx_paint_new();
}

static void bitmap_paintable_paintable_init(GdkPaintableInterface *iface)
{
	iface->snapshot = bitmap_paintable_snapshot;
	iface->get_intrinsic_width = bitmap_paintable_get_intrinsic_width;
	iface->get_intrinsic_height = bitmap_paintable_get_intrinsic_height;
}

G_DEFINE_TYPE_WITH_CODE(BitmapPaintable, bitmap_paintable, G_TYPE_OBJECT,
                        G_IMPLEMENT_INTERFACE(GDK_TYPE_PAINTABLE, bitmap_paintable_paintable_init))

static void bitmap_paintable_finalize(GObject *object)
{
	BitmapPaintable *bitmap = BITMAP_PAINTABLE(object);
	if (bitmap->bitmap)
		gfx_bitmap_unref(bitmap->bitmap);
	gfx_paint_free(bitmap->filter);
	atl_test_drawable_freed(G_OBJECT_TYPE_NAME(object));
	G_OBJECT_CLASS(bitmap_paintable_parent_class)->finalize(object);
}

static void bitmap_paintable_class_init(BitmapPaintableClass *class)
{
	G_OBJECT_CLASS(class)->finalize = bitmap_paintable_finalize;
}

struct gfx_drawable *gfx_bitmap_drawable_new(struct gfx_bitmap *bitmap)
{
	BitmapPaintable *paintable = g_object_new(bitmap_paintable_get_type(), NULL);
	gfx_bitmap_ref(bitmap);
	paintable->bitmap = bitmap;
	return (struct gfx_drawable *)paintable;
}

struct gfx_paint *gsk_bitmap_drawable_filter(GdkPaintable *paintable)
{
	return BITMAP_IS_PAINTABLE(paintable) ? BITMAP_PAINTABLE(paintable)->filter : NULL;
}

struct gfx_bitmap *gsk_bitmap_drawable_bitmap(GdkPaintable *paintable)
{
	return BITMAP_IS_PAINTABLE(paintable) ? BITMAP_PAINTABLE(paintable)->bitmap : NULL;
}
