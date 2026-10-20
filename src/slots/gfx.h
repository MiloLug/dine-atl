#ifndef SLOTS_GFX_H
#define SLOTS_GFX_H

#include <stdint.h>

/* gfx slot: paint state, drawables and drawing. */

struct gfx_paint;
struct gfx_drawable;
struct atl_ref;

struct gfx_paint *gfx_paint_new(void);
void gfx_paint_set_color(struct gfx_paint *paint, uint32_t argb);
/* For a Drawable whose class overrides draw(); the backend draws it by calling draw(). */
struct gfx_drawable *gfx_drawable_new(struct atl_ref *drawable_ref);
/* Drawables share one through these; the last unref frees it. ref: any thread; unref: the Android
 * main thread. */
void gfx_drawable_ref(struct gfx_drawable *drawable);
void gfx_drawable_unref(struct gfx_drawable *drawable);

#endif
