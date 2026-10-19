#include <stdlib.h>

#include "src/slots/gfx.h"

/* Plain records; nothing on the headless paths draws yet. */

struct gfx_paint {
	uint32_t color;
};

struct gfx_drawable {
	struct atl_ref *drawable_ref;
};

struct gfx_paint *gfx_paint_new(void)
{
	struct gfx_paint *paint = calloc(1, sizeof(*paint));
	paint->color = 0xFF000000;
	return paint;
}

void gfx_paint_set_color(struct gfx_paint *paint, uint32_t argb)
{
	paint->color = argb;
}

struct gfx_drawable *gfx_drawable_new(struct atl_ref *drawable_ref)
{
	struct gfx_drawable *drawable = calloc(1, sizeof(*drawable));
	drawable->drawable_ref = drawable_ref;
	return drawable;
}
