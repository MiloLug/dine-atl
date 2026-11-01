#include <math.h>
#include <pthread.h>
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

// Roboto's digits share one width, so apps' digit grids and counters line up; San Francisco's don't
static CTFontRef tabular_digits(CTFontRef font, CGFloat size)
{
	CTFontDescriptorRef plain = CTFontCopyFontDescriptor(font);
	CFNumberRef type = CFNumberCreate(NULL, kCFNumberIntType, &(int){kNumberSpacingType});
	CFNumberRef selector = CFNumberCreate(NULL, kCFNumberIntType, &(int){kMonospacedNumbersSelector});
	CTFontDescriptorRef tabular = CTFontDescriptorCreateCopyWithFeature(plain, type, selector);
	CTFontRef copy = CTFontCreateWithFontDescriptor(tabular, size, NULL);
	CFRelease(tabular);
	CFRelease(selector);
	CFRelease(type);
	CFRelease(plain);
	CFRelease(font);
	return copy;
}

static CTFontRef create_font(CGFloat size, enum gfx_font_family family, int style)
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
	if (styled) { // else regular, or a face the family lacks
		CFRelease(font);
		font = styled;
	}
	return family == GFX_FONT_SANS ? tabular_digits(font, size) : font;
}

/* Fonts by size, family and style for every thread; a miss creates its font outside the lock, so
 * UIKit's thread never waits for another thread's CoreText work. Full: it starts over. */
static struct cached_font {
	CGFloat size;
	enum gfx_font_family family;
	int style;
	CTFontRef font; // the cache's ref
} fonts[64];
static size_t font_count;
static pthread_mutex_t font_lock = PTHREAD_MUTEX_INITIALIZER;

static CTFontRef cached_font(CGFloat size, enum gfx_font_family family, int style)
{
	for (size_t i = 0; i < font_count; i++)
		if (fonts[i].size == size && fonts[i].family == family && fonts[i].style == style)
			return CFRetain(fonts[i].font);
	return NULL;
}

CTFontRef cg_font(CGFloat size, enum gfx_font_family family, int style)
{
	pthread_mutex_lock(&font_lock);
	CTFontRef font = cached_font(size, family, style);
	pthread_mutex_unlock(&font_lock);
	if (font)
		return font;
	CTFontRef created = create_font(size, family, style);
	pthread_mutex_lock(&font_lock);
	font = cached_font(size, family, style);
	if (!font) {
		if (font_count == sizeof(fonts) / sizeof(*fonts)) {
			for (size_t i = 0; i < font_count; i++)
				CFRelease(fonts[i].font);
			font_count = 0;
		}
		fonts[font_count++] = (struct cached_font){size, family, style, CFRetain(created)};
	}
	pthread_mutex_unlock(&font_lock);
	if (!font)
		return created;
	CFRelease(created); // another thread cached it first
	return font;
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
