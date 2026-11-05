#include "pango/pango-layout.h"
#include <gdk/gdk.h>
#include <gsk/gsk.h>
#include <pango/pango.h>

#include "src/slots/gfx.h"

struct gfx_shader;

struct AndroidPaint {
	GdkRGBA color;
	GskStroke *gsk_stroke;
	PangoFontDescription *font;
	PangoAlignment alignment;
	struct gfx_color_filter filter;
	/* the filter as a matrix over unpremultiplied colors where one is exact (blend_filter_matrix),
	 * for a source's colors before its shape; else one color takes it on the CPU and other sources
	 * composite the filter's color in its mode */
	graphene_matrix_t color_matrix;
	graphene_vec4_t color_offset;
	struct gfx_shader *shader; // held
	enum gfx_blend blend;
	bool is_fill : 1;
	bool is_stroke : 1;
	bool use_color_filter : 1;
};
