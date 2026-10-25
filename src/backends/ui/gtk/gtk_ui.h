#ifndef GTK_UI_H
#define GTK_UI_H

#include <gtk/gtk.h>

struct ui_window;

/* The window's stack of activity root views. */
GtkStack *window_stack(struct ui_window *window);
/* A text node's label, next to its compound drawables in a box; NULL on other handles. */
GtkLabel *text_get_label(GtkWidget *handle);
/* The box with a text node's label; handle: the node's handle holding it, NULL when it is the box. */
GtkWidget *text_box_new(GtkWidget *handle, const char *class_name, const char *text);
/* A push or toggle button draws GTK's border only while its view has no background of its own. */
void button_set_has_background(GtkWidget *handle, bool has_background);

/* The window's touches and keys go to Java's dispatch through the root view it shows; once per
 * window. */
void input_attach_window(GtkWindow *window);
/* A toolkit widget that takes its own presses (text, a scrollbar): GTK keeps a gesture that starts
 * on it or its children, Java doesn't see it. */
void input_take_native(GtkWidget *widget);
void input_return_native(GtkWidget *widget);
/* No pointer is down; hover exits wait for it. */
bool input_pointers_up(void);
/* One pointer's DOWN and UP at the view's centre, through its window's root view as a press there
 * goes; whether Java took the DOWN. */
bool input_tap(GtkWidget *view);

/* ATL_DBG_AGENT: the debug agent's socket (agent.c, test hooks); once. */
void agent_start(void);

#endif
