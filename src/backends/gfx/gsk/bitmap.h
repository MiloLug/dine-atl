#ifndef GFX_GSK_BITMAP_H
#define GFX_GSK_BITMAP_H

#include <gdk/gdk.h>

#include "src/shared/bitmap.h"

/* A texture of the bitmap's pixels as they are now (a ref): a copy made once per change. */
GdkTexture *gsk_bitmap_texture(struct gfx_bitmap *bitmap);

#endif
