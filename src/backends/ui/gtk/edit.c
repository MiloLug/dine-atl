#include <gtk/gtk.h>
#include <string.h>

#include "src/api-impl-jni/widgets/WrapperWidget.h"
#include "src/shared/input.h"
#include "src/slots/ui.h"

#include "gtk_ui.h"

/* An EditText is a GtkText, single-line. Changes report from an idle: typing over a selection (a
 * delete, then an insert) is one report. Preedit text stays out of the buffer and doesn't report. */

struct edit {
	GtkText *text;
	char *prev; // the state last reported or taken from Java; positions UTF-16
	int prev_start, prev_end;
	uint32_t seq;
	guint report;   // the pending report's idle, or 0
	bool applying;  // a snapshot's own changes don't report
	bool preedit;   // Java's snapshots wait: the composition's end reports, changed or not
	bool ended;
};

static struct edit *edit_of(struct ui_view *view)
{
	return g_object_get_data(G_OBJECT(view), "atl-edit");
}

static struct atl_ref *edit_ref(struct edit *edit)
{
	return (struct atl_ref *)WRAPPER_WIDGET(gtk_widget_get_parent(GTK_WIDGET(edit->text)))->jobj;
}

static int utf16_of_chars(const char *text, int chars)
{
	return atl_utf16_length(text, g_utf8_offset_to_pointer(text, chars) - text);
}

static int chars_of_utf16(const char *text, int utf16)
{
	return g_utf8_pointer_to_offset(text, text + atl_utf8_offset(text, utf16));
}

// the anchor, then the cursor
static void get_selection(struct edit *edit, const char *text, int *start, int *end)
{
	int anchor, cursor;
	g_object_get(edit->text, "selection-bound", &anchor, "cursor-position", &cursor, NULL);
	*start = utf16_of_chars(text, anchor);
	*end = utf16_of_chars(text, cursor);
}

static gboolean report(gpointer data)
{
	struct edit *edit = data;
	edit->report = 0;
	const char *text = gtk_editable_get_text(GTK_EDITABLE(edit->text));
	int start, end;
	get_selection(edit, text, &start, &end);
	bool ended = edit->ended;
	edit->ended = false;
	if (!ended && !strcmp(text, edit->prev) && start == edit->prev_start && end == edit->prev_end)
		return G_SOURCE_REMOVE;
	char *prev = edit->prev;
	struct atl_edit_report report = {prev, edit->prev_start, edit->prev_end, text, start, end, ++edit->seq};
	edit->prev = g_strdup(text);
	edit->prev_start = start;
	edit->prev_end = end;
	atl_upcall_edit_report(edit_ref(edit), &report);
	g_free(prev);
	return G_SOURCE_REMOVE;
}

static void schedule_report(struct edit *edit)
{
	if (!edit->applying && !edit->report)
		edit->report = g_idle_add(report, edit);
}

static void on_changed(GtkEditable *editable, struct edit *edit)
{
	schedule_report(edit);
}

static void on_selection_moved(GObject *object, GParamSpec *pspec, struct edit *edit)
{
	schedule_report(edit);
}

static void on_preedit_changed(GtkText *text, const char *preedit, struct edit *edit)
{
	bool open = preedit && *preedit;
	g_object_set_data(G_OBJECT(text), "atl-preedit", GINT_TO_POINTER(open));
	if (edit->preedit && !open) {
		edit->ended = true;
		schedule_report(edit);
	}
	edit->preedit = open;
}

static void on_focus_enter(GtkEventControllerFocus *controller, struct edit *edit)
{
	atl_upcall_edit_focus(edit_ref(edit), true, false);
}

static void on_focus_leave(GtkEventControllerFocus *controller, struct edit *edit)
{
	atl_upcall_edit_focus(edit_ref(edit), false, false);
}

static void edit_free(gpointer data)
{
	struct edit *edit = data;
	if (edit->report)
		g_source_remove(edit->report);
	g_free(edit->prev);
	g_free(edit);
}

struct ui_view *ui_edit_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits)
{
	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *text = gtk_text_new();
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), text);
	wrapper_widget_setup(WRAPPER_WIDGET(wrapper), view_ref, traits);
	input_take_native(text);

	struct edit *edit = g_new0(struct edit, 1);
	edit->text = GTK_TEXT(text);
	edit->prev = g_strdup("");
	g_object_set_data_full(G_OBJECT(text), "atl-edit", edit, edit_free);
	g_signal_connect(text, "changed", G_CALLBACK(on_changed), edit);
	g_signal_connect(text, "notify::cursor-position", G_CALLBACK(on_selection_moved), edit);
	g_signal_connect(text, "notify::selection-bound", G_CALLBACK(on_selection_moved), edit);
	g_signal_connect(text, "preedit-changed", G_CALLBACK(on_preedit_changed), edit);
	GtkEventController *focus = gtk_event_controller_focus_new();
	g_signal_connect(focus, "enter", G_CALLBACK(on_focus_enter), edit);
	g_signal_connect(focus, "leave", G_CALLBACK(on_focus_leave), edit);
	gtk_widget_add_controller(text, focus);
	return (struct ui_view *)text;
}

void ui_edit_set_config(struct ui_view *view, const struct ui_edit_config *config)
{
	GtkText *text = GTK_TEXT(view);
	gtk_text_set_placeholder_text(text, config->hint);
	gtk_text_set_visibility(text, !config->secure);

	static const GtkInputPurpose purposes[] = {
		[UI_KEYBOARD_TEXT] = GTK_INPUT_PURPOSE_FREE_FORM, [UI_KEYBOARD_NUMBER] = GTK_INPUT_PURPOSE_DIGITS,
		[UI_KEYBOARD_DECIMAL] = GTK_INPUT_PURPOSE_NUMBER, [UI_KEYBOARD_PHONE] = GTK_INPUT_PURPOSE_PHONE,
		[UI_KEYBOARD_EMAIL] = GTK_INPUT_PURPOSE_EMAIL, [UI_KEYBOARD_URL] = GTK_INPUT_PURPOSE_URL,
	};
	GtkInputPurpose purpose = purposes[config->keyboard];
	if (config->secure)
		purpose = config->keyboard == UI_KEYBOARD_NUMBER ? GTK_INPUT_PURPOSE_PIN : GTK_INPUT_PURPOSE_PASSWORD;
	gtk_text_set_input_purpose(text, purpose);

	static const GtkInputHints caps[] = {
		[UI_AUTOCAP_NONE] = GTK_INPUT_HINT_NONE, [UI_AUTOCAP_WORDS] = GTK_INPUT_HINT_UPPERCASE_WORDS,
		[UI_AUTOCAP_SENTENCES] = GTK_INPUT_HINT_UPPERCASE_SENTENCES, [UI_AUTOCAP_CHARACTERS] = GTK_INPUT_HINT_UPPERCASE_CHARS,
	};
	GtkInputHints hints = caps[config->autocap] | (config->autocorrect ? GTK_INPUT_HINT_SPELLCHECK : GTK_INPUT_HINT_NO_SPELLCHECK);
	if (config->secure)
		hints |= GTK_INPUT_HINT_PRIVATE;
	gtk_text_set_input_hints(text, hints);
}

// not inside GtkText's own signals: Java's snapshots come from its looper or after a report
void ui_edit_set_state(struct ui_view *view, const char *utf8, int start, int end, uint32_t ack)
{
	struct edit *edit = edit_of(view);
	if (ack != edit->seq || edit->report || edit->preedit)
		return; // a newer report is on its way, and Java answers it
	edit->applying = true;
	GtkEditable *editable = GTK_EDITABLE(view);
	if (strcmp(gtk_editable_get_text(editable), utf8))
		gtk_editable_set_text(editable, utf8);
	gtk_editable_select_region(editable, chars_of_utf16(utf8, start), chars_of_utf16(utf8, end));
	edit->applying = false;
	g_free(edit->prev);
	edit->prev = g_strdup(utf8);
	get_selection(edit, utf8, &edit->prev_start, &edit->prev_end);
}

// GTK types into the focused widget, which follows Java's focus (ui_view_set_focused)
bool ui_edit_set_keyboard(struct ui_view *view, bool shown)
{
	return false;
}
