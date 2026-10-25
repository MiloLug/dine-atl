#ifndef GFX_COREGRAPHICS_H
#define GFX_COREGRAPHICS_H

#include <CoreGraphics/CoreGraphics.h>

struct gfx_canvas;

/* record's display lists, drawn with CoreGraphics by the ui backend that holds the CGContext
 * (uikit). A list is plain data: any thread may hold one, gfx_canvas_free drops a ref. */
struct gfx_canvas *cg_list_ref(struct gfx_canvas *list);
/* The list's px into a context in points; scale: px per point. */
void cg_list_draw(const struct gfx_canvas *list, CGContextRef context, CGFloat scale);

struct gfx_bitmap;
CGImageRef cg_bitmap_image(const struct gfx_bitmap *bitmap); // NULL: blank

#endif
