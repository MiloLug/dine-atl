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

struct ui_display {
	int width, height; // px
	int night;         // Configuration.UI_MODE_NIGHT_* value
};
#define UI_DISPLAY_UNKNOWN (-1) // for any ui_display field: the platform doesn't say

void ui_display_get(struct ui_display *display);

/* Which View methods the view's class overrides; shared code decides once per view. */
struct ui_view_traits {
	bool draws;            // onDraw, draw or dispatchDraw
	bool clicks;           // performClick
	bool touches;          // onTouchEvent, or dispatches_touch
	bool dispatches_touch; // dispatchTouchEvent other than View's and ViewGroup's
	bool scrolls;          // computeScroll
	bool keys;             // dispatchKeyEvent or onKeyDown
	bool generic_motion;   // onGenericMotionEvent or dispatchGenericMotionEvent
	bool hovers;           // dispatchHoverEvent
};

/* ViewGroup.LayoutParams and its margin and gravity subclasses, flattened. */
struct ui_layout_params {
	int width, height; // px, or MATCH_PARENT (-1) / WRAP_CONTENT (-2)
	int gravity;       // android.view.Gravity flags, -1 when unset
	float weight;
	int margin_left, margin_top, margin_right, margin_bottom;
};

/* For hosts other than gtk, whose host creates its GtkApplicationWindow itself. */
struct ui_window *ui_window_new(void);
void ui_window_set_title(struct ui_window *window, const char *title);
void ui_window_set_java(struct ui_window *window, struct atl_ref *window_ref);
void ui_window_clear_background(struct ui_window *window);
void ui_window_add_activity(struct ui_window *window, struct ui_view *root);
void ui_window_show_activity(struct ui_window *window, struct ui_view *root);
void ui_window_remove_activity(struct ui_window *window, struct ui_view *root);
void ui_window_set_back_enabled(struct ui_window *window, bool enabled);
void ui_window_close(struct ui_window *window);
/* The window's current size, px. */
void ui_window_get_size(struct ui_window *window, int *width, int *height);
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
void ui_dialog_set_cancel_on_touch_outside(struct ui_window *dialog, bool cancel);

/* A View drawn by Java, possibly with children. */
struct ui_view *ui_view_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits);
void ui_view_add_child(struct ui_view *parent, struct ui_view *child, int index);
void ui_view_set_layout_params(struct ui_view *view, const struct ui_layout_params *params);
void ui_view_set_padding(struct ui_view *view, int left, int top, int right, int bottom);
void ui_view_set_background_color(struct ui_view *view, uint32_t argb);
void ui_view_request_layout(struct ui_view *view);
/* Size for the MeasureSpecs, from the toolkit (views whose Java class doesn't measure itself). */
void ui_view_measure(struct ui_view *view, int width_spec, int height_spec, int *width, int *height);
/* The frame from View.layout, relative to the parent. A ui_view_new view then lays out its
 * children through atl_upcall_view_layout. */
void ui_view_layout(struct ui_view *view, int left, int top, int right, int bottom, float translation_x, float translation_y);

/* A TextView: the toolkit measures and draws the text. */
struct ui_view *ui_text_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits, const char *text);
void ui_text_set_text(struct ui_view *view, const char *text);
void ui_text_set_color(struct ui_view *view, uint32_t argb);
void ui_text_set_size(struct ui_view *view, float size); // px

#endif
