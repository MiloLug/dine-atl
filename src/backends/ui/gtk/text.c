#include <gtk/gtk.h>

#include "src/api-impl-jni/gtk_util.h"
#include "src/api-impl-jni/widgets/WrapperWidget.h"
#include "src/slots/ui.h"

#include "gtk_ui.h"

/* A TextView is a GtkLabel in a GtkBox that also holds the compound drawables; a button's handle holds
 * such a box. Handles without a label (an EditText's GtkText, any view for the alignment) skip the
 * calls that need one. */

GtkLabel *text_get_label(GtkWidget *handle)
{
	return g_object_get_data(G_OBJECT(handle), "atl-label");
}

static void apply_attributes(GtkLabel *label);

GtkWidget *text_box_new(GtkWidget *handle, const char *class_name, const char *text)
{
	GtkWidget *box = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 0);
	GtkWidget *label = gtk_label_new(text);
	gtk_label_set_wrap(GTK_LABEL(label), TRUE);
	gtk_label_set_xalign(GTK_LABEL(label), 0.f);
	gtk_label_set_yalign(GTK_LABEL(label), 0.f);
	gtk_widget_set_hexpand(label, TRUE);
	gtk_box_append(GTK_BOX(box), label);
	g_object_set_data(G_OBJECT(handle ?: box), "atl-label", label);
	g_object_set_data_full(G_OBJECT(handle ?: box), "atl-class-name", g_strdup(class_name), g_free);

	apply_attributes(GTK_LABEL(label));
	return box;
}

struct ui_view *ui_text_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits, const char *text)
{
	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *box = text_box_new(NULL, class_name, text);
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), box);
	wrapper_widget_setup(WRAPPER_WIDGET(wrapper), view_ref, traits);
	return (struct ui_view *)box;
}

static void apply_min_lines(GtkLabel *label);

// a UTF-16 index of text as a byte index
static int byte_index(const char *text, int utf16)
{
	const char *at = text;
	for (int units = 0; *at && units < utf16; at = g_utf8_next_char(at))
		units += g_utf8_get_char(at) > 0xFFFF ? 2 : 1;
	return at - text;
}

static void add(PangoAttrList *attrs, PangoAttribute *attr, int start, int end)
{
	attr->start_index = start;
	attr->end_index = end;
	pango_attr_list_insert(attrs, attr);
}

// a run with its range in the bytes of the text it came with
struct byte_run {
	int start, end;
	struct ui_text_run run;
};

// tabular digits and the size ui_text_set_size set over the whole text, then the runs (scale x isn't drawn)
static void apply_attributes(GtkLabel *label)
{
	PangoAttrList *attrs = pango_attr_list_new();
	pango_attr_list_insert(attrs, pango_attr_font_features_new("tnum"));
	float *size = g_object_get_data(G_OBJECT(label), "atl-text-size");
	if (size)
		pango_attr_list_insert(attrs, pango_attr_size_new(*size * PANGO_SCALE));
	GArray *runs = g_object_get_data(G_OBJECT(label), "atl-text-runs");
	for (guint i = 0; runs && i < runs->len; i++) {
		const struct byte_run *byte_run = &g_array_index(runs, struct byte_run, i);
		const struct ui_text_run *run = &byte_run->run;
		int start = byte_run->start, end = byte_run->end;
		if (run->set & UI_RUN_COLOR) {
			add(attrs, pango_attr_foreground_new(((run->color >> 16) & 0xff) * 257, ((run->color >> 8) & 0xff) * 257, (run->color & 0xff) * 257), start, end);
			add(attrs, pango_attr_foreground_alpha_new((run->color >> 24) * 257), start, end);
		}
		if (run->set & UI_RUN_BACKGROUND) {
			add(attrs, pango_attr_background_new(((run->background >> 16) & 0xff) * 257, ((run->background >> 8) & 0xff) * 257, (run->background & 0xff) * 257), start, end);
			add(attrs, pango_attr_background_alpha_new((run->background >> 24) * 257), start, end);
		}
		if (run->set & UI_RUN_SIZE)
			add(attrs, pango_attr_size_new(run->size * PANGO_SCALE), start, end);
		if (run->set & UI_RUN_STYLE) {
			add(attrs, pango_attr_weight_new(run->style & UI_TEXT_BOLD ? PANGO_WEIGHT_BOLD : PANGO_WEIGHT_NORMAL), start, end);
			add(attrs, pango_attr_style_new(run->style & UI_TEXT_ITALIC ? PANGO_STYLE_ITALIC : PANGO_STYLE_NORMAL), start, end);
		}
		if (run->set & UI_RUN_FAMILY)
			add(attrs, pango_attr_family_new(run->family == 1 ? "serif" : run->family == 2 ? "monospace" : "sans"), start, end);
		if (run->set & UI_RUN_UNDERLINE)
			add(attrs, pango_attr_underline_new(PANGO_UNDERLINE_SINGLE), start, end);
		if (run->set & UI_RUN_STRIKE)
			add(attrs, pango_attr_strikethrough_new(TRUE), start, end);
		if (run->set & UI_RUN_BASELINE)
			add(attrs, pango_attr_rise_new(-run->baseline_shift * PANGO_SCALE), start, end);
	}
	gtk_label_set_attributes(label, attrs);
	pango_attr_list_unref(attrs);
	apply_min_lines(label);
}

extern int snapshot_in_progress;

// the latest text and its runs at once
static void set_styled(GtkLabel *label)
{
	gtk_label_set_text(label, g_object_get_data(G_OBJECT(label), "atl-text"));
	apply_attributes(label);
}

static gboolean set_styled_later(GtkWidget *label, GdkFrameClock *clock, gpointer data)
{
	set_styled(GTK_LABEL(label));
	return G_SOURCE_REMOVE;
}

void ui_text_set_styled(struct ui_view *view, const char *text, const struct ui_text_run *runs, size_t n_runs)
{
	GtkLabel *label = text_get_label(GTK_WIDGET(view));
	if (!label)
		return;
	GArray *copy = NULL;
	if (n_runs) {
		copy = g_array_sized_new(FALSE, FALSE, sizeof(struct byte_run), n_runs);
		for (size_t i = 0; i < n_runs; i++) {
			struct byte_run byte_run = {byte_index(text, runs[i].start), byte_index(text, runs[i].end), runs[i]};
			g_array_append_val(copy, byte_run);
		}
	}
	g_object_set_data_full(G_OBJECT(label), "atl-text-runs", copy, copy ? (GDestroyNotify)g_array_unref : NULL);
	g_object_set_data_full(G_OBJECT(label), "atl-text", g_strdup(text), g_free);
	if (snapshot_in_progress) // a change now drops the widget from this snapshot: the next frame's
		gtk_widget_add_tick_callback(GTK_WIDGET(label), set_styled_later, NULL, NULL);
	else
		set_styled(label);
}

/* we kinda need per-widget css */
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
void ui_text_set_color(struct ui_view *view, uint32_t color)
{
	GtkWidget *widget = GTK_WIDGET(view);

	GtkStyleContext *style_context = gtk_widget_get_style_context(widget);

	GtkCssProvider *old_provider = g_object_get_data(G_OBJECT(widget), "color_style_provider");
	if (old_provider)
		gtk_style_context_remove_provider(style_context, GTK_STYLE_PROVIDER(old_provider));

	GtkCssProvider *css_provider = gtk_css_provider_new();

	char *css_string = g_markup_printf_escaped("* { color: #%06x%02x; }", color & 0xFFFFFF, (color >> 24) & 0xFF);
	gtk_css_provider_load_from_string(css_provider, css_string);
	g_free(css_string);

	gtk_style_context_add_provider(style_context, GTK_STYLE_PROVIDER(css_provider), GTK_STYLE_PROVIDER_PRIORITY_APPLICATION);
	g_object_set_data_full(G_OBJECT(widget), "color_style_provider", css_provider, g_object_unref);
}
#pragma GCC diagnostic pop

// one layout of n lines: rounded once, as GtkLabel rounds its text's
static int lines_height(GtkLabel *label, int n)
{
	GString *text = g_string_new("X");
	for (int i = 1; i < n; i++)
		g_string_append(text, "\nX");
	PangoLayout *layout = gtk_widget_create_pango_layout(GTK_WIDGET(label), text->str);
	g_string_free(text, TRUE);
	pango_layout_set_attributes(layout, gtk_label_get_attributes(label));
	int height;
	pango_layout_get_pixel_size(layout, NULL, &height);
	g_object_unref(layout);
	return height;
}

// min lines as the label's least height
static void apply_min_lines(GtkLabel *label)
{
	int min = GPOINTER_TO_INT(g_object_get_data(G_OBJECT(label), "atl-min-lines"));
	gtk_widget_set_size_request(GTK_WIDGET(label), -1, min > 0 ? lines_height(label, min) : -1);
}

/* GtkLabel's own line limit counts per paragraph: the measured height drops the lines past max,
 * which the box clips. The label's width is taken as the whole for_width. */
int text_cap_height(GtkWidget *handle, int for_width, int height)
{
	GtkLabel *label = text_get_label(handle);
	int max = label ? GPOINTER_TO_INT(g_object_get_data(G_OBJECT(label), "atl-max-lines")) - 1 : -1;
	if (max < 0)
		return height;
	PangoLayout *layout = pango_layout_copy(gtk_label_get_layout(label));
	pango_layout_set_width(layout, for_width >= 0 ? for_width * PANGO_SCALE : -1);
	if (pango_layout_get_line_count(layout) > max) {
		PangoLayoutIter *iter = pango_layout_get_iter(layout);
		for (int i = 0; i < max; i++)
			pango_layout_iter_next_line(iter);
		int cut, end;
		pango_layout_iter_get_line_yrange(iter, &cut, &end);
		pango_layout_iter_free(iter);
		PangoRectangle logical;
		pango_layout_get_extents(layout, NULL, &logical);
		// rounded up, as GtkLabel rounds its layout's size
		height -= PANGO_PIXELS_CEIL(logical.height) - PANGO_PIXELS_CEIL(cut);
	}
	g_object_unref(layout);
	return height;
}

// the size set, else the label's font's; points, as ui_text_set_size's size attribute takes it
float ui_text_get_size(struct ui_view *view)
{
	GtkLabel *label = text_get_label(GTK_WIDGET(view));
	if (!label)
		return 0;
	float *size = g_object_get_data(G_OBJECT(label), "atl-text-size");
	if (size)
		return *size;
	const PangoFontDescription *font = pango_context_get_font_description(gtk_widget_get_pango_context(GTK_WIDGET(label)));
	return (float)pango_font_description_get_size(font) / PANGO_SCALE;
}

void ui_text_set_lines(struct ui_view *view, int min, int max)
{
	GtkLabel *label = text_get_label(GTK_WIDGET(view));
	if (!label)
		return;
	g_object_set_data(G_OBJECT(label), "atl-max-lines", GINT_TO_POINTER(max + 1)); // 0: unset
	gtk_widget_set_overflow(GTK_WIDGET(view), max >= 0 ? GTK_OVERFLOW_HIDDEN : GTK_OVERFLOW_VISIBLE);
	g_object_set_data(G_OBJECT(label), "atl-min-lines", GINT_TO_POINTER(min));
	apply_min_lines(label);
}

void ui_text_set_size(struct ui_view *view, float size)
{
	GtkLabel *label = text_get_label(GTK_WIDGET(view));
	if (!label)
		return;
	g_object_set_data_full(G_OBJECT(label), "atl-text-size", g_memdup2(&size, sizeof(size)), g_free);
	apply_attributes(label);
}

void ui_text_set_style(struct ui_view *view, int style)
{
	GtkWidget *widget = GTK_WIDGET(view);
	gtk_widget_remove_css_class(widget, "ATL-font-bold");
	gtk_widget_remove_css_class(widget, "ATL-font-italic");
	if (style & UI_TEXT_BOLD)
		gtk_widget_add_css_class(widget, "ATL-font-bold");
	if (style & UI_TEXT_ITALIC)
		gtk_widget_add_css_class(widget, "ATL-font-italic");
	GtkLabel *label = text_get_label(widget);
	if (label)
		apply_min_lines(label);
}

void ui_text_set_alignment(struct ui_view *view, int alignment)
{
	GtkLabel *label = text_get_label(GTK_WIDGET(view));
	if (!label)
		return;
	static const float xalign[] = {[UI_TEXT_ALIGN_START] = 0.f, [UI_TEXT_ALIGN_CENTER] = .5f, [UI_TEXT_ALIGN_END] = 1.f};
	static const GtkJustification justify[] = {[UI_TEXT_ALIGN_START] = GTK_JUSTIFY_LEFT, [UI_TEXT_ALIGN_CENTER] = GTK_JUSTIFY_CENTER,
	                                           [UI_TEXT_ALIGN_END] = GTK_JUSTIFY_RIGHT};
	gtk_label_set_xalign(label, xalign[alignment]);
	gtk_label_set_justify(label, justify[alignment]);
}

// the label keeps its own presses for the selection and its menu: Java's clicks on it stop
void ui_text_set_selectable(struct ui_view *view, bool selectable)
{
	GtkLabel *label = text_get_label(GTK_WIDGET(view));
	if (!label)
		return;
	gtk_label_set_selectable(label, selectable);
	if (selectable)
		input_take_native(GTK_WIDGET(label));
	else
		input_return_native(GTK_WIDGET(label));
}

// pictures before and after the label in its box
void ui_text_set_drawables(struct ui_view *view, struct gfx_drawable *left, struct gfx_drawable *top, struct gfx_drawable *right, struct gfx_drawable *bottom)
{
	GtkLabel *label = text_get_label(GTK_WIDGET(view));
	if (!label)
		return;
	GtkWidget *box = gtk_widget_get_parent(GTK_WIDGET(label));
	gtk_orientable_set_orientation(GTK_ORIENTABLE(box), (left || right) ? GTK_ORIENTATION_HORIZONTAL : GTK_ORIENTATION_VERTICAL);

	GdkPaintable *paintable = (GdkPaintable *)(left ?: top);
	GtkWidget *picture = gtk_widget_get_first_child(box);
	if (GTK_IS_PICTURE(picture)) {
		gtk_picture_set_paintable(GTK_PICTURE(picture), paintable);
	} else if (paintable) {
		picture = gtk_picture_new_for_paintable(paintable);
		gtk_widget_insert_after(picture, box, NULL);
	}

	paintable = (GdkPaintable *)(right ?: bottom);
	picture = gtk_widget_get_last_child(box);
	if (GTK_IS_PICTURE(picture)) {
		gtk_picture_set_paintable(GTK_PICTURE(picture), paintable);
	} else if (paintable) {
		picture = gtk_picture_new_for_paintable(paintable);
		gtk_widget_insert_before(picture, box, NULL);
	}
}
