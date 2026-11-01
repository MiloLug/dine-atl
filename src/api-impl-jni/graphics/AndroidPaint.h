#include "pango/pango-layout.h"
#include <gdk/gdk.h>
#include <gsk/gsk.h>
#include <pango/pango.h>

struct gfx_shader;

struct AndroidPaint {
	GdkRGBA color;
	GskStroke *gsk_stroke;
	PangoFontDescription *font;
	PangoAlignment alignment;
	graphene_matrix_t color_matrix;
	graphene_vec4_t color_offset;
	struct gfx_shader *shader; // held
	bool is_fill : 1;
	bool is_stroke : 1;
	bool use_color_filter : 1;
};
