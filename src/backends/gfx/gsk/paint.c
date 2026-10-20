#include <gsk/gsk.h>

#include "src/api-impl-jni/graphics/AndroidPaint.h"
#include "src/api-impl-jni/graphics/java_paintable.h"
#include "src/slots/gfx.h"

struct gfx_paint *gfx_paint_new(void)
{
	struct AndroidPaint *paint = g_new0(struct AndroidPaint, 1);
	paint->color.alpha = 1.f;
	paint->gsk_stroke = gsk_stroke_new(1);
	paint->font = pango_font_description_new();
	paint->is_fill = true;
	return (struct gfx_paint *)paint;
}

void gfx_paint_set_color(struct gfx_paint *gfx_paint, uint32_t color)
{
	struct AndroidPaint *paint = (struct AndroidPaint *)gfx_paint;
	paint->color.red = ((color >> 16) & 0xFF) / 255.f;
	paint->color.green = ((color >> 8) & 0xFF) / 255.f;
	paint->color.blue = ((color >> 0) & 0xFF) / 255.f;
	paint->color.alpha = ((color >> 24) & 0xFF) / 255.f;
}

struct gfx_drawable *gfx_drawable_new(struct atl_ref *drawable_ref)
{
	return (struct gfx_drawable *)java_paintable_new((jweak)drawable_ref);
}

// any GdkPaintable: the gtk-only Drawable natives make the others
void gfx_drawable_ref(struct gfx_drawable *drawable)
{
	g_object_ref(drawable);
}

void gfx_drawable_unref(struct gfx_drawable *drawable)
{
	g_object_unref(drawable);
}
