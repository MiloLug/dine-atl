#include <gtk/gtk.h>

#include "src/slots/host.h"

/* A desktop window has no back: Back on the root activity finishes it. */

bool host_task_to_back(struct atl_app *app)
{
	return false;
}

void host_task_moved(struct atl_app *app, bool front, bool by_host) {}

// a desktop window has no gesture strips
void host_gesture_exclusion_changed(struct atl_app *app, const struct atl_gesture_exclusion *exclusion) {}

void host_keep_screen_on(struct atl_app *app, bool on)
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

static gboolean quit(gpointer data)
{
	g_application_quit(g_application_get_default());
	return G_SOURCE_REMOVE;
}

// one app per process: GApplication's run returns and the process exits
void host_app_end(struct atl_app *app)
{
	g_idle_add(quit, NULL);
}
