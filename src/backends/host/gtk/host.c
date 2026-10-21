#include <gtk/gtk.h>

#include "src/slots/host.h"

/* A desktop window has no back: Back on the root activity finishes it. */

bool host_task_to_back(void)
{
	return false;
}

void host_task_moved(bool front, bool by_host) {}

void host_keep_screen_on(bool on)
{
	static guint cookie;
	GtkApplication *application = GTK_APPLICATION(g_application_get_default());
	if (on && !cookie) {
		cookie = gtk_application_inhibit(application, gtk_application_get_active_window(application),
		                                 GTK_APPLICATION_INHIBIT_SUSPEND | GTK_APPLICATION_INHIBIT_IDLE, "keep-screen-on");
	} else if (!on && cookie) {
		gtk_application_uninhibit(application, cookie);
		cookie = 0;
	}
}
