#ifndef SHARED_TEXT_H
#define SHARED_TEXT_H

struct gfx_paint;

/* Paint.FontMetricsInt: the gfx slot's metrics rounded as Skia does */
struct atl_font_metrics_int {
	int top, ascent, descent, bottom, leading;
};
void atl_font_metrics_int(const struct gfx_paint *paint, struct atl_font_metrics_int *metrics);

#endif
