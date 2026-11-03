#include <ImageIO/ImageIO.h>
#include <os/lock.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>

#include "src/shared/pixels.h"
#include "src/slots/gfx.h"

#include "coregraphics.h"

/* A CGImage, decoded at once on the thread that decodes, so a layer's draw doesn't. New pixels are
 * a new image: uikit's layers replay lists on UIKit's thread while the Android thread swaps it, so
 * the swap and a replay's read share a lock held only for the pointer. */

struct gfx_bitmap {
	atomic_int refs;
	int width, height;
	os_unfair_lock lock;
	CGImageRef image; // NULL: blank
};

static struct gfx_bitmap *bitmap_new(int width, int height, CGImageRef image)
{
	struct gfx_bitmap *bitmap = malloc(sizeof(*bitmap));
	*bitmap = (struct gfx_bitmap){1, width, height, OS_UNFAIR_LOCK_INIT, image};
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

CGImageRef cg_bitmap_copy_image(const struct gfx_bitmap *bitmap)
{
	os_unfair_lock_lock((os_unfair_lock *)&bitmap->lock);
	CGImageRef image = bitmap->image ? CGImageRetain(bitmap->image) : NULL;
	os_unfair_lock_unlock((os_unfair_lock *)&bitmap->lock);
	return image;
}

static void swap_image(struct gfx_bitmap *bitmap, CGImageRef image)
{
	os_unfair_lock_lock(&bitmap->lock);
	CGImageRef old = bitmap->image;
	bitmap->image = image;
	os_unfair_lock_unlock(&bitmap->lock);
	if (old)
		CGImageRelease(old);
}

// premultiplied ARGB words (src/shared/pixels.h), y down; the bitmap's pixels drawn in
static CGContextRef pixels_context(const struct gfx_bitmap *bitmap)
{
	CGColorSpaceRef space = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
	CGContextRef context = CGBitmapContextCreate(NULL, bitmap->width, bitmap->height, 8, bitmap->width * 4, space,
	                                             kCGImageAlphaPremultipliedFirst | kCGBitmapByteOrder32Little);
	CGColorSpaceRelease(space);
	if (!context)
		return NULL;
	CGImageRef image = cg_bitmap_copy_image(bitmap);
	if (image) {
		CGContextSetBlendMode(context, kCGBlendModeCopy);
		CGContextDrawImage(context, CGRectMake(0, 0, bitmap->width, bitmap->height), image);
		CGContextSetBlendMode(context, kCGBlendModeNormal);
		CGImageRelease(image);
	}
	CGContextTranslateCTM(context, 0, bitmap->height);
	CGContextScaleCTM(context, 1, -1);
	return context;
}

static uint32_t *context_pixels(CGContextRef context, int x, int y)
{
	return (uint32_t *)CGBitmapContextGetData(context) + (size_t)y * (CGBitmapContextGetBytesPerRow(context) / 4) + x;
}

static void finish(struct gfx_bitmap *bitmap, CGContextRef context)
{
	swap_image(bitmap, CGBitmapContextCreateImage(context));
	CGContextRelease(context);
}

void gfx_bitmap_render(struct gfx_bitmap *bitmap, struct gfx_canvas *canvas)
{
	CGContextRef context = pixels_context(bitmap);
	if (context) {
		cg_list_draw(canvas, context, 1);
		finish(bitmap, context);
	}
	gfx_canvas_free(canvas);
}

void gfx_bitmap_erase(struct gfx_bitmap *bitmap, uint32_t argb)
{
	CGContextRef context = pixels_context(bitmap);
	if (!context)
		return;
	uint32_t pixel = argb_premultiply(argb);
	for (int y = 0; y < bitmap->height; y++) {
		uint32_t *row = context_pixels(context, 0, y);
		for (int x = 0; x < bitmap->width; x++)
			row[x] = pixel;
	}
	finish(bitmap, context);
}

void gfx_bitmap_read(const struct gfx_bitmap *bitmap, int x, int y, int width, int height, uint32_t *argb, int stride)
{
	CGContextRef context = pixels_context(bitmap);
	for (int row = 0; row < height; row++) {
		const uint32_t *pixels = context ? context_pixels(context, x, y + row) : NULL;
		for (int col = 0; col < width; col++)
			argb[(ptrdiff_t)row * stride + col] = pixels ? argb_unpremultiply(pixels[col]) : 0;
	}
	if (context)
		CGContextRelease(context);
}

void gfx_bitmap_write(struct gfx_bitmap *bitmap, int x, int y, int width, int height, const uint32_t *argb, int stride)
{
	CGContextRef context = pixels_context(bitmap);
	if (!context)
		return;
	for (int row = 0; row < height; row++) {
		uint32_t *pixels = context_pixels(context, x, y + row);
		for (int col = 0; col < width; col++)
			pixels[col] = argb_premultiply(argb[(ptrdiff_t)row * stride + col]);
	}
	finish(bitmap, context);
}

void *gfx_bitmap_encode_png(const struct gfx_bitmap *bitmap, size_t *size)
{
	CGImageRef image = cg_bitmap_copy_image(bitmap);
	if (!image) { // blank: a transparent image of the size
		CGContextRef context = pixels_context(bitmap);
		if (!context)
			return NULL;
		image = CGBitmapContextCreateImage(context);
		CGContextRelease(context);
	}
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
