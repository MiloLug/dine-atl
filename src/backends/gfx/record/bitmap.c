#include <png.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/pixels.h"

#include "bitmap.h"
#include "record.h"

/* libpng reads PNGs, the only images decoded here, and writes them. */

static struct gfx_bitmap *bitmap_new(int width, int height, enum gfx_bitmap_config config, uint32_t *pixels)
{
	struct gfx_bitmap *bitmap = malloc(sizeof(*bitmap));
	*bitmap = (struct gfx_bitmap){1, width, height, config, pixels};
	return bitmap;
}

struct gfx_bitmap *gfx_bitmap_new(int width, int height, enum gfx_bitmap_config config)
{
	return bitmap_new(width, height, config, calloc((size_t)width * height, sizeof(uint32_t)));
}

struct gfx_bitmap *gfx_bitmap_decode(const void *data, size_t size)
{
	png_image image = {.version = PNG_IMAGE_VERSION};
	if (!png_image_begin_read_from_memory(&image, data, size))
		return NULL;
	image.format = PNG_FORMAT_BGRA; // a little-endian ARGB word per pixel
	uint32_t *pixels = malloc(PNG_IMAGE_SIZE(image));
	if (!png_image_finish_read(&image, NULL, pixels, 0, NULL)) {
		free(pixels);
		png_image_free(&image);
		return NULL;
	}
	for (size_t i = 0; i < (size_t)image.width * image.height; i++)
		pixels[i] = argb_premultiply(pixels[i]);
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

void gfx_bitmap_render(struct gfx_bitmap *bitmap, struct gfx_canvas *canvas)
{
	record_raster(canvas, bitmap->pixels, bitmap->width, bitmap->height);
	gfx_canvas_free(canvas);
}

void gfx_bitmap_erase(struct gfx_bitmap *bitmap, uint32_t argb)
{
	uint32_t pixel = argb_premultiply(argb);
	for (size_t i = 0; i < (size_t)bitmap->width * bitmap->height; i++)
		bitmap->pixels[i] = pixel;
}

void gfx_bitmap_read(const struct gfx_bitmap *bitmap, int x, int y, int width, int height, uint32_t *argb, int stride)
{
	for (int row = 0; row < height; row++)
		for (int col = 0; col < width; col++)
			argb[(ptrdiff_t)row * stride + col] = argb_unpremultiply(bitmap->pixels[(size_t)(y + row) * bitmap->width + x + col]);
}

void gfx_bitmap_write(struct gfx_bitmap *bitmap, int x, int y, int width, int height, const uint32_t *argb, int stride)
{
	for (int row = 0; row < height; row++)
		for (int col = 0; col < width; col++)
			bitmap->pixels[(size_t)(y + row) * bitmap->width + x + col] = argb_premultiply(argb[(ptrdiff_t)row * stride + col]);
}

void *gfx_bitmap_encode_png(const struct gfx_bitmap *bitmap, size_t *size)
{
	size_t n = (size_t)bitmap->width * bitmap->height;
	uint32_t *argb = malloc(n * sizeof(*argb));
	for (size_t i = 0; i < n; i++)
		argb[i] = argb_unpremultiply(bitmap->pixels[i]);
	png_image image = {.version = PNG_IMAGE_VERSION, .width = bitmap->width, .height = bitmap->height, .format = PNG_FORMAT_BGRA};
	png_alloc_size_t length = 0;
	void *png = NULL;
	if (png_image_write_get_memory_size(image, length, 0, argb, 0, NULL)) {
		png = malloc(length);
		if (!png_image_write_to_memory(&image, png, &length, 0, argb, 0, NULL)) {
			free(png);
			png = NULL;
		}
	}
	free(argb);
	*size = length;
	return png;
}
