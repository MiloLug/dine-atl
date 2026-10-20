#include <gtk/gtk.h>

#include "src/api-impl-jni/gtk_util.h"
#include "src/api-impl-jni/widgets/WrapperWidget.h"
#include "src/slots/ui.h"

#include "gtk_ui.h"

/* A TextView is a GtkLabel in a GtkBox that also holds the compound drawables. */

GtkLabel *text_box_get_label(GtkWidget *box)
{
	GtkWidget *label = gtk_widget_get_last_child(GTK_WIDGET(box));
	if (!GTK_IS_LABEL(label))
		label = gtk_widget_get_prev_sibling(label);
	return GTK_LABEL(label);
}

struct ui_view *ui_text_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits, const char *text)
{
	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *box = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 0);
	GtkWidget *label = gtk_label_new(text);
	gtk_label_set_wrap(GTK_LABEL(label), TRUE);
	gtk_label_set_xalign(GTK_LABEL(label), 0.f);
	gtk_label_set_yalign(GTK_LABEL(label), 0.f);
	gtk_widget_set_hexpand(label, TRUE);
	gtk_box_append(GTK_BOX(box), label);
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), box);
	wrapper_widget_setup(WRAPPER_WIDGET(wrapper), view_ref, traits);
	g_object_set_data_full(G_OBJECT(box), "atl-class-name", g_strdup(class_name), g_free);

	PangoAttrList *pango_attrs = pango_attr_list_new();
	pango_attr_list_insert(pango_attrs, pango_attr_font_features_new("tnum"));
	gtk_label_set_attributes(GTK_LABEL(label), pango_attrs);
	pango_attr_list_unref(pango_attrs);

	return (struct ui_view *)box;
}

void ui_text_set_text(struct ui_view *view, const char *text)
{
	atl_safe_gtk_label_set_text(text_box_get_label(GTK_WIDGET(view)), text);
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
	g_object_set_data(G_OBJECT(widget), "color_style_provider", css_provider);
}
#pragma GCC diagnostic pop

void ui_text_set_size(struct ui_view *view, float size)
{
	GtkLabel *label = text_box_get_label(GTK_WIDGET(view));
	PangoAttrList *attrs;

	PangoAttrList *old_attrs = gtk_label_get_attributes(label);
	if (old_attrs)
		attrs = pango_attr_list_copy(old_attrs);
	else
		attrs = pango_attr_list_new();

	PangoAttribute *size_attr = pango_attr_size_new(size * PANGO_SCALE);
	pango_attr_list_change(attrs, size_attr);
	gtk_label_set_attributes(label, attrs);

	pango_attr_list_unref(attrs);
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
}
