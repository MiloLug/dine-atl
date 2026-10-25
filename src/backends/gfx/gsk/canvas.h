#ifndef GFX_GSK_CANVAS_H
#define GFX_GSK_CANVAS_H

#include <gtk/gtk.h>

/* A gtk-only native pushed onto the canvas (clipPath): the restore of its save level pops it. */
void gsk_canvas_pushed(GtkSnapshot *snapshot);

#endif
