#include <ImageIO/ImageIO.h>
#include <stdatomic.h>
#include <stdlib.h>

#include "src/slots/gfx.h"

#include "coregraphics.h"

/* A CGImage, decoded at once on the thread that decodes, so a layer's draw doesn't. */

struct gfx_bitmap {
	atomic_int refs;
	int width, height;
	CGImageRef image; // NULL: blank
};

static struct gfx_bitmap *bitmap_new(int width, int height, CGImageRef image)
{
	struct gfx_bitmap *bitmap = malloc(sizeof(*bitmap));
	*bitmap = (struct gfx_bitmap){1, width, height, image};
	return bitmap;
}

struct gfx_bitmap *gfx_bitmap_new(int width, int height, enum gfx_bitmap_config config)
{
	return bitmap_new(width, height, NULL);
}

struct gfx_bitmap *gfx_bitmap_decode(const void *data, size_t size)
{
	CFDataRef bytes = CFDataCreate(NULL, data, size);
	CGImageSourceRef source = CGImageSourceCreateWithData(bytes, NULL);
	CFRelease(bytes);
	if (!source)
		return NULL;
	const void *keys[] = {kCGImageSourceShouldCacheImmediately};
	const void *values[] = {kCFBooleanTrue};
	CFDictionaryRef options = CFDictionaryCreate(NULL, keys, values, 1, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
	CGImageRef image = CGImageSourceCreateImageAtIndex(source, 0, options);
	CFRelease(options);
	CFRelease(source);
	return image ? bitmap_new(CGImageGetWidth(image), CGImageGetHeight(image), image) : NULL;
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
	if (bitmap->image)
		CGImageRelease(bitmap->image);
	free(bitmap);
}

CGImageRef cg_bitmap_image(const struct gfx_bitmap *bitmap)
{
	return bitmap->image;
}
