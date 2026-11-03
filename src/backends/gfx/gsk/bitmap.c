#include <gtk/gtk.h>
#include <stdlib.h>
#include <string.h>

#include "src/api-impl-jni/graphics/AndroidPaint.h"
#include "src/shared/test_hooks.h"
#include "src/slots/gfx.h"

#include "bitmap.h"
#include "canvas.h"
#include "memory_format.h"

/* A BitmapDrawable draws a BitmapPaintable: the bitmap's texture through the drawable's color
 * filter. */

static struct gfx_bitmap *bitmap_new(GdkTexture *texture)
{
	if (!texture)
		return NULL;
	struct gfx_bitmap *bitmap = g_new0(struct gfx_bitmap, 1);
	bitmap->refs = 1;
	g_mutex_init(&bitmap->lock);
	g_mutex_init(&bitmap->download_lock);
	bitmap->texture = texture;
	return bitmap;
}

GdkTexture *gsk_bitmap_ref_texture(struct gfx_bitmap *bitmap)
{
	g_mutex_lock(&bitmap->lock);
	GdkTexture *texture = g_object_ref(bitmap->texture);
	g_mutex_unlock(&bitmap->lock);
	return texture;
}

void gsk_bitmap_set_texture(struct gfx_bitmap *bitmap, GdkTexture *texture)
{
	g_mutex_lock(&bitmap->lock);
	GdkTexture *old = bitmap->texture;
	bitmap->texture = texture;
	g_mutex_unlock(&bitmap->lock);
	g_object_unref(old);
	if (g_mutex_trylock(&bitmap->download_lock)) { // else a reader is replacing it
		g_clear_pointer(&bitmap->download, g_free);
		g_clear_object(&bitmap->downloaded);
		g_mutex_unlock(&bitmap->download_lock);
	}
}

// straight BGRA bytes: Color's ARGB ints, little-endian
static GdkTexture *texture_of_argb(int width, int height, uint32_t *argb)
{
	GBytes *bytes = g_bytes_new_take(argb, (size_t)width * height * 4);
	GdkTexture *texture = gdk_memory_texture_new(width, height, GDK_MEMORY_B8G8R8A8, bytes, width * 4);
	g_bytes_unref(bytes);
	return texture;
}

static uint32_t *download_argb(GdkTexture *texture)
{
	int width = gdk_texture_get_width(texture), height = gdk_texture_get_height(texture);
	uint32_t *argb = g_malloc((size_t)width * height * 4);
	GdkTextureDownloader *downloader = gdk_texture_downloader_new(texture);
	gdk_texture_downloader_set_format(downloader, GDK_MEMORY_B8G8R8A8);
	gdk_texture_downloader_download_into(downloader, (guchar *)argb, width * 4);
	gdk_texture_downloader_free(downloader);
	return argb;
}

struct gfx_bitmap *gfx_bitmap_new(int width, int height, enum gfx_bitmap_config config)
{
	int format = gsk_memory_format(config);
	if (format == -1)
		format = GDK_MEMORY_R8G8B8A8;
	size_t stride = width * (format == GDK_MEMORY_A8 ? 1 : format == GDK_MEMORY_R16G16B16A16_FLOAT ? 8 : 4);
	GBytes *bytes = g_bytes_new_take(g_malloc0(height * stride), height * stride);
	GdkTexture *texture = gdk_memory_texture_new(width, height, format, bytes, stride);
	g_bytes_unref(bytes);
	return bitmap_new(texture);
}

struct gfx_bitmap *gfx_bitmap_decode(const void *data, size_t size)
{
	GdkPixbufLoader *loader = gdk_pixbuf_loader_new();
	bool written = gdk_pixbuf_loader_write(loader, data, size, NULL);
	bool closed = gdk_pixbuf_loader_close(loader, NULL); // after a failed write too
	GdkPixbuf *pixbuf = written && closed ? gdk_pixbuf_loader_get_pixbuf(loader) : NULL;
	GdkTexture *texture = pixbuf ? gdk_texture_new_for_pixbuf(pixbuf) : NULL;
	g_object_unref(loader);
	return bitmap_new(texture);
}

// a swap keeps the size
int gfx_bitmap_get_width(const struct gfx_bitmap *bitmap)
{
	GdkTexture *texture = gsk_bitmap_ref_texture((struct gfx_bitmap *)bitmap);
	int width = gdk_texture_get_width(texture);
	g_object_unref(texture);
	return width;
}

int gfx_bitmap_get_height(const struct gfx_bitmap *bitmap)
{
	GdkTexture *texture = gsk_bitmap_ref_texture((struct gfx_bitmap *)bitmap);
	int height = gdk_texture_get_height(texture);
	g_object_unref(texture);
	return height;
}

void gfx_bitmap_ref(struct gfx_bitmap *bitmap)
{
	atomic_fetch_add(&bitmap->refs, 1);
}

void gfx_bitmap_unref(struct gfx_bitmap *bitmap)
{
	if (atomic_fetch_sub(&bitmap->refs, 1) != 1)
		return;
	g_object_unref(bitmap->texture);
	if (bitmap->downloaded)
		g_object_unref(bitmap->downloaded);
	g_free(bitmap->download);
	g_mutex_clear(&bitmap->lock);
	g_mutex_clear(&bitmap->download_lock);
	g_free(bitmap);
}

extern GThread *main_thread_id;

/* GSK renders offscreen with the display's renderer on the main thread, cairo's elsewhere. */
static GdkTexture *render(GskRenderNode *node, int width, int height)
{
	static GType renderer_type = 0;
	static GdkDisplay *off_screen_display;
	GskRenderer *renderer;
	if (g_thread_self() == main_thread_id) {
		GObject *default_display = G_OBJECT(gdk_display_get_default());
		while (default_display->ref_count < 100)
			g_object_ref(default_display); // workaround for https://gitlab.gnome.org/GNOME/gtk/-/issues/7848
		if (!off_screen_display) {
			off_screen_display = gdk_display_open(NULL);
			// Create and destroy a dummy surface to get the renderer type
			GdkSurface *surface = gdk_surface_new_toplevel(off_screen_display);
			GskRenderer *renderer = gsk_renderer_new_for_surface(surface);
			renderer_type = G_OBJECT_TYPE(renderer);
			gsk_renderer_unrealize(renderer);
			g_object_unref(renderer);
			gdk_surface_destroy(surface);
		}
		renderer = g_object_new(renderer_type, NULL);
		gsk_renderer_realize_for_display(renderer, off_screen_display, NULL);
	} else {
		renderer = gsk_cairo_renderer_new();
		gsk_renderer_realize(renderer, NULL, NULL);
	}
	GdkTexture *texture = gsk_renderer_render_texture(renderer, node, &GRAPHENE_RECT_INIT(0, 0, width, height));
	gsk_renderer_unrealize(renderer);
	g_object_unref(renderer);
	return texture;
}

void gfx_bitmap_render(struct gfx_bitmap *bitmap, struct gfx_canvas *canvas)
{
	GskRenderNode *drawn = gtk_snapshot_free_to_node(GTK_SNAPSHOT(canvas));
	if (!drawn)
		return;
	GdkTexture *texture = gsk_bitmap_ref_texture(bitmap);
	int width = gdk_texture_get_width(texture), height = gdk_texture_get_height(texture);
	GtkSnapshot *snapshot = gtk_snapshot_new();
	gtk_snapshot_append_texture(snapshot, texture, &GRAPHENE_RECT_INIT(0, 0, width, height));
	g_object_unref(texture);
	gtk_snapshot_append_node(snapshot, drawn);
	gsk_render_node_unref(drawn);
	GskRenderNode *node = gtk_snapshot_free_to_node(snapshot);
	gsk_bitmap_set_texture(bitmap, render(node, width, height));
	gsk_render_node_unref(node);
}

void gfx_bitmap_erase(struct gfx_bitmap *bitmap, uint32_t argb)
{
	int width = gfx_bitmap_get_width(bitmap), height = gfx_bitmap_get_height(bitmap);
	uint32_t *pixels = g_malloc((size_t)width * height * 4);
	for (size_t i = 0; i < (size_t)width * height; i++)
		pixels[i] = argb;
	gsk_bitmap_set_texture(bitmap, texture_of_argb(width, height, pixels));
}

// downloaded once per texture: a getPixel loop reads the same one again and again
void gfx_bitmap_read(const struct gfx_bitmap *bitmap, int x, int y, int width, int height, uint32_t *argb, int stride)
{
	struct gfx_bitmap *held = (struct gfx_bitmap *)bitmap;
	GdkTexture *texture = gsk_bitmap_ref_texture(held);
	int bitmap_width = gdk_texture_get_width(texture);
	g_mutex_lock(&held->download_lock);
	if (held->downloaded != texture) {
		g_free(held->download);
		if (held->downloaded)
			g_object_unref(held->downloaded);
		held->download = download_argb(texture);
		held->downloaded = g_object_ref(texture);
	}
	for (int row = 0; row < height; row++)
		memcpy(argb + (ptrdiff_t)row * stride, held->download + (size_t)(y + row) * bitmap_width + x, width * 4);
	g_mutex_unlock(&held->download_lock);
	g_object_unref(texture);
}

void gfx_bitmap_write(struct gfx_bitmap *bitmap, int x, int y, int width, int height, const uint32_t *argb, int stride)
{
	GdkTexture *texture = gsk_bitmap_ref_texture(bitmap);
	int bitmap_width = gdk_texture_get_width(texture), bitmap_height = gdk_texture_get_height(texture);
	uint32_t *pixels = download_argb(texture);
	g_object_unref(texture);
	for (int row = 0; row < height; row++)
		memcpy(pixels + (size_t)(y + row) * bitmap_width + x, argb + (ptrdiff_t)row * stride, width * 4);
	gsk_bitmap_set_texture(bitmap, texture_of_argb(bitmap_width, bitmap_height, pixels));
}

void *gfx_bitmap_encode_png(const struct gfx_bitmap *bitmap, size_t *size)
{
	GdkTexture *texture = gsk_bitmap_ref_texture((struct gfx_bitmap *)bitmap);
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

struct _BitmapPaintable {
	GObject parent_instance;
	struct gfx_bitmap *bitmap; // held
	struct gfx_paint *filter;
};
G_DECLARE_FINAL_TYPE(BitmapPaintable, bitmap_paintable, BITMAP, PAINTABLE, GObject)

static void bitmap_paintable_snapshot(GdkPaintable *paintable, GdkSnapshot *snapshot, double width, double height)
{
	BitmapPaintable *bitmap = BITMAP_PAINTABLE(paintable);
	const struct AndroidPaint *filter = (const struct AndroidPaint *)bitmap->filter;
	if (filter->use_color_filter)
		gtk_snapshot_push_color_matrix(snapshot, &filter->color_matrix, &filter->color_offset);
	GdkTexture *texture = gsk_bitmap_ref_texture(bitmap->bitmap);
	gdk_paintable_snapshot(GDK_PAINTABLE(texture), snapshot, width, height);
	g_object_unref(texture);
	if (filter->use_color_filter)
		gtk_snapshot_pop(snapshot);
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
