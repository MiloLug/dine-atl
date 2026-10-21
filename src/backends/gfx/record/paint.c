#include <stdatomic.h>
#include <stdlib.h>

#include "src/shared/test_hooks.h"
#include "src/shared/upcall.h"
#include "src/slots/gfx.h"

/* Plain records; nothing on the headless paths draws yet. */

struct gfx_paint {
	uint32_t color;
	enum gfx_paint_style style;
	float stroke_width;
	int stroke_cap;
	int stroke_join;
	float text_size;
	int text_align;
	int filter_mode;
	uint32_t filter_color;
};

struct gfx_drawable {
	struct atl_ref *drawable_ref; // NULL for a container
	atomic_int refs;
	struct gfx_drawable *selected; // a container's child, held
};

struct gfx_paint *gfx_paint_new(void)
{
	struct gfx_paint *paint = calloc(1, sizeof(*paint));
	paint->color = 0xFF000000;
	paint->stroke_width = 1;
	paint->text_size = 12;
	paint->filter_mode = -1;
	return paint;
}

struct gfx_paint *gfx_paint_clone(const struct gfx_paint *paint)
{
	struct gfx_paint *clone = malloc(sizeof(*clone));
	*clone = *paint;
	return clone;
}

void gfx_paint_free(struct gfx_paint *paint)
{
	free(paint);
}

void gfx_paint_set_color(struct gfx_paint *paint, uint32_t argb)
{
	paint->color = argb;
}

uint32_t gfx_paint_get_color(const struct gfx_paint *paint)
{
	return paint->color;
}

void gfx_paint_set_alpha(struct gfx_paint *paint, uint8_t alpha)
{
	paint->color = (paint->color & 0x00FFFFFF) | (uint32_t)alpha << 24;
}

uint8_t gfx_paint_get_alpha(const struct gfx_paint *paint)
{
	return paint->color >> 24;
}

void gfx_paint_set_style(struct gfx_paint *paint, enum gfx_paint_style style)
{
	paint->style = style;
}

enum gfx_paint_style gfx_paint_get_style(const struct gfx_paint *paint)
{
	return paint->style;
}

void gfx_paint_set_stroke_width(struct gfx_paint *paint, float width)
{
	paint->stroke_width = width;
}

float gfx_paint_get_stroke_width(const struct gfx_paint *paint)
{
	return paint->stroke_width;
}

void gfx_paint_set_stroke_cap(struct gfx_paint *paint, int cap)
{
	paint->stroke_cap = cap;
}

int gfx_paint_get_stroke_cap(const struct gfx_paint *paint)
{
	return paint->stroke_cap;
}

void gfx_paint_set_stroke_join(struct gfx_paint *paint, int join)
{
	paint->stroke_join = join;
}

int gfx_paint_get_stroke_join(const struct gfx_paint *paint)
{
	return paint->stroke_join;
}

void gfx_paint_set_text_size(struct gfx_paint *paint, float size)
{
	paint->text_size = size;
}

float gfx_paint_get_text_size(const struct gfx_paint *paint)
{
	return paint->text_size;
}

void gfx_paint_set_text_align(struct gfx_paint *paint, int align)
{
	paint->text_align = align;
}

void gfx_paint_set_color_filter(struct gfx_paint *paint, int mode, uint32_t argb)
{
	paint->filter_mode = mode;
	paint->filter_color = argb;
}

struct gfx_drawable *gfx_drawable_new(struct atl_ref *drawable_ref)
{
	struct gfx_drawable *drawable = calloc(1, sizeof(*drawable));
	drawable->drawable_ref = drawable_ref;
	drawable->refs = 1;
	return drawable;
}

void gfx_drawable_ref(struct gfx_drawable *drawable)
{
	atomic_fetch_add(&drawable->refs, 1);
}

void gfx_drawable_unref(struct gfx_drawable *drawable)
{
	if (atomic_fetch_sub(&drawable->refs, 1) != 1)
		return;
	atl_test_drawable_freed("record");
	if (drawable->selected)
		gfx_drawable_unref(drawable->selected);
	if (drawable->drawable_ref)
		atl_ref_free(drawable->drawable_ref);
	free(drawable);
}

void gfx_drawable_invalidate(struct gfx_drawable *drawable) {}

struct gfx_drawable *gfx_container_new(void)
{
	return gfx_drawable_new(NULL);
}

// the selection is kept for M5's drawing
void gfx_container_select(struct gfx_drawable *container, struct gfx_drawable *child)
{
	if (child)
		gfx_drawable_ref(child);
	if (container->selected)
		gfx_drawable_unref(container->selected);
	container->selected = child;
}
