#ifndef SLOTS_GFX_H
#define SLOTS_GFX_H

#include <stdint.h>
#include <stdio.h>

/* gfx slot: paint state, drawables and drawing. */

struct gfx_paint;
struct gfx_drawable;
struct gfx_canvas;
struct atl_ref;
struct _graphene_matrix_t; // graphene_matrix_t

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
/* A DrawableContainer (StateListDrawable, LevelListDrawable, AnimationDrawable): shows the child
 * Java selects, held while selected; NULL: none. */
struct gfx_drawable *gfx_container_new(void);
void gfx_container_select(struct gfx_drawable *container, struct gfx_drawable *child);

/* What android.atl.ATLCanvas draws into, on the thread that draws (a view's draw: the Android main
 * thread). gsk: a GtkSnapshot; record: a display list whose ops keep their paint and matrix as they
 * were. Transforms apply before the current matrix, as Canvas's do. */
struct gfx_canvas *gfx_canvas_new(void); // an empty recording
void gfx_canvas_free(struct gfx_canvas *canvas);
void gfx_canvas_save(struct gfx_canvas *canvas);
void gfx_canvas_restore(struct gfx_canvas *canvas);
void gfx_canvas_translate(struct gfx_canvas *canvas, float dx, float dy);
void gfx_canvas_rotate(struct gfx_canvas *canvas, float degrees);
void gfx_canvas_scale(struct gfx_canvas *canvas, float sx, float sy);
void gfx_canvas_concat(struct gfx_canvas *canvas, const struct _graphene_matrix_t *matrix);
void gfx_canvas_draw_rect(struct gfx_canvas *canvas, float left, float top, float right, float bottom, const struct gfx_paint *paint);
void gfx_canvas_draw_round_rect(struct gfx_canvas *canvas, float left, float top, float right, float bottom, float rx, float ry, const struct gfx_paint *paint);
void gfx_canvas_draw_line(struct gfx_canvas *canvas, float x0, float y0, float x1, float y1, const struct gfx_paint *paint);
/* Drawable.draw at 0,0 in width x height; one drawn by the Drawable's own draw() is skipped. */
void gfx_canvas_draw_drawable(struct gfx_canvas *canvas, struct gfx_drawable *drawable, int width, int height);
#ifdef ATL_TEST_HOOKS
/* For the records ui backends' dump: a "displaylist:" line per op. gsk prints nothing. */
void gfx_canvas_dump(const struct gfx_canvas *canvas, FILE *out);
#endif

#endif
