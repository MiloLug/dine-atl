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

struct gfx_paint *gfx_paint_clone(const struct gfx_paint *gfx_paint)
{
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	struct AndroidPaint *clone = g_memdup2(paint, sizeof(struct AndroidPaint));
	clone->gsk_stroke = gsk_stroke_copy(paint->gsk_stroke);
	clone->font = pango_font_description_copy(paint->font);
	return (struct gfx_paint *)clone;
}

void gfx_paint_free(struct gfx_paint *gfx_paint)
{
	struct AndroidPaint *paint = (struct AndroidPaint *)gfx_paint;
	gsk_stroke_free(paint->gsk_stroke);
	pango_font_description_free(paint->font);
	g_free(paint);
}

void gfx_paint_set_color(struct gfx_paint *gfx_paint, uint32_t color)
{
	struct AndroidPaint *paint = (struct AndroidPaint *)gfx_paint;
	paint->color.red = ((color >> 16) & 0xFF) / 255.f;
	paint->color.green = ((color >> 8) & 0xFF) / 255.f;
	paint->color.blue = ((color >> 0) & 0xFF) / 255.f;
	paint->color.alpha = ((color >> 24) & 0xFF) / 255.f;
}

uint32_t gfx_paint_get_color(const struct gfx_paint *gfx_paint)
{
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	return ((uint32_t)(paint->color.red * 0xFF) << 16) + ((uint32_t)(paint->color.green * 0xFF) << 8) + ((uint32_t)(paint->color.blue * 0xFF) << 0) + ((uint32_t)(paint->color.alpha * 0xFF) << 24);
}

void gfx_paint_set_alpha(struct gfx_paint *gfx_paint, uint8_t alpha)
{
	((struct AndroidPaint *)gfx_paint)->color.alpha = alpha / 255.f;
}

uint8_t gfx_paint_get_alpha(const struct gfx_paint *gfx_paint)
{
	return ((const struct AndroidPaint *)gfx_paint)->color.alpha * 0xFF;
}

void gfx_paint_set_style(struct gfx_paint *gfx_paint, enum gfx_paint_style style)
{
	struct AndroidPaint *paint = (struct AndroidPaint *)gfx_paint;
	paint->is_fill = style == GFX_PAINT_FILL || style == GFX_PAINT_FILL_AND_STROKE;
	paint->is_stroke = style == GFX_PAINT_STROKE || style == GFX_PAINT_FILL_AND_STROKE;
}

enum gfx_paint_style gfx_paint_get_style(const struct gfx_paint *gfx_paint)
{
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	if (paint->is_fill && paint->is_stroke)
		return GFX_PAINT_FILL_AND_STROKE;
	return paint->is_fill ? GFX_PAINT_FILL : GFX_PAINT_STROKE;
}

void gfx_paint_set_stroke_width(struct gfx_paint *gfx_paint, float width)
{
	/* TODO: width of 0 means "single pixel width, no matter what",
	 * meanwile width of 1 should care about scaling;
	 * The problem is, 0 is not a valid value for gsk_stroke_set_line_width,
	 * so at least change it to 1 for now */
	if (width == 0)
		width = 1;
	gsk_stroke_set_line_width(((struct AndroidPaint *)gfx_paint)->gsk_stroke, width);
}

float gfx_paint_get_stroke_width(const struct gfx_paint *gfx_paint)
{
	return gsk_stroke_get_line_width(((const struct AndroidPaint *)gfx_paint)->gsk_stroke);
}

// GskLineCap and GskLineJoin share Android's ordinals
void gfx_paint_set_stroke_cap(struct gfx_paint *gfx_paint, int cap)
{
	gsk_stroke_set_line_cap(((struct AndroidPaint *)gfx_paint)->gsk_stroke, cap);
}

int gfx_paint_get_stroke_cap(const struct gfx_paint *gfx_paint)
{
	return gsk_stroke_get_line_cap(((const struct AndroidPaint *)gfx_paint)->gsk_stroke);
}

void gfx_paint_set_stroke_join(struct gfx_paint *gfx_paint, int join)
{
	gsk_stroke_set_line_join(((struct AndroidPaint *)gfx_paint)->gsk_stroke, join);
}

int gfx_paint_get_stroke_join(const struct gfx_paint *gfx_paint)
{
	return gsk_stroke_get_line_join(((const struct AndroidPaint *)gfx_paint)->gsk_stroke);
}

void gfx_paint_set_text_size(struct gfx_paint *gfx_paint, float size)
{
	pango_font_description_set_absolute_size(((struct AndroidPaint *)gfx_paint)->font, roundf(size * PANGO_SCALE));
}

float gfx_paint_get_text_size(const struct gfx_paint *gfx_paint)
{
	return (float)pango_font_description_get_size(((const struct AndroidPaint *)gfx_paint)->font) / PANGO_SCALE;
}

// PangoAlignment shares Android's ordinals
void gfx_paint_set_text_align(struct gfx_paint *gfx_paint, int align)
{
	((struct AndroidPaint *)gfx_paint)->alignment = align;
}

void gfx_paint_set_color_filter(struct gfx_paint *gfx_paint, int mode, uint32_t color)
{
	struct AndroidPaint *paint = (struct AndroidPaint *)gfx_paint;
	/* clang-format off */
	graphene_matrix_init_from_float(&paint->color_matrix, (float[]){
		0, 0, 0, 0,
		0, 0, 0, 0,
		0, 0, 0, 0,
		0, 0, 0, ((color >> 24) & 0xFF) / 255.f,
	});
	/* clang-format on */
	graphene_vec4_init(&paint->color_offset, ((color >> 16) & 0xFF) / 255.f, ((color >> 8) & 0xFF) / 255.f, ((color >> 0) & 0xFF) / 255.f, 0);
	paint->use_color_filter = mode != -1;
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

static gboolean queue_invalidate_contents(GdkPaintable *paintable)
{
	gdk_paintable_invalidate_contents(paintable);
	g_object_unref(paintable);
	return G_SOURCE_REMOVE;
}

void gfx_drawable_invalidate(struct gfx_drawable *drawable)
{
	// GTK doesn't allow invalidating a paintable while it's being drawn, so we need to queue it up
	g_idle_add_full(G_PRIORITY_HIGH_IDLE + 20, G_SOURCE_FUNC(queue_invalidate_contents), g_object_ref(GDK_PAINTABLE(drawable)), NULL);
}
