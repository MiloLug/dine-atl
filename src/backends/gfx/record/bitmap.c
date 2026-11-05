#include <png.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/pixels.h"
#include "src/slots/gfx.h"

#include "record.h"

/* libpng reads PNGs, the only images decoded here, and writes them. */

uint32_t *gfx_image_decode(const void *data, size_t size, int *width, int *height)
{
	png_image image = {.version = PNG_IMAGE_VERSION};
	if (!png_image_begin_read_from_memory(&image, data, size))
		return NULL;
	image.format = PNG_FORMAT_BGRA; // a little-endian ARGB word per pixel
	uint32_t *words = malloc(PNG_IMAGE_SIZE(image));
	if (!png_image_finish_read(&image, NULL, words, 0, NULL)) {
		free(words);
		png_image_free(&image);
		return NULL;
	}
	for (size_t i = 0; i < (size_t)image.width * image.height; i++)
		words[i] = argb_premultiply(words[i]);
	*width = image.width;
	*height = image.height;
	return words;
}

void *gfx_image_encode_png(const uint32_t *words, int width, int height, size_t *size)
{
	size_t n = (size_t)width * height;
	uint32_t *argb = malloc((n ? n : 1) * sizeof(*argb));
	for (size_t i = 0; i < n; i++)
		argb[i] = argb_unpremultiply(words[i]);
	png_image image = {.version = PNG_IMAGE_VERSION, .width = width, .height = height, .format = PNG_FORMAT_BGRA};
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

// headless shows no bitmap, so none has a display image
void gfx_bitmap_image_free(void *image) {}

// record keeps nothing on its ops
void record_op_cache_free(void *cache) {}
