#include <math.h>
#include <stdbool.h>
#include <stdlib.h>

#include "src/slots/gfx.h"

#include "record.h"

/* No font: fixed metrics keep dumps exact. Every unit advances the same, whatever the typeface. */

#define ADVANCE_PER_SIZE 0.6f
#define ASCENT_PER_SIZE  0.95f
#define DESCENT_PER_SIZE 0.25f // a line of 1.2 sizes

static bool is_low_surrogate(uint16_t unit)
{
	return unit >= 0xDC00 && unit < 0xE000;
}

void gfx_text_font_metrics(const struct gfx_paint *paint, struct gfx_font_metrics *metrics)
{
	float ascent = -ASCENT_PER_SIZE * paint->text_size, descent = DESCENT_PER_SIZE * paint->text_size;
	*metrics = (struct gfx_font_metrics){.top = ascent, .ascent = ascent, .descent = descent, .bottom = descent};
}

// count × advance, never a sum: a sum's rounding would move ceil()ed sizes
float gfx_text_measure(const struct gfx_paint *paint, const uint16_t *text, size_t len)
{
	return len * (ADVANCE_PER_SIZE * paint->text_size);
}

void gfx_text_carets(const struct gfx_paint *paint, const uint16_t *text, size_t len, float *x)
{
	float advance = ADVANCE_PER_SIZE * paint->text_size;
	for (size_t i = 0; i <= len; i++)
		x[i] = (i < len && i && is_low_surrogate(text[i]) ? i - 1 : i) * advance;
}

void gfx_text_bounds(const struct gfx_paint *paint, const uint16_t *text, size_t len, struct gfx_rect *bounds)
{
	struct gfx_font_metrics metrics;
	gfx_text_font_metrics(paint, &metrics);
	*bounds = len ? (struct gfx_rect){0, metrics.ascent, gfx_text_measure(paint, text, len), metrics.descent} : (struct gfx_rect){0};
}

// greedy: a line ends after its last space, spaces past the width hang on it, a word wider than
// the line breaks between code points
size_t gfx_text_break_lines(const struct gfx_paint *paint, const uint16_t *text, size_t len, float width, size_t **ends)
{
	float advance = ADVANCE_PER_SIZE * paint->text_size;
	size_t fit = width >= 0 && advance > 0 ? floorf(width / advance) : len;
	size_t n = 0, size = 4;
	*ends = malloc(size * sizeof(**ends));
	size_t start = 0;
	do {
		size_t end = len - start <= fit ? len : start + fit;
		if (end < len) {
			if (text[end] == ' ') {
				while (end < len && text[end] == ' ')
					end++;
			} else {
				size_t space = end;
				while (space > start && text[space - 1] != ' ')
					space--;
				if (space > start)
					end = space;
				else if (end > start && is_low_surrogate(text[end]))
					end--;
			}
		}
		if (end == start && start < len)
			end = start + 1 + (start + 1 < len && is_low_surrogate(text[start + 1]));
		if (n == size)
			*ends = realloc(*ends, (size *= 2) * sizeof(**ends));
		(*ends)[n++] = end;
		start = end;
	} while (start < len);
	return n;
}
