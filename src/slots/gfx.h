#ifndef SLOTS_GFX_H
#define SLOTS_GFX_H

#include <stdint.h>

/* gfx slot: paint state, drawables and drawing. */

struct gfx_paint;
struct gfx_drawable;
struct atl_ref;

/* Paint state. The enums take Android's values: Paint.Style, Cap, Join and Align ordinals. */
enum gfx_paint_style {
	GFX_PAINT_FILL,
	GFX_PAINT_STROKE,
	GFX_PAINT_FILL_AND_STROKE,
};

struct gfx_paint *gfx_paint_new(void);
struct gfx_paint *gfx_paint_clone(const struct gfx_paint *paint);
void gfx_paint_free(struct gfx_paint *paint);
void gfx_paint_set_color(struct gfx_paint *paint, uint32_t argb);
uint32_t gfx_paint_get_color(const struct gfx_paint *paint);
void gfx_paint_set_alpha(struct gfx_paint *paint, uint8_t alpha);
uint8_t gfx_paint_get_alpha(const struct gfx_paint *paint);
void gfx_paint_set_style(struct gfx_paint *paint, enum gfx_paint_style style);
enum gfx_paint_style gfx_paint_get_style(const struct gfx_paint *paint);
void gfx_paint_set_stroke_width(struct gfx_paint *paint, float width);
float gfx_paint_get_stroke_width(const struct gfx_paint *paint);
void gfx_paint_set_stroke_cap(struct gfx_paint *paint, int cap);
int gfx_paint_get_stroke_cap(const struct gfx_paint *paint);
void gfx_paint_set_stroke_join(struct gfx_paint *paint, int join);
int gfx_paint_get_stroke_join(const struct gfx_paint *paint);
void gfx_paint_set_text_size(struct gfx_paint *paint, float size);
float gfx_paint_get_text_size(const struct gfx_paint *paint);
void gfx_paint_set_text_align(struct gfx_paint *paint, int align);
/* PorterDuff mode, or -1 for none */
void gfx_paint_set_color_filter(struct gfx_paint *paint, int mode, uint32_t argb);
/* For a Drawable whose class overrides draw(); the backend draws it by calling draw(). */
struct gfx_drawable *gfx_drawable_new(struct atl_ref *drawable_ref);
/* Drawables share one through these; the last unref frees it. ref: any thread; unref: the Android
 * main thread. */
void gfx_drawable_ref(struct gfx_drawable *drawable);
void gfx_drawable_unref(struct gfx_drawable *drawable);
/* Its content changed: redraw what shows it. */
void gfx_drawable_invalidate(struct gfx_drawable *drawable);

#endif
