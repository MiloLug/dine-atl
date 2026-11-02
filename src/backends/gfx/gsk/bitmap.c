#include <gtk/gtk.h>

#include "src/api-impl-jni/graphics/AndroidPaint.h"
#include "src/shared/test_hooks.h"
#include "src/slots/gfx.h"

#include "canvas.h"
#include "memory_format.h"

/* A bitmap is a GdkTexture. A BitmapDrawable draws a BitmapPaintable: the texture through the
 * drawable's color filter. */

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

struct _BitmapPaintable {
	GObject parent_instance;
	GdkTexture *texture;
	struct gfx_paint *filter;
};
G_DECLARE_FINAL_TYPE(BitmapPaintable, bitmap_paintable, BITMAP, PAINTABLE, GObject)

static void bitmap_paintable_snapshot(GdkPaintable *paintable, GdkSnapshot *snapshot, double width, double height)
{
	BitmapPaintable *bitmap = BITMAP_PAINTABLE(paintable);
	const struct AndroidPaint *filter = (const struct AndroidPaint *)bitmap->filter;
	if (filter->use_color_filter)
		gtk_snapshot_push_color_matrix(snapshot, &filter->color_matrix, &filter->color_offset);
	gdk_paintable_snapshot(GDK_PAINTABLE(bitmap->texture), snapshot, width, height);
	if (filter->use_color_filter)
		gtk_snapshot_pop(snapshot);
}

static int bitmap_paintable_get_intrinsic_width(GdkPaintable *paintable)
{
	return gdk_texture_get_width(BITMAP_PAINTABLE(paintable)->texture);
}

static int bitmap_paintable_get_intrinsic_height(GdkPaintable *paintable)
{
	return gdk_texture_get_height(BITMAP_PAINTABLE(paintable)->texture);
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
	g_clear_object(&bitmap->texture);
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
	paintable->texture = g_object_ref(GDK_TEXTURE(bitmap));
	return (struct gfx_drawable *)paintable;
}

struct gfx_paint *gsk_bitmap_drawable_filter(GdkPaintable *paintable)
{
	return BITMAP_IS_PAINTABLE(paintable) ? BITMAP_PAINTABLE(paintable)->filter : NULL;
}
