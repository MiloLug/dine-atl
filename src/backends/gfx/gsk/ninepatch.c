#include <gtk/gtk.h>

#include "src/api-impl-jni/graphics/AndroidPaint.h"
#include "src/shared/ninepatch.h"
#include "src/shared/test_hooks.h"
#include "src/slots/gfx.h"

/* A NinePatchDrawable's paintable: the texture's slices at the size it's drawn at. */

struct _NinePatchPaintable {
	GObject parent_instance;
	GdkTexture *texture;
	struct gfx_ninepatch *ninepatch;
	struct gfx_paint *filter; // the tint's color filter
};
G_DECLARE_FINAL_TYPE(NinePatchPaintable, ninepatch_paintable, NINEPATCH, PAINTABLE, GObject)

static void ninepatch_paintable_snapshot(GdkPaintable *paintable, GdkSnapshot *snapshot, double width, double height)
{
	NinePatchPaintable *ninepatch = NINEPATCH_PAINTABLE(paintable);
	const struct AndroidPaint *filter = (const struct AndroidPaint *)ninepatch->filter;
	int texture_width = gdk_texture_get_width(ninepatch->texture), texture_height = gdk_texture_get_height(ninepatch->texture);
	struct ninepatch_slice *slices;
	size_t n = ninepatch_slices(ninepatch->ninepatch, texture_width, texture_height, width, height, &slices);
	if (filter->use_color_filter)
		gtk_snapshot_push_color_matrix(snapshot, &filter->color_matrix, &filter->color_offset);
	for (size_t i = 0; i < n; i++) {
		const struct gfx_rect *src = &slices[i].src, *dst = &slices[i].dst;
		graphene_rect_t bounds = GRAPHENE_RECT_INIT(dst->left, dst->top, dst->right - dst->left, dst->bottom - dst->top);
		uint32_t color = slices[i].color;
		if (color != NINEPATCH_NO_COLOR) {
			gtk_snapshot_append_color(snapshot, &(GdkRGBA){(color >> 16 & 0xFF) / 255.f, (color >> 8 & 0xFF) / 255.f, (color & 0xFF) / 255.f, (color >> 24 & 0xFF) / 255.f}, &bounds);
			continue;
		}
		float xscale = bounds.size.width / (src->right - src->left), yscale = bounds.size.height / (src->bottom - src->top);
		gtk_snapshot_push_clip(snapshot, &bounds);
		gtk_snapshot_append_texture(snapshot, ninepatch->texture,
		                            &GRAPHENE_RECT_INIT(dst->left - src->left * xscale, dst->top - src->top * yscale, texture_width * xscale, texture_height * yscale));
		gtk_snapshot_pop(snapshot);
	}
	if (filter->use_color_filter)
		gtk_snapshot_pop(snapshot);
	free(slices);
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
	g_clear_object(&ninepatch->texture);
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
	ninepatch->texture = g_object_ref(GDK_TEXTURE(bitmap));
	ninepatch->ninepatch = chunk;
	return (struct gfx_drawable *)ninepatch;
}

// a bitmap drawable is its texture, which keeps no filter
void gfx_drawable_set_color_filter(struct gfx_drawable *drawable, int mode, uint32_t argb)
{
	if (NINEPATCH_IS_PAINTABLE(drawable))
		gfx_paint_set_color_filter(NINEPATCH_PAINTABLE(drawable)->filter, mode, argb);
}
