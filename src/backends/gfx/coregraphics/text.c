#include <math.h>
#include <stdbool.h>
#include <stdlib.h>

#include "src/shared/upcall.h"
#include "src/slots/gfx.h"

#include "../record/record.h"
#include "coregraphics.h"

/* CoreText on the calling thread. Text is laid out at the paint's size in points, in the UILabels'
 * font (cg_font), and measured back in px, so Paint and TextViews agree. */

static CGFloat scale(void)
{
	return atl_window_get()->density_dpi / 160.;
}

CTFontRef cg_font(CGFloat size, enum gfx_font_family family, int style)
{
	CTFontRef font;
	if (family == GFX_FONT_MONOSPACE)
		font = CTFontCreateUIFontForLanguage(kCTFontUIFontUserFixedPitch, size, NULL);
	else if (family == GFX_FONT_SERIF)
		font = CTFontCreateWithName(CFSTR("TimesNewRomanPSMT"), size, NULL);
	else
		font = CTFontCreateUIFontForLanguage(kCTFontUIFontSystem, size, NULL);
	CTFontSymbolicTraits traits = (style & GFX_FONT_BOLD ? kCTFontTraitBold : 0) | (style & GFX_FONT_ITALIC ? kCTFontTraitItalic : 0);
	CTFontRef styled = traits ? CTFontCreateCopyWithSymbolicTraits(font, size, NULL, traits, traits) : NULL;
	if (!styled) // regular, or a face the family lacks
		return font;
	CFRelease(font);
	return styled;
}

CTLineRef cg_text_line(const struct gfx_paint *paint, CGFloat scale, const uint16_t *text, size_t len)
{
	CTFontRef font = cg_font(paint->text_size / scale, paint->font_family, paint->font_style);
	CFStringRef string = CFStringCreateWithCharacters(NULL, text, len);
	const void *keys[] = {kCTFontAttributeName, kCTForegroundColorFromContextAttributeName};
	const void *values[] = {font, kCFBooleanTrue};
	CFDictionaryRef attributes = CFDictionaryCreate(NULL, keys, values, 2, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
	CFAttributedStringRef attributed = CFAttributedStringCreate(NULL, string, attributes);
	CTLineRef line = CTLineCreateWithAttributedString(attributed);
	CFRelease(attributed);
	CFRelease(attributes);
	CFRelease(string);
	CFRelease(font);
	return line;
}

// CoreText takes size 0 as its default size; Android draws nothing
static bool no_text(const struct gfx_paint *paint)
{
	return paint->text_size <= 0;
}

void gfx_text_font_metrics(const struct gfx_paint *paint, struct gfx_font_metrics *metrics)
{
	if (no_text(paint)) {
		*metrics = (struct gfx_font_metrics){0};
		return;
	}
	CGFloat s = scale();
	CTFontRef font = cg_font(paint->text_size / s, paint->font_family, paint->font_style);
	float ascent = CTFontGetAscent(font) * s, descent = CTFontGetDescent(font) * s;
	*metrics = (struct gfx_font_metrics){
		.top = -ascent,
		.ascent = -ascent,
		.descent = descent,
		.bottom = descent,
		.leading = CTFontGetLeading(font) * s,
	};
	CFRelease(font);
}

float gfx_text_measure(const struct gfx_paint *paint, const uint16_t *text, size_t len)
{
	if (no_text(paint))
		return 0;
	CGFloat s = scale();
	CTLineRef line = cg_text_line(paint, s, text, len);
	float width = CTLineGetTypographicBounds(line, NULL, NULL, NULL) * s;
	CFRelease(line);
	return width;
}

void gfx_text_carets(const struct gfx_paint *paint, const uint16_t *text, size_t len, float *x)
{
	if (no_text(paint)) {
		for (size_t i = 0; i <= len; i++)
			x[i] = 0;
		return;
	}
	CGFloat s = scale();
	CTLineRef line = cg_text_line(paint, s, text, len);
	for (size_t i = 0; i < len; i++) {
		bool in_pair = i && text[i] >= 0xDC00 && text[i] < 0xE000 && text[i - 1] >= 0xD800 && text[i - 1] < 0xDC00;
		x[i] = in_pair ? x[i - 1] : CTLineGetOffsetForStringIndex(line, i, NULL) * s;
	}
	x[len] = CTLineGetTypographicBounds(line, NULL, NULL, NULL) * s;
	CFRelease(line);
}

// CoreText's y is up
void gfx_text_bounds(const struct gfx_paint *paint, const uint16_t *text, size_t len, struct gfx_rect *bounds)
{
	if (!len || no_text(paint)) {
		*bounds = (struct gfx_rect){0};
		return;
	}
	CGFloat s = scale();
	CTLineRef line = cg_text_line(paint, s, text, len);
	CGRect ink = CTLineGetBoundsWithOptions(line, kCTLineBoundsUseGlyphPathBounds);
	CFRelease(line);
	if (CGRectIsNull(ink)) { // no glyph has ink: spaces
		*bounds = (struct gfx_rect){0};
		return;
	}
	*bounds = (struct gfx_rect){CGRectGetMinX(ink) * s, -CGRectGetMaxY(ink) * s, CGRectGetMaxX(ink) * s, -CGRectGetMinY(ink) * s};
}

size_t gfx_text_break_lines(const struct gfx_paint *paint, const uint16_t *text, size_t len, float width, size_t **ends)
{
	size_t n = 0, size = 4;
	*ends = malloc(size * sizeof(**ends));
	if (width < 0 || !len || no_text(paint)) {
		(*ends)[n++] = len;
		return n;
	}
	CGFloat s = scale();
	CTFontRef font = cg_font(paint->text_size / s, paint->font_family, paint->font_style);
	CFStringRef string = CFStringCreateWithCharacters(NULL, text, len);
	const void *keys[] = {kCTFontAttributeName};
	const void *values[] = {font};
	CFDictionaryRef attributes = CFDictionaryCreate(NULL, keys, values, 1, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
	CFAttributedStringRef attributed = CFAttributedStringCreate(NULL, string, attributes);
	CTTypesetterRef typesetter = CTTypesetterCreateWithAttributedString(attributed);
	for (size_t start = 0; start < len;) {
		size_t count = CTTypesetterSuggestLineBreak(typesetter, start, width / s);
		if (!count) // nothing fits: one code point
			count = 1 + (start + 1 < len && text[start + 1] >= 0xDC00 && text[start + 1] < 0xE000);
		if (n == size)
			*ends = realloc(*ends, (size *= 2) * sizeof(**ends));
		(*ends)[n++] = start += count;
	}
	CFRelease(typesetter);
	CFRelease(attributed);
	CFRelease(attributes);
	CFRelease(string);
	CFRelease(font);
	return n;
}
