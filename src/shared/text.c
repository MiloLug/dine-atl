#include <math.h>

#include "src/slots/gfx.h"

#include "text.h"

static int round_half_up(float value)
{
	return floorf(value + 0.5f);
}

void atl_font_metrics_int(const struct gfx_paint *paint, struct atl_font_metrics_int *metrics)
{
	struct gfx_font_metrics font;
	gfx_text_font_metrics(paint, &font);
	*metrics = (struct atl_font_metrics_int){
		.top = floorf(font.top),
		.ascent = round_half_up(font.ascent),
		.descent = round_half_up(font.descent),
		.bottom = ceilf(font.bottom),
		.leading = round_half_up(font.leading),
	};
}
