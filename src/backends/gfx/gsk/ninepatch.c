#include <gtk/gtk.h>

#include "src/api-impl-jni/graphics/AndroidPaint.h"
#include "src/shared/ninepatch.h"
#include "src/shared/test_hooks.h"
#include "src/slots/gfx.h"

#include "bitmap.h"
#include "canvas.h"

/* A NinePatchDrawable's paintable: the bitmap's slices at the size it's drawn at. */

struct _NinePatchPaintable {
	GObject parent_instance;
	struct gfx_bitmap *bitmap; // held
	struct gfx_ninepatch *ninepatch;
	struct gfx_paint *filter; // the tint's color filter
};
G_DECLARE_FINAL_TYPE(NinePatchPaintable, ninepatch_paintable, NINEPATCH, PAINTABLE, GObject)

struct slices_draw {
	GdkTexture *texture;
	const struct ninepatch_slice *slices;
	size_t n;
};

static void draw_slices(GtkSnapshot *snapshot, const struct gsk_draw *gsk_draw, enum gsk_part part)
{
	const struct slices_draw *draw = gsk_draw->data;
	if (part == GSK_COVERAGE) {
		gtk_snapshot_append_color(snapshot, &(GdkRGBA){1, 1, 1, 1}, gsk_draw->bounds);
		return;
	}
	int texture_width = gdk_texture_get_width(draw->texture), texture_height = gdk_texture_get_height(draw->texture);
	for (size_t i = 0; i < draw->n; i++) {
		const struct gfx_rect *src = &draw->slices[i].src, *dst = &draw->slices[i].dst;
		graphene_rect_t bounds = GRAPHENE_RECT_INIT(dst->left, dst->top, dst->right - dst->left, dst->bottom - dst->top);
		uint32_t color = draw->slices[i].color;
		if (color != NINEPATCH_NO_COLOR) {
			gtk_snapshot_append_color(snapshot, &(GdkRGBA){(color >> 16 & 0xFF) / 255.f, (color >> 8 & 0xFF) / 255.f, (color & 0xFF) / 255.f, (color >> 24 & 0xFF) / 255.f}, &bounds);
			continue;
		}
		float xscale = bounds.size.width / (src->right - src->left), yscale = bounds.size.height / (src->bottom - src->top);
		gtk_snapshot_push_clip(snapshot, &bounds);
		gtk_snapshot_append_texture(snapshot, draw->texture,
		                            &GRAPHENE_RECT_INIT(dst->left - src->left * xscale, dst->top - src->top * yscale, texture_width * xscale, texture_height * yscale));
		gtk_snapshot_pop(snapshot);
	}
}

static void ninepatch_paintable_snapshot(GdkPaintable *paintable, GdkSnapshot *snapshot, double width, double height)
{
	NinePatchPaintable *ninepatch = NINEPATCH_PAINTABLE(paintable);
	const struct AndroidPaint *filter = (const struct AndroidPaint *)ninepatch->filter;
	struct slices_draw draw = {gsk_bitmap_texture(ninepatch->bitmap)};
	struct ninepatch_slice *slices;
	draw.n = ninepatch_slices(ninepatch->ninepatch, gdk_texture_get_width(draw.texture), gdk_texture_get_height(draw.texture), width, height, &slices);
	draw.slices = slices;
	gsk_filtered(GTK_SNAPSHOT(snapshot), filter, &(struct gsk_draw){draw_slices, &draw, filter, &GRAPHENE_RECT_INIT(0, 0, width, height)}, GSK_CONTENT);
	free(slices);
	g_object_unref(draw.texture);
}

static void ninepatch_paintable_init(NinePatchPaintable *ninepatch)
{
	ninepatch->filter = gfx_paint_new();
}

static void ninepatch_paintable_paintable_init(GdkPaintableInterface *iface)
{
	iface->snapshot = ninepatch_paintable_snapshot;
}

G_DEFINE_TYPE_WITH_CODE(NinePatchPaintable, ninepatch_paintable, G_TYPE_OBJECT,
                        G_IMPLEMENT_INTERFACE(GDK_TYPE_PAINTABLE, ninepatch_paintable_paintable_init))

static void ninepatch_paintable_finalize(GObject *object)
{
	NinePatchPaintable *ninepatch = NINEPATCH_PAINTABLE(object);
	if (ninepatch->bitmap)
		gfx_bitmap_unref(ninepatch->bitmap);
	ninepatch_free(ninepatch->ninepatch);
	gfx_paint_free(ninepatch->filter);
	atl_test_drawable_freed(G_OBJECT_TYPE_NAME(object));
	G_OBJECT_CLASS(ninepatch_paintable_parent_class)->finalize(object);
}

static void ninepatch_paintable_class_init(NinePatchPaintableClass *class)
{
	G_OBJECT_CLASS(class)->finalize = ninepatch_paintable_finalize;
}

struct gfx_drawable *gfx_ninepatch_drawable_new(struct gfx_bitmap *bitmap, struct gfx_ninepatch *chunk)
{
	NinePatchPaintable *ninepatch = g_object_new(ninepatch_paintable_get_type(), NULL);
	gfx_bitmap_ref(bitmap);
	ninepatch->bitmap = bitmap;
	ninepatch->ninepatch = chunk;
	return (struct gfx_drawable *)ninepatch;
}

void gfx_drawable_set_color_filter(struct gfx_drawable *drawable, const struct gfx_color_filter *color_filter)
{
	struct gfx_paint *filter = NINEPATCH_IS_PAINTABLE(drawable) ? NINEPATCH_PAINTABLE(drawable)->filter : gsk_bitmap_drawable_filter(GDK_PAINTABLE(drawable));
	if (filter)
		gfx_paint_set_color_filter(filter, color_filter);
}
