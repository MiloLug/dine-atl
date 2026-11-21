#ifndef GTK_UI_H
#define GTK_UI_H

#include <gtk/gtk.h>

struct atl_app;
struct ui_window;

extern struct atl_app *host_app; // the gtk host's one app (main.c)

/* The window's stack of activity root views, under an overlay of the windows over the activity
 * (ui_window_set_roots). */
GtkStack *window_stack(struct ui_window *window);
void window_attach_stack(GtkWindow *window, GtkWidget *stack);
/* The root wrapper a DOWN at x, y in the window's content goes to: a window over the activity, or
 * the shown activity's; NULL: none. */
GtkWidget *window_touch_root(GtkWindow *window, double x, double y);
/* toast.c: the toast's label back on top of the windows ui_window_set_roots added */
void toast_raise(GtkOverlay *overlay);
/* A DOWN (its first pointer in the window's content): ACTION_OUTSIDE to the windows above its root that watch. */
struct atl_touch;
void window_touch_outside(GtkWindow *window, const struct atl_touch *down);
/* A text node's label, next to its compound drawables in a box; NULL on other handles. */
GtkLabel *text_get_label(GtkWidget *handle);
/* The box with a text node's label; handle: the node's handle holding it, NULL when it is the box. */
GtkWidget *text_box_new(GtkWidget *handle, const char *class_name, const char *text);
/* edit.c: the focused GtkText's pending report goes to Java now: a key or touch after the typing sees
 * its text. NULL or a widget that is no GtkText: nothing. */
void edit_flush_report(GtkWidget *focus);
/* A push or toggle button draws GTK's border only while its view has no background of its own. */
void button_set_has_background(GtkWidget *handle, bool has_background);

/* The window's touches and keys go to Java's dispatch through the root view it shows; once per
 * window. */
void input_attach_window(GtkWindow *window);
/* A toolkit widget that takes its own presses (text, a scrollbar): GTK keeps a gesture that starts
 * on it or its children, Java doesn't see it. */
void input_take_native(GtkWidget *widget);
void input_return_native(GtkWidget *widget);
/* A root wrapper left the window or stopped being shown: a gesture on it ends with a CANCEL, as
 * Android's does for a window that is removed or hidden. */
void input_root_gone(GtkWidget *root);
/* One pointer's event, what on_pointer_event reads from a GdkEvent: sequence is its handle (NULL:
 * the mouse), x and y px in native's space. The gtk debug agent feeds it with made-up handles, as GTK 4
 * has no public GdkEvent constructor. True when the gesture is Java's (the first DOWN was taken). */
enum input_pointer {
	INPUT_POINTER_DOWN,
	INPUT_POINTER_MOVE,
	INPUT_POINTER_UP,
	INPUT_POINTER_CANCEL,
};
gboolean input_pointer(enum input_pointer what, GtkNative *native, GdkEventSequence *sequence, double x, double y);
/* One key event of a window's key controller for phase (what on_key reads from the GdkEvent):
 * action is ATL_KEY_DOWN or ATL_KEY_UP, keycode the hardware keycode. The gtk debug agent feeds it;
 * true when Java took the key. */
gboolean input_key(GtkWindow *window, GtkPropagationPhase phase, guint keyval, guint keycode, GdkModifierType state, int action);
/* No pointer is down; hover exits wait for it. */
bool input_pointers_up(void);
/* One pointer's DOWN and UP at the view's centre, through its window's root view as a press there
 * goes; whether Java took the DOWN. */
bool input_tap(GtkWidget *view);

/* A panel alert's box around its window's root, NULL before its show (dialog.c). */
GtkWidget *dialog_panel(struct ui_window *dialog);

/* ATL_DBG_AGENT: the debug agent's socket (agent.c, test hooks); once. */
void agent_start(void);

#endif
