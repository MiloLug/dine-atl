#ifndef GTK_UI_H
#define GTK_UI_H

#include <gtk/gtk.h>

struct ui_window;

/* The window's stack of activity root views. */
GtkStack *window_stack(struct ui_window *window);
/* The label inside a TextView's box, next to its compound drawables. */
GtkLabel *text_box_get_label(GtkWidget *box);

/* The window's touches and keys go to Java's dispatch through the root view it shows; once per
 * window. */
void input_attach_window(GtkWindow *window);
/* A toolkit widget that takes its own presses (a button, text, a scrollbar): GTK keeps a gesture
 * that starts on it or its children, Java doesn't see it. */
void input_take_native(GtkWidget *widget);
/* No pointer is down; hover exits wait for it. */
bool input_pointers_up(void);

#endif
