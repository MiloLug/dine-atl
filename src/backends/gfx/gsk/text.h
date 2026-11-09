#ifndef GFX_GSK_TEXT_H
#define GFX_GSK_TEXT_H

#include <stdint.h>

#include <pango/pango.h>

struct gfx_paint;

/* On GTK's thread before any text is measured: Paints take GTK's text settings from then on, and
 * again after each GtkSettings change. */
void gsk_text_follow_gtk(void);

/* The text in the paint's font; byte_of (len + 1 entries, or NULL) gets each unit's UTF-8 offset. */
PangoLayout *gsk_text_layout(const struct gfx_paint *paint, const uint16_t *text, size_t len, int *byte_of);

#endif
