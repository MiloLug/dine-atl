#ifndef GTK_UI_H
#define GTK_UI_H

#include <gtk/gtk.h>

struct ui_window;

/* The window's stack of activity root views. */
GtkStack *window_stack(struct ui_window *window);
/* The label inside a TextView's box, next to its compound drawables. */
GtkLabel *text_box_get_label(GtkWidget *box);

#endif
