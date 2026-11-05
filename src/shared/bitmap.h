#ifndef SHARED_BITMAP_H
#define SHARED_BITMAP_H

#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>

#include "src/slots/gfx.h"

/* A Bitmap's pixels: premultiplied ARGB words (pixels.h) whatever the config, allocated once and
 * never moved, so a bitmap canvas's context over them stays valid. Nothing guards the words, as on
 * Android: a draw racing a read on another thread can tear, never reach freed words (refs keep
 * them). Configs round as Android 10's Skia stores them (bitmap_round). */
struct gfx_bitmap {
	atomic_int refs;
	int width, height;
	enum gfx_bitmap_config config;
	uint32_t *words;
	atomic_uint generation; // bumped by every change
	pthread_mutex_t image_lock; // held only to swap or take image
	void *image;                // the backend's display copy of the words at image_generation; NULL: none
	unsigned image_generation;
};

/* The words in [x0, x1) x [y0, y1) (clamped) as the config stores them; then the change counts. */
void bitmap_round(struct gfx_bitmap *bitmap, int x0, int y0, int x1, int y1);
void bitmap_changed(struct gfx_bitmap *bitmap);
/* A copy of src's words, src's own pixels for a draw of src into itself. */
struct gfx_bitmap *bitmap_snapshot(const struct gfx_bitmap *src);

/* AndroidBitmap_lockPixels' buffer: the pixels in the config's NDK format (ARGB_8888: R, G, B, A
 * bytes, premultiplied; RGBA_F16 halves), rows row_bytes apart; unpack writes them back. */
void bitmap_pack(const struct gfx_bitmap *bitmap, uint8_t *dst, int row_bytes);
void bitmap_unpack(struct gfx_bitmap *bitmap, const uint8_t *src, int row_bytes);
int bitmap_bytes_per_pixel(enum gfx_bitmap_config config);

#endif
