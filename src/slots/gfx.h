#ifndef SLOTS_GFX_H
#define SLOTS_GFX_H

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

/* gfx slot: paint state, drawables, drawing and text. */

struct gfx_paint;
struct gfx_drawable;
struct gfx_bitmap;
struct gfx_canvas;
struct gfx_path;
struct gfx_shader;
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
/* src/shared/shader.h's, NULL for none; the paint and what copies it hold a ref */
void gfx_paint_set_shader(struct gfx_paint *paint, struct gfx_shader *shader);
/* SkBlendMode's numbers, as android.graphics.BlendMode and PorterDuff.Mode's nativeInt have them;
 * a paint's mode composites what it draws onto the canvas's own content or its innermost layer. */
enum gfx_blend {
	GFX_BLEND_CLEAR, GFX_BLEND_SRC, GFX_BLEND_DST, GFX_BLEND_SRC_OVER, GFX_BLEND_DST_OVER,
	GFX_BLEND_SRC_IN, GFX_BLEND_DST_IN, GFX_BLEND_SRC_OUT, GFX_BLEND_DST_OUT, GFX_BLEND_SRC_ATOP,
	GFX_BLEND_DST_ATOP, GFX_BLEND_XOR, GFX_BLEND_PLUS, GFX_BLEND_MODULATE, GFX_BLEND_SCREEN,
	GFX_BLEND_OVERLAY, GFX_BLEND_DARKEN, GFX_BLEND_LIGHTEN, GFX_BLEND_COLOR_DODGE, GFX_BLEND_COLOR_BURN,
	GFX_BLEND_HARD_LIGHT, GFX_BLEND_SOFT_LIGHT, GFX_BLEND_DIFFERENCE, GFX_BLEND_EXCLUSION,
	GFX_BLEND_MULTIPLY, GFX_BLEND_HUE, GFX_BLEND_SATURATION, GFX_BLEND_COLOR, GFX_BLEND_LUMINOSITY,
	GFX_BLEND_COUNT
};
void gfx_paint_set_blend(struct gfx_paint *paint, enum gfx_blend blend);
/* A ColorFilter: a blend mode with its color (the color over what the paint draws), or a
 * ColorMatrix's 4x5 array over unpremultiplied 0-255 channels (R' = m[0] R + m[1] G + m[2] B +
 * m[3] A + m[4], then G', B', A'). */
struct gfx_color_filter {
	int mode; // -1: none, or the matrix
	uint32_t color;
	bool has_matrix;
	float m[20];
};
#define GFX_NO_COLOR_FILTER ((struct gfx_color_filter){.mode = -1})
static inline bool gfx_color_filter_is_set(const struct gfx_color_filter *filter)
{
	return filter->has_matrix || filter->mode != -1;
}
void gfx_paint_set_color_filter(struct gfx_paint *paint, const struct gfx_color_filter *filter);
/* For a Drawable whose class overrides draw(); the backend draws it by calling draw(). */
struct gfx_drawable *gfx_drawable_new(struct atl_ref *drawable_ref);
/* Drawables share one through these; the last unref frees it. Any thread. */
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

/* A Bitmap's pixels: src/shared/bitmap.h's, the same on every backend. Java's Bitmap holds a ref
 * and display lists their own; refs drop on any thread. The pixels change in place: lists and
 * drawables that hold the bitmap draw the new ones from their next replay. Pixels in and out are
 * Color's ARGB ints, not premultiplied, rows stride ints apart (negative: bottom up). */
struct gfx_bitmap *gfx_bitmap_new(int width, int height, enum gfx_bitmap_config config); // transparent; RGB_565 opaque black
struct gfx_bitmap *gfx_bitmap_decode(const void *data, size_t size); // ARGB_8888; NULL: an image it can't read
struct gfx_bitmap *gfx_bitmap_copy(const struct gfx_bitmap *bitmap, enum gfx_bitmap_config config); // rounded to config
int gfx_bitmap_get_width(const struct gfx_bitmap *bitmap);
int gfx_bitmap_get_height(const struct gfx_bitmap *bitmap);
void gfx_bitmap_ref(struct gfx_bitmap *bitmap);
void gfx_bitmap_unref(struct gfx_bitmap *bitmap);
void gfx_bitmap_erase(struct gfx_bitmap *bitmap, uint32_t argb);
void gfx_bitmap_read(const struct gfx_bitmap *bitmap, int x, int y, int width, int height, uint32_t *argb, int stride);
void gfx_bitmap_write(struct gfx_bitmap *bitmap, int x, int y, int width, int height, const uint32_t *argb, int stride);
void *gfx_bitmap_encode_png(const struct gfx_bitmap *bitmap, size_t *size); // malloc'd, NULL: failed
/* The backend's part: images in and out as premultiplied ARGB words (malloc'd; NULL: unreadable or
 * failed), and freeing a bitmap's display image (struct gfx_bitmap's image). */
uint32_t *gfx_image_decode(const void *data, size_t size, int *width, int *height);
void *gfx_image_encode_png(const uint32_t *words, int width, int height, size_t *size);
void gfx_bitmap_image_free(void *image);
/* BitmapDrawable's: the bitmap scaled to the size it's drawn at */
struct gfx_drawable *gfx_bitmap_drawable_new(struct gfx_bitmap *bitmap);
struct gfx_ninepatch; // src/shared/ninepatch.h
/* NinePatchDrawable's: the bitmap stretched by the nine-patch's divs; takes ninepatch. */
struct gfx_drawable *gfx_ninepatch_drawable_new(struct gfx_bitmap *bitmap, struct gfx_ninepatch *ninepatch);
/* A bitmap or nine-patch drawable's color filter: its color filter or tint. */
void gfx_drawable_set_color_filter(struct gfx_drawable *drawable, const struct gfx_color_filter *filter);

/* What android.atl.ATLCanvas draws into, on the thread that draws (a view's draw: the Android main
 * thread). gsk: a GtkSnapshot; record and coregraphics: a display list whose ops keep their paint
 * and matrix as they were (coregraphics: uikit's layers replay it). Transforms apply before the
 * current matrix, as Canvas's do. */
struct gfx_canvas *gfx_canvas_new(void); // an empty recording
/* A Canvas(Bitmap)'s, on any one thread at a time: each call draws into the bitmap's pixels at
 * once, under the canvas's own matrix, clip, saves and layers, as Skia's raster canvas does. */
struct gfx_canvas *gfx_canvas_new_bitmap(struct gfx_bitmap *bitmap);
void gfx_canvas_free(struct gfx_canvas *canvas);
void gfx_canvas_save(struct gfx_canvas *canvas);
/* A save whose restore composites what was drawn since at alpha with blend; bounds (under the
 * current matrix) clip it, NULL: none. */
void gfx_canvas_save_layer(struct gfx_canvas *canvas, const struct gfx_rect *bounds, uint8_t alpha, enum gfx_blend blend);
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
/* path: src/shared/path.h's; a canvas that keeps it holds a ref. Inverse fill types aren't
 * drawn inverse. */
void gfx_canvas_draw_path(struct gfx_canvas *canvas, struct gfx_path *path, bool even_odd, const struct gfx_paint *paint);
void gfx_canvas_clip_path(struct gfx_canvas *canvas, struct gfx_path *path, bool even_odd, enum gfx_clip_op op);
/* Canvas.drawArc: degrees clockwise from the oval's right */
void gfx_canvas_draw_arc(struct gfx_canvas *canvas, float left, float top, float right, float bottom, float start, float sweep, bool use_center, const struct gfx_paint *paint);
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
/* A view's recording (gfx_canvas_new), freed, drawn into target as the window would show it (gsk:
 * the display's renderer, on the main thread); false: no backend renderer to ask. */
bool gfx_test_render_view(struct gfx_canvas *canvas, struct gfx_bitmap *target);
#endif

#endif
