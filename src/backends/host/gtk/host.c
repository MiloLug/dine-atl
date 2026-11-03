#include <errno.h>
#include <gtk/gtk.h>
#include <math.h>
#include <stdio.h>

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

/* The desktop's backlight in percent: GNOME's power daemon over D-Bus, else /sys/class/backlight
 * (writable only with the right permissions). Synchronous, so the restore at the end completes. */

static GVariant *power_call(const char *method, GVariant *parameters)
{
	GDBusConnection *connection = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	if (!connection)
		return NULL;
	GVariant *result = g_dbus_connection_call_sync(connection, "org.gnome.SettingsDaemon.Power", "/org/gnome/SettingsDaemon/Power",
	                                               "org.freedesktop.DBus.Properties", method, parameters, NULL, G_DBUS_CALL_FLAGS_NONE,
	                                               1000, NULL, NULL);
	g_object_unref(connection);
	return result;
}

static int backlight_read(const char *name, const char *file)
{
	gchar *path = g_build_filename("/sys/class/backlight", name, file, NULL);
	FILE *fp = fopen(path, "r");
	g_free(path);
	int value = -1;
	if (fp) {
		if (fscanf(fp, "%d", &value) != 1)
			value = -1;
		fclose(fp);
	}
	return value;
}

// -1 when neither answers
static int brightness_get(void)
{
	GVariant *result = power_call("Get", g_variant_new("(ss)", "org.gnome.SettingsDaemon.Power.Screen", "Brightness"));
	if (result) {
		GVariant *value;
		g_variant_get(result, "(v)", &value);
		int percent = g_variant_get_int32(value);
		g_variant_unref(value);
		g_variant_unref(result);
		return percent;
	}
	GDir *dir = g_dir_open("/sys/class/backlight", 0, NULL);
	const gchar *name = dir ? g_dir_read_name(dir) : NULL;
	int percent = -1;
	if (name) {
		int max = backlight_read(name, "max_brightness"), now = backlight_read(name, "brightness");
		if (max > 0 && now >= 0)
			percent = now * 100 / max;
	}
	if (dir)
		g_dir_close(dir);
	return percent;
}

static void brightness_set(int percent)
{
	GVariant *result = power_call("Set", g_variant_new("(ssv)", "org.gnome.SettingsDaemon.Power.Screen", "Brightness", g_variant_new_int32(percent)));
	if (result) {
		g_variant_unref(result);
		return;
	}
	GDir *dir = g_dir_open("/sys/class/backlight", 0, NULL);
	if (!dir)
		return;
	for (const gchar *name; (name = g_dir_read_name(dir));) {
		int max = backlight_read(name, "max_brightness");
		gchar *path = g_build_filename("/sys/class/backlight", name, "brightness", NULL);
		FILE *fp = max > 0 ? fopen(path, "w") : NULL;
		if (fp) {
			fprintf(fp, "%d", percent * max / 100);
			fclose(fp);
		} else {
			g_printerr("can't set %s: %s\n", path, g_strerror(errno));
		}
		g_free(path);
	}
	g_dir_close(dir);
}

static int saved_brightness = -1; // the desktop's, from before the app's first, while the app's applies

void host_screen_brightness(struct atl_app *app, float brightness)
{
	if (brightness < 0) {
		if (saved_brightness >= 0)
			brightness_set(saved_brightness);
		saved_brightness = -1;
		return;
	}
	if (saved_brightness < 0)
		saved_brightness = brightness_get();
	brightness_set(lroundf(brightness * 100));
}

static gboolean quit(gpointer data)
{
	g_application_quit(g_application_get_default());
	return G_SOURCE_REMOVE;
}

// one app per process: GApplication's run returns and the process exits
void host_app_end(struct atl_app *app)
{
	if (saved_brightness >= 0)
		brightness_set(saved_brightness);
	saved_brightness = -1;
	g_idle_add(quit, NULL);
}
