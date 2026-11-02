#ifndef GFX_GSK_CANVAS_H
#define GFX_GSK_CANVAS_H

#include <gtk/gtk.h>

struct gfx_paint;
struct gfx_path;

GskPath *gsk_path_from_gfx(const struct gfx_path *path);
/* A bitmap drawable's color filter; NULL for any other paintable */
struct gfx_paint *gsk_bitmap_drawable_filter(GdkPaintable *paintable);

#endif
