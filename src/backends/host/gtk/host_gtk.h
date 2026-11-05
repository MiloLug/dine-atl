#ifndef HOST_GTK_H
#define HOST_GTK_H

#include <gio/gio.h>

/* Before the app runs: the desktop's brightness back if a run ended without restoring it, and
 * again at the application's shutdown. */
void host_gtk_init(GApplication *application);

#endif
