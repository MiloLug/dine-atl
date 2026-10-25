#ifndef SLOTS_GFX_H
#define SLOTS_GFX_H

#include <stdint.h>
#include <stdio.h>

/* gfx slot: paint state, drawables, drawing and text. */

struct gfx_paint;
struct gfx_drawable;
struct gfx_bitmap;
struct gfx_canvas;
struct atl_ref;
struct _graphene_matrix_t; // graphene_matrix_t

struct gfx_rect {
	float left, top, right, bottom;
};

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
enum gfx_font_family {
	GFX_FONT_SANS,
	GFX_FONT_SERIF,
	GFX_FONT_MONOSPACE,
};
#define GFX_FONT_BOLD   1 // Typeface.BOLD
#define GFX_FONT_ITALIC 2 // Typeface.ITALIC
void gfx_paint_set_typeface(struct gfx_paint *paint, enum gfx_font_family family, int style);
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

/* Bitmap.Config's nativeInt */
enum gfx_bitmap_config {
	GFX_BITMAP_ALPHA_8 = 1,
	GFX_BITMAP_RGB_565 = 3,
	GFX_BITMAP_ARGB_4444 = 4,
	GFX_BITMAP_ARGB_8888 = 5,
	GFX_BITMAP_RGBA_F16 = 6,
	GFX_BITMAP_HARDWARE = 7,
};

/* A Bitmap's pixels. Java's Bitmap holds a ref and display lists their own; refs drop on any
 * thread. gsk: a GdkTexture. */
struct gfx_bitmap *gfx_bitmap_new(int width, int height, enum gfx_bitmap_config config); // transparent
struct gfx_bitmap *gfx_bitmap_decode(const void *data, size_t size); // NULL: an image it can't read
int gfx_bitmap_get_width(const struct gfx_bitmap *bitmap);
int gfx_bitmap_get_height(const struct gfx_bitmap *bitmap);
void gfx_bitmap_ref(struct gfx_bitmap *bitmap);
void gfx_bitmap_unref(struct gfx_bitmap *bitmap);
/* BitmapDrawable's: the bitmap scaled to the size it's drawn at */
struct gfx_drawable *gfx_bitmap_drawable_new(struct gfx_bitmap *bitmap);

/* What android.atl.ATLCanvas draws into, on the thread that draws (a view's draw: the Android main
 * thread). gsk: a GtkSnapshot; record and coregraphics: a display list whose ops keep their paint
 * and matrix as they were (coregraphics: uikit's layers replay it). Transforms apply before the
 * current matrix, as Canvas's do. */
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
enum gfx_clip_op {
	GFX_CLIP_INTERSECT,
	GFX_CLIP_DIFFERENCE,
};
/* Until the restore of the save before it, under the current matrix. */
void gfx_canvas_clip_rect(struct gfx_canvas *canvas, float left, float top, float right, float bottom, enum gfx_clip_op op);
/* src: the bitmap's part, in its pixels; NULL: all of it */
void gfx_canvas_draw_bitmap(struct gfx_canvas *canvas, struct gfx_bitmap *bitmap, const struct gfx_rect *src, const struct gfx_rect *dst, const struct gfx_paint *paint);
/* x, y: the start of the baseline, before the paint's align moves it */
void gfx_canvas_draw_text(struct gfx_canvas *canvas, const uint16_t *text, size_t len, float x, float y, const struct gfx_paint *paint);

/* Text in the paint's font, in UTF-16 units as Java's strings, on any thread. */
struct gfx_font_metrics {
	float top, ascent, descent, bottom, leading; // Paint.FontMetrics: above the baseline is negative
};
void gfx_text_font_metrics(const struct gfx_paint *paint, struct gfx_font_metrics *metrics);
float gfx_text_measure(const struct gfx_paint *paint, const uint16_t *text, size_t len);
/* x[i]: the caret before unit i, i <= len (x[len] is the advance); a unit inside a cluster gets the
 * cluster's start */
void gfx_text_carets(const struct gfx_paint *paint, const uint16_t *text, size_t len, float *x);
/* The glyphs' ink around the origin on the baseline; empty text: all 0. */
void gfx_text_bounds(const struct gfx_paint *paint, const uint16_t *text, size_t len, struct gfx_rect *bounds);
/* A paragraph (no '\n') broken where its lines fit width: *ends (malloc'd) gets each line's end, a
 * line of at least one code point. Returns the line count, 1 for empty text. */
size_t gfx_text_break_lines(const struct gfx_paint *paint, const uint16_t *text, size_t len, float width, size_t **ends);
#ifdef ATL_TEST_HOOKS
/* For the records ui backends' dump: a "displaylist:" line per op. gsk prints nothing. */
void gfx_canvas_dump(const struct gfx_canvas *canvas, FILE *out);
#endif

#endif
