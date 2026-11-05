#ifndef GFX_COREGRAPHICS_H
#define GFX_COREGRAPHICS_H

#include <CoreGraphics/CoreGraphics.h>
#include <CoreText/CoreText.h>
#include <stdbool.h>

#include "src/slots/gfx.h"

/* record's display lists, drawn with CoreGraphics by the ui backend that holds the CGContext
 * (uikit). A list is plain data: any thread may hold one, gfx_canvas_free drops a ref. */
struct gfx_canvas *cg_list_ref(struct gfx_canvas *list);
/* The list's px into a context in points; scale: px per point. */
void cg_list_draw(const struct gfx_canvas *list, CGContextRef context, CGFloat scale);

struct gfx_bitmap;
/* The bitmap's pixels as an image (retained; NULL: blank) and their generation. live: an image over
 * its words, for a draw on a thread that may draw into them; else the copy last published, the
 * only kind UIKit's thread takes. */
CGImageRef cg_bitmap_image(const struct gfx_bitmap *bitmap, bool live, unsigned *generation);
// On the Android thread that drew them: the copy UIKit's replays draw, made if the pixels changed.
void cg_bitmap_publish(struct gfx_bitmap *bitmap);
// every bitmap a list draws (its bitmap ops and bitmap shaders), published
void cg_list_publish(const struct gfx_canvas *list);

// CG's blend mode for a gfx one, and whether it is Skia's; the others a painter blends per pixel
CGBlendMode cg_blend(enum gfx_blend mode);
bool cg_blend_exact(enum gfx_blend mode);
CGColorSpaceRef cg_srgb(void); // shared, never released
/* One op of a list into a context, as cg_list_draw draws it; for painter.c. live: cg_bitmap_image's. */
struct op;
void cg_draw(CGContextRef context, const struct op *op, CGFloat scale, bool live);
void cg_clip(CGContextRef context, const struct op *op); // OP_CLIP's or OP_CLIP_PATH's, in the current gstate

/* The font of gfx's text and uikit's labels; size in points. Any thread; the caller releases it. */
CTFontRef cg_font(CGFloat size, enum gfx_font_family family, int style);
/* The text in the paint's font at its size in points (scale: px per point). */
CTLineRef cg_text_line(const struct gfx_paint *paint, CGFloat scale, const uint16_t *text, size_t len);

#endif
