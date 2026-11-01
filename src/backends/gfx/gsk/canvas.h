#ifndef GFX_GSK_CANVAS_H
#define GFX_GSK_CANVAS_H

#include <gtk/gtk.h>

struct gfx_path;

/* For Region's gtk-only natives */
GskPath *gsk_path_from_gfx(const struct gfx_path *path);

#endif
