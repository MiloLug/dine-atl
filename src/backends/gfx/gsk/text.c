#include <limits.h>

#include <gtk/gtk.h>
#include <pango/pangocairo.h>

#include "src/api-impl-jni/graphics/AndroidPaint.h"
#include "src/slots/gfx.h"

#include "text.h"

/* GTK's text settings as a label gets them: GTK's thread copies them, and every thread's own context
 * takes the copy, so a Paint measures as the labels draw on any thread. Pango objects stay on their
 * thread: each gets its own context from its own default font map (per thread since Pango 1.32.6). */
static GMutex gtk_text_lock;
static PangoFontDescription *gtk_font;
static cairo_font_options_t *gtk_font_options;
static gboolean gtk_round_glyphs;
static int gtk_text_version; // atomic; 0: no copy yet

static void take_gtk_text(void)
{
	GtkWidget *label = g_object_ref_sink(gtk_label_new(NULL));
	PangoContext *gtk_context = gtk_widget_get_pango_context(label);
	const cairo_font_options_t *options = pango_cairo_context_get_font_options(gtk_context);
	g_mutex_lock(&gtk_text_lock);
	if (gtk_font)
		pango_font_description_free(gtk_font);
	gtk_font = pango_font_description_copy(pango_context_get_font_description(gtk_context));
	if (gtk_font_options)
		cairo_font_options_destroy(gtk_font_options);
	gtk_font_options = options ? cairo_font_options_copy(options) : NULL;
	gtk_round_glyphs = pango_context_get_round_glyph_positions(gtk_context);
	g_atomic_int_inc(&gtk_text_version);
	g_mutex_unlock(&gtk_text_lock);
	g_object_unref(label);
}

static void gtk_settings_changed(GtkSettings *settings, GParamSpec *pspec, gpointer data)
{
	take_gtk_text();
}

void gsk_text_follow_gtk(void)
{
	take_gtk_text();
	g_signal_connect(gtk_settings_get_default(), "notify", G_CALLBACK(gtk_settings_changed), NULL);
}

struct thread_context {
	PangoContext *context;
	int version; // of the copy it took
};

static void context_free(gpointer data)
{
	struct thread_context *thread = data;
	g_object_unref(thread->context);
	g_free(thread);
}

static GPrivate thread_context = G_PRIVATE_INIT(context_free);

static PangoContext *context(void)
{
	struct thread_context *thread = g_private_get(&thread_context);
	if (!thread) {
		thread = g_new0(struct thread_context, 1);
		thread->context = pango_font_map_create_context(pango_cairo_font_map_get_default());
		g_private_set(&thread_context, thread);
	}
	if (thread->version != g_atomic_int_get(&gtk_text_version)) {
		g_mutex_lock(&gtk_text_lock);
		pango_context_set_font_description(thread->context, gtk_font);
		pango_cairo_context_set_font_options(thread->context, gtk_font_options);
		pango_context_set_round_glyph_positions(thread->context, gtk_round_glyphs);
		thread->version = g_atomic_int_get(&gtk_text_version);
		g_mutex_unlock(&gtk_text_lock);
	}
	return thread->context;
}

// lone surrogates become U+FFFD; byte_of[i] is unit i's first byte, a pair's second unit gets the
// pair's (byte_of[len]: the length)
static char *to_utf8(const uint16_t *text, size_t len, int *byte_of)
{
	char *utf8 = g_malloc(len * 3 + 1), *out = utf8;
	for (size_t i = 0; i < len; i++) {
		byte_of[i] = out - utf8;
		gunichar c = text[i];
		if (c >= 0xD800 && c < 0xDC00 && i + 1 < len && text[i + 1] >= 0xDC00 && text[i + 1] < 0xE000) {
			c = 0x10000 + ((c - 0xD800) << 10) + (text[i + 1] - 0xDC00);
			byte_of[++i] = out - utf8;
		} else if (c >= 0xD800 && c < 0xE000) {
			c = 0xFFFD;
		}
		out += g_unichar_to_utf8(c, out);
	}
	byte_of[len] = out - utf8;
	*out = '\0';
	return utf8;
}

PangoLayout *gsk_text_layout(const struct gfx_paint *gfx_paint, const uint16_t *text, size_t len, int *byte_of)
{
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	PangoLayout *layout = pango_layout_new(context());
	pango_layout_set_font_description(layout, paint->font);
	// tabular digits, as the labels have (ui/gtk/text.c): the feature changes other glyphs' widths too
	PangoAttrList *attrs = pango_attr_list_new();
	pango_attr_list_insert(attrs, pango_attr_font_features_new("tnum"));
	pango_layout_set_attributes(layout, attrs);
	pango_attr_list_unref(attrs);
	int *bytes = byte_of ? byte_of : g_new(int, len + 1);
	char *utf8 = to_utf8(text, len, bytes);
	pango_layout_set_text(layout, utf8, -1);
	g_free(utf8);
	if (!byte_of)
		g_free(bytes);
	return layout;
}

void gfx_text_font_metrics(const struct gfx_paint *gfx_paint, struct gfx_font_metrics *metrics)
{
	const struct AndroidPaint *paint = (const struct AndroidPaint *)gfx_paint;
	PangoContext *pango_context = context();
	// as a layout does: the paint's fields over the context's font
	PangoFontDescription *font = pango_font_description_copy(pango_context_get_font_description(pango_context));
	pango_font_description_merge(font, paint->font, TRUE);
	PangoFontMetrics *font_metrics = pango_context_get_metrics(pango_context, font, NULL);
	float ascent = (float)pango_font_metrics_get_ascent(font_metrics) / PANGO_SCALE;
	float descent = (float)pango_font_metrics_get_descent(font_metrics) / PANGO_SCALE;
	float height = (float)pango_font_metrics_get_height(font_metrics) / PANGO_SCALE;
	*metrics = (struct gfx_font_metrics){
		.top = -ascent,
		.ascent = -ascent,
		.descent = descent,
		.bottom = descent,
		.leading = height - ascent - descent,
	};
	pango_font_metrics_unref(font_metrics);
	pango_font_description_free(font);
}

float gfx_text_measure(const struct gfx_paint *paint, const uint16_t *text, size_t len)
{
	PangoLayout *layout = gsk_text_layout(paint, text, len, NULL);
	PangoRectangle logical;
	pango_layout_get_extents(layout, NULL, &logical);
	g_object_unref(layout);
	return (float)logical.width / PANGO_SCALE;
}

void gfx_text_carets(const struct gfx_paint *paint, const uint16_t *text, size_t len, float *x)
{
	int *byte_of = g_new(int, len + 1);
	PangoLayout *layout = gsk_text_layout(paint, text, len, byte_of);
	// clusters in logical order, as left-to-right text lays them out
	PangoLayoutIter *iter = pango_layout_get_iter(layout);
	float cluster_x = 0;
	size_t i = 0;
	do {
		int index = pango_layout_iter_get_index(iter);
		PangoRectangle logical;
		pango_layout_iter_get_cluster_extents(iter, NULL, &logical);
		for (; i < len && byte_of[i] < index; i++)
			x[i] = cluster_x;
		cluster_x = (float)logical.x / PANGO_SCALE;
	} while (pango_layout_iter_next_cluster(iter));
	pango_layout_iter_free(iter);
	for (; i < len; i++)
		x[i] = cluster_x;
	PangoRectangle logical;
	pango_layout_get_extents(layout, NULL, &logical);
	x[len] = (float)logical.width / PANGO_SCALE;
	g_object_unref(layout);
	g_free(byte_of);
}

void gfx_text_bounds(const struct gfx_paint *paint, const uint16_t *text, size_t len, struct gfx_rect *bounds)
{
	if (!len) {
		*bounds = (struct gfx_rect){0};
		return;
	}
	PangoLayout *layout = gsk_text_layout(paint, text, len, NULL);
	PangoRectangle ink;
	pango_layout_get_extents(layout, &ink, NULL);
	int baseline = pango_layout_get_baseline(layout);
	*bounds = (struct gfx_rect){
		(float)ink.x / PANGO_SCALE,
		(float)(ink.y - baseline) / PANGO_SCALE,
		(float)(ink.x + ink.width) / PANGO_SCALE,
		(float)(ink.y + ink.height - baseline) / PANGO_SCALE,
	};
	g_object_unref(layout);
}

size_t gfx_text_break_lines(const struct gfx_paint *paint, const uint16_t *text, size_t len, float width, size_t **ends)
{
	int *byte_of = g_new(int, len + 1);
	PangoLayout *layout = gsk_text_layout(paint, text, len, byte_of);
	if (width >= 0 && width * PANGO_SCALE < INT_MAX) { // Compose's unbounded width is INT_MAX px
		pango_layout_set_width(layout, width * PANGO_SCALE);
		pango_layout_set_wrap(layout, PANGO_WRAP_WORD_CHAR);
	}
	size_t n = pango_layout_get_line_count(layout);
	*ends = malloc(n * sizeof(**ends));
	size_t unit = 0, line = 0;
	for (GSList *l = pango_layout_get_lines_readonly(layout); l; l = l->next, line++) {
		const PangoLayoutLine *pango_line = l->data;
		int end = pango_line->start_index + pango_line->length;
		while (unit < len && byte_of[unit] < end)
			unit++;
		(*ends)[line] = unit;
	}
	(*ends)[n - 1] = len;
	g_object_unref(layout);
	g_free(byte_of);
	return n;
}
