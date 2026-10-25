#ifndef GFX_GSK_MEMORY_FORMAT_H
#define GFX_GSK_MEMORY_FORMAT_H

#include <gdk/gdk.h>

#include "src/slots/gfx.h"

// the GdkMemoryFormat a Bitmap.Config's pixels are read and written in; -1: none yet
static inline int gsk_memory_format(enum gfx_bitmap_config config)
{
	switch (config) {
	case GFX_BITMAP_ALPHA_8:
		return GDK_MEMORY_A8;
	case GFX_BITMAP_ARGB_8888:
	case GFX_BITMAP_HARDWARE:
		return GDK_MEMORY_R8G8B8A8;
	case GFX_BITMAP_RGBA_F16:
		return GDK_MEMORY_R16G16B16A16_FLOAT;
	default:
		return -1;
	}
}

#endif
