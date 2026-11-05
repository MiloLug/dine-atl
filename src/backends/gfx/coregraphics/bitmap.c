#include <ImageIO/ImageIO.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/bitmap.h"
#include "src/slots/gfx.h"

#include "coregraphics.h"

/* A bitmap's display image is a copy of its words that an Android thread publishes
 * (cg_bitmap_publish): UIKit's thread only takes it, under the short image lock, and never waits
 * for a draw. */

static CGColorSpaceRef space;

static void make_space(void)
{
	space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
}

CGColorSpaceRef cg_srgb(void)
{
	static pthread_once_t once = PTHREAD_ONCE_INIT;
	pthread_once(&once, make_space);
	return space;
}

static void free_copy(void *info, const void *data, size_t size)
{
	free((void *)data);
}

// premultiplied ARGB words, CG's 32-bit little-endian premultiplied-first; copy: words are the image's own
static CGImageRef image_of_words(const uint32_t *words, int width, int height, bool copy)
{
	size_t size = (size_t)width * height * 4;
	const void *data = words;
	if (copy) {
		void *own = malloc(size ? size : 1);
		memcpy(own, words, size);
		data = own;
	}
	CGDataProviderRef provider = CGDataProviderCreateWithData(NULL, data, size, copy ? free_copy : NULL);
	CGImageRef image = CGImageCreate(width, height, 8, 32, width * 4, cg_srgb(), kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little,
	                                 provider, NULL, false, kCGRenderingIntentDefault);
	CGDataProviderRelease(provider);
	return image;
}

void gfx_bitmap_image_free(void *image)
{
	CGImageRelease(image);
}

CGImageRef cg_bitmap_image(const struct gfx_bitmap *bitmap, bool live, unsigned *generation)
{
	if (!bitmap->width || !bitmap->height)
		return NULL;
	if (live) {
		*generation = atomic_load(&((struct gfx_bitmap *)bitmap)->generation);
		return image_of_words(bitmap->words, bitmap->width, bitmap->height, false);
	}
	struct gfx_bitmap *held = (struct gfx_bitmap *)bitmap;
	pthread_mutex_lock(&held->image_lock);
	CGImageRef image = held->image ? CGImageRetain(held->image) : NULL;
	*generation = held->image_generation;
	pthread_mutex_unlock(&held->image_lock);
	return image;
}

void cg_bitmap_publish(struct gfx_bitmap *bitmap)
{
	unsigned generation = atomic_load(&bitmap->generation);
	pthread_mutex_lock(&bitmap->image_lock);
	bool current = bitmap->image && bitmap->image_generation == generation;
	pthread_mutex_unlock(&bitmap->image_lock);
	if (current || !bitmap->width || !bitmap->height)
		return;
	CGImageRef image = image_of_words(bitmap->words, bitmap->width, bitmap->height, true);
	pthread_mutex_lock(&bitmap->image_lock);
	CGImageRef old = image;
	if (!bitmap->image || (int)(generation - bitmap->image_generation) > 0) { // another thread's may be newer
		old = bitmap->image;
		bitmap->image = (void *)image;
		bitmap->image_generation = generation;
	}
	pthread_mutex_unlock(&bitmap->image_lock);
	if (old)
		CGImageRelease(old);
}

uint32_t *gfx_image_decode(const void *data, size_t size, int *width, int *height)
{
	CFDataRef bytes = CFDataCreate(NULL, data, size);
	CGImageSourceRef source = CGImageSourceCreateWithData(bytes, NULL);
	CFRelease(bytes);
	if (!source)
		return NULL;
	CGImageRef image = CGImageSourceCreateImageAtIndex(source, 0, NULL);
	CFRelease(source);
	if (!image)
		return NULL;
	*width = CGImageGetWidth(image);
	*height = CGImageGetHeight(image);
	uint32_t *words = calloc((size_t)*width * *height ? (size_t)*width * *height : 1, 4);
	CGContextRef context = CGBitmapContextCreate(words, *width, *height, 8, *width * 4, cg_srgb(), kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
	if (context) {
		CGContextSetBlendMode(context, kCGBlendModeCopy);
		CGContextDrawImage(context, CGRectMake(0, 0, *width, *height), image);
		CGContextRelease(context);
	}
	CGImageRelease(image);
	return words;
}

void *gfx_image_encode_png(const uint32_t *words, int width, int height, size_t *size)
{
	CGImageRef image = image_of_words(words, width, height, false);
	if (!image)
		return NULL;
	CFMutableDataRef data = CFDataCreateMutable(NULL, 0);
	CGImageDestinationRef destination = CGImageDestinationCreateWithData(data, CFSTR("public.png"), 1, NULL);
	void *png = NULL;
	if (destination) {
		CGImageDestinationAddImage(destination, image, NULL);
		if (CGImageDestinationFinalize(destination)) {
			*size = CFDataGetLength(data);
			png = malloc(*size);
			memcpy(png, CFDataGetBytePtr(data), *size);
		}
		CFRelease(destination);
	}
	CFRelease(data);
	CGImageRelease(image);
	return png;
}
