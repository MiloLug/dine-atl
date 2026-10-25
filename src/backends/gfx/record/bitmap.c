#include <png.h>
#include <stdatomic.h>
#include <stdlib.h>

#include "src/slots/gfx.h"

/* Pixels as RGBA bytes, whatever the config; libpng reads PNGs, the only images decoded here. */

struct gfx_bitmap {
	atomic_int refs;
	int width, height;
	enum gfx_bitmap_config config;
	uint8_t *pixels;
};

static struct gfx_bitmap *bitmap_new(int width, int height, enum gfx_bitmap_config config, uint8_t *pixels)
{
	struct gfx_bitmap *bitmap = malloc(sizeof(*bitmap));
	*bitmap = (struct gfx_bitmap){1, width, height, config, pixels};
	return bitmap;
}

struct gfx_bitmap *gfx_bitmap_new(int width, int height, enum gfx_bitmap_config config)
{
	return bitmap_new(width, height, config, calloc((size_t)width * height, 4));
}

struct gfx_bitmap *gfx_bitmap_decode(const void *data, size_t size)
{
	png_image image = {.version = PNG_IMAGE_VERSION};
	if (!png_image_begin_read_from_memory(&image, data, size))
		return NULL;
	image.format = PNG_FORMAT_RGBA;
	uint8_t *pixels = malloc(PNG_IMAGE_SIZE(image));
	if (!png_image_finish_read(&image, NULL, pixels, 0, NULL)) {
		free(pixels);
		png_image_free(&image);
		return NULL;
	}
	return bitmap_new(image.width, image.height, GFX_BITMAP_ARGB_8888, pixels);
}

int gfx_bitmap_get_width(const struct gfx_bitmap *bitmap)
{
	return bitmap->width;
}

int gfx_bitmap_get_height(const struct gfx_bitmap *bitmap)
{
	return bitmap->height;
}

void gfx_bitmap_ref(struct gfx_bitmap *bitmap)
{
	atomic_fetch_add(&bitmap->refs, 1);
}

void gfx_bitmap_unref(struct gfx_bitmap *bitmap)
{
	if (atomic_fetch_sub(&bitmap->refs, 1) != 1)
		return;
	free(bitmap->pixels);
	free(bitmap);
}
