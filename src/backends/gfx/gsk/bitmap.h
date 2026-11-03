#ifndef GFX_GSK_BITMAP_H
#define GFX_GSK_BITMAP_H

#include <gdk/gdk.h>
#include <stdatomic.h>

#include "src/slots/gfx.h"

/* A bitmap holds a GdkTexture, which can't change: new pixels are a new texture, swapped in by
 * whichever thread draws into the bitmap while others may draw it. lock guards only the pointer:
 * a user takes its own ref under it (gsk_bitmap_ref_texture). */
struct gfx_bitmap {
	atomic_int refs;
	GMutex lock;
	GdkTexture *texture;
	GMutex download_lock; // the readers': a swap never waits on a download
	GdkTexture *downloaded; // a ref; what download holds the straight ARGB words of
	uint32_t *download;
};

GdkTexture *gsk_bitmap_ref_texture(struct gfx_bitmap *bitmap);
void gsk_bitmap_set_texture(struct gfx_bitmap *bitmap, GdkTexture *texture); // takes the ref

#endif
