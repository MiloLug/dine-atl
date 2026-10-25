#ifndef GFX_COREGRAPHICS_H
#define GFX_COREGRAPHICS_H

#include <CoreGraphics/CoreGraphics.h>
#include <CoreText/CoreText.h>

#include "src/slots/gfx.h"

/* record's display lists, drawn with CoreGraphics by the ui backend that holds the CGContext
 * (uikit). A list is plain data: any thread may hold one, gfx_canvas_free drops a ref. */
struct gfx_canvas *cg_list_ref(struct gfx_canvas *list);
/* The list's px into a context in points; scale: px per point. */
void cg_list_draw(const struct gfx_canvas *list, CGContextRef context, CGFloat scale);

struct gfx_bitmap;
CGImageRef cg_bitmap_image(const struct gfx_bitmap *bitmap); // NULL: blank

/* The font of gfx's text and uikit's labels; size in points. Any thread; the caller releases it. */
CTFontRef cg_font(CGFloat size, enum gfx_font_family family, int style);
/* The text in the paint's font at its size in points (scale: px per point). */
CTLineRef cg_text_line(const struct gfx_paint *paint, CGFloat scale, const uint16_t *text, size_t len);

#endif
