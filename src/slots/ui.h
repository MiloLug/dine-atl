#ifndef SLOTS_UI_H
#define SLOTS_UI_H

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>

/* ui slot: the app's window with its activity stack, and view nodes. Called on the Android main
 * thread. */

struct ui_window; // the app's surface holding the activities' root views, or a Dialog's window
struct ui_view;   // a view node; View.widget holds it
struct atl_ref;   // a weak reference to a Java object, for upcalls
struct gfx_drawable;

struct ui_display {
	int width, height; // px
	int night;         // Configuration.UI_MODE_NIGHT_* value
};
#define UI_DISPLAY_UNKNOWN (-1) // for any ui_display field: the platform doesn't say

void ui_display_get(struct ui_display *display);

/* Which View methods the view's class overrides; shared code decides once per view. */
struct ui_view_traits {
	bool draws;          // onDraw, draw or dispatchDraw
	bool scrolls;        // computeScroll
	bool generic_motion; // onGenericMotionEvent or dispatchGenericMotionEvent
	bool hovers;         // dispatchHoverEvent
};

/* ViewGroup.LayoutParams and its margin and gravity subclasses, flattened. */
struct ui_layout_params {
	int width, height; // px, or MATCH_PARENT (-1) / WRAP_CONTENT (-2)
	int gravity;       // android.view.Gravity flags, -1 when unset
	float weight;
	int margin_left, margin_top, margin_right, margin_bottom;
};

/* For hosts other than gtk, whose host creates its GtkApplicationWindow itself. surface: the
 * platform view the app's views go in (uikit: a UIView, retained), or NULL. */
struct ui_window *ui_window_new(void *surface);
void ui_window_set_title(struct ui_window *window, const char *title);
void ui_window_clear_background(struct ui_window *window);
void ui_window_add_activity(struct ui_window *window, struct ui_view *root);
void ui_window_show_activity(struct ui_window *window, struct ui_view *root);
void ui_window_remove_activity(struct ui_window *window, struct ui_view *root);
void ui_window_set_back_enabled(struct ui_window *window, bool enabled);
void ui_window_close(struct ui_window *window);
/* The window's current size, px. */
void ui_window_get_size(struct ui_window *window, int *width, int *height);
/* The window shares the screen with others (Activity.isInMultiWindowMode). */
bool ui_window_is_multi_window(struct ui_window *window);
/* A ViewTreeObserver with listeners: the window calls atl_upcall_global_layout (UI_OBSERVE_LAYOUT,
 * after each layout pass) or atl_upcall_pre_draw (UI_OBSERVE_DRAW, before a frame) with the
 * observer, which the observation owns, until ui_window_unobserve. An observation outlives its
 * window: ui_window_unobserve may come after the window is freed. */
enum ui_observe {
	UI_OBSERVE_LAYOUT,
	UI_OBSERVE_DRAW,
};
struct ui_observation;
struct ui_observation *ui_window_observe(struct ui_window *window, struct atl_ref *observer, enum ui_observe what);
void ui_window_unobserve(struct ui_window *window, struct ui_observation *observation);

/* What the shown activity asks of the host's frame around the window (iOS: the status bar; gtk:
 * the header bar). */
struct ui_frame {
	bool has_color;  // false: the app leaves the frame to the host, and color and light mean nothing
	uint32_t color;  // ARGB
	bool light;      // the frame is light: dark text and icons on it
	bool fullscreen; // FLAG_FULLSCREEN: the host hides its bars; the window keeps its size
};
void ui_window_set_frame(struct ui_window *window, const struct ui_frame *frame);
#ifdef ATL_TEST_HOOKS
/* Prints the shown activity's view tree: "viewtree: " lines, one per view with its class, frame
 * relative to the parent and text, then "viewtree: end". */
void ui_window_dump(struct ui_window *window, FILE *out);
#endif

/* AlertDialog's content, valid only during the call; NULL or 0 when absent. */
struct ui_alert {
	const char *title;
	const char *message;
	const char *const *items;
	int n_items;
	const char *buttons[3]; // positive, negative, neutral: index -1 - DialogInterface.BUTTON_*
	bool cancelable;
};
/* The result of a cancel (back, a tap outside, a Cancel action), next to buttons and items. */
#define UI_DIALOG_CANCEL INT32_MIN

/* A Dialog's window, with the decor view as its content. Results go to atl_upcall_dialog_result;
 * the dialog closes only through ui_dialog_close. */
struct ui_window *ui_dialog_new(struct atl_ref *dialog_ref, struct ui_view *decor);
/* Shows the alert instead of the decor view when alert isn't NULL. */
void ui_dialog_show(struct ui_window *dialog, const struct ui_alert *alert);
void ui_dialog_close(struct ui_window *dialog);
/* Java dropped the Dialog; it was closed or never shown. */
void ui_dialog_free(struct ui_window *dialog);
void ui_dialog_set_cancel_on_touch_outside(struct ui_window *dialog, bool cancel);

/* A View drawn by Java, possibly with children. */
struct ui_view *ui_view_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits);
void ui_view_add_child(struct ui_view *parent, struct ui_view *child, int index);
/* Detaches only: the view may be added again. A child the parent doesn't hold is ignored. */
void ui_view_remove_child(struct ui_view *parent, struct ui_view *child);
/* Java dropped the view. A parent that still holds it was dropped too and is freed without its
 * children being walked. */
void ui_view_free(struct ui_view *view);
void ui_view_set_layout_params(struct ui_view *view, const struct ui_layout_params *params);
void ui_view_set_padding(struct ui_view *view, int left, int top, int right, int bottom);
void ui_view_set_background_color(struct ui_view *view, uint32_t argb);
/* NULL: none. The view holds a ref while the drawable is its background. */
void ui_view_set_background_drawable(struct ui_view *view, struct gfx_drawable *drawable);
/* Android's View.VISIBLE, INVISIBLE or GONE; enabled false stops input */
#define UI_VIEW_VISIBLE   0
#define UI_VIEW_INVISIBLE 4
#define UI_VIEW_GONE      8
void ui_view_set_visibility(struct ui_view *view, int visibility, float alpha, bool enabled);
void ui_view_invalidate(struct ui_view *view);
/* A child's translation changed: its frame moves. */
void ui_view_children_moved(struct ui_view *view);
void ui_view_request_layout(struct ui_view *view);
/* Size for the MeasureSpecs, from the toolkit (views whose Java class doesn't measure itself). */
void ui_view_measure(struct ui_view *view, int width_spec, int height_spec, int *width, int *height);
/* The frame from View.layout, relative to the parent. A ui_view_new view then lays out its
 * children through atl_upcall_view_layout. */
void ui_view_layout(struct ui_view *view, int left, int top, int right, int bottom, float translation_x, float translation_y);
/* View.scrollTo, px. A scroll node's toolkit moves its content, animated if asked, and reports every
 * offset it reaches through atl_upcall_scrolled; elsewhere the backend shifts the children or not. */
void ui_view_scroll_to(struct ui_view *view, int x, int y, bool animated);
/* Java's focus came or went: a toolkit widget that edits text takes the toolkit's focus with it. */
void ui_view_set_focused(struct ui_view *view, bool focused);

/* A TextView: the toolkit measures and draws the text. */
struct ui_view *ui_text_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits, const char *text);
void ui_text_set_text(struct ui_view *view, const char *text);
void ui_text_set_color(struct ui_view *view, uint32_t argb);
void ui_text_set_size(struct ui_view *view, float size); // px
/* Typeface.NORMAL, BOLD, ITALIC or BOLD_ITALIC; any view node, since Button and its subclasses
 * share TextView's code. */
void ui_text_set_style(struct ui_view *view, int style);
#define UI_TEXT_BOLD   1
#define UI_TEXT_ITALIC 2
/* The text holds Pango markup (ATL's Html.fromHtml keeps the tags). */
void ui_text_set_markup(struct ui_view *view, bool markup);

/* An ImageView: the toolkit measures it from the drawable's intrinsic size, px. */
struct ui_view *ui_image_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits);
/* NULL: none. The view holds a ref while it shows the drawable. */
void ui_image_set_drawable(struct ui_view *view, struct gfx_drawable *drawable, int width, int height);
void ui_image_set_scale_type(struct ui_view *view, int scale_type); // ImageView.ScaleType.nativeInt

/* A ScrollView or HorizontalScrollView: the toolkit scrolls its one child along the axis. */
struct ui_view *ui_scroll_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits, bool horizontal);

/* A ProgressBar: a bar for a fraction, or a spinner when indeterminate. */
struct ui_view *ui_progress_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits);
void ui_progress_set_fraction(struct ui_view *view, float fraction);
void ui_progress_set_indeterminate(struct ui_view *view, bool indeterminate);

#endif
