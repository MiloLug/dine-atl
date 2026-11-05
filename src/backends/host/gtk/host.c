#include <errno.h>
#include <glib/gstdio.h>
#include <gtk/gtk.h>
#include <math.h>
#include <signal.h>
#include <stdio.h>
#include <unistd.h>

#include "src/slots/host.h"

#include "host_gtk.h"

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

/* An app's window brightness on the desktop: GNOME's power daemon over D-Bus, else
 * /sys/class/backlight (writable only with the right permissions). The desktop's own level waits
 * in a file of this process's while the app's applies; a later start restores a dead process's. */

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

static bool power_set(int percent)
{
	GVariant *result = power_call("Set", g_variant_new("(ssv)", "org.gnome.SettingsDaemon.Power.Screen", "Brightness", g_variant_new_int32(percent)));
	if (result)
		g_variant_unref(result);
	return result != NULL;
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

static void backlight_write(const char *name, int value)
{
	gchar *path = g_build_filename("/sys/class/backlight", name, "brightness", NULL);
	FILE *fp = fopen(path, "w");
	if (fp) {
		fprintf(fp, "%d", value);
		fclose(fp);
	} else {
		g_printerr("can't set %s: %s\n", path, g_strerror(errno));
	}
	g_free(path);
}

#define SAVED_PREFIX "atl-desktop-brightness-"

static char *saved_path(void)
{
	char name[64];
	snprintf(name, sizeof(name), SAVED_PREFIX "%d", (int)getpid());
	return g_build_filename(g_get_user_runtime_dir(), name, NULL);
}

// the desktop's level as lines "power <percent>" or "backlight <device> <raw>"
static void save_desktop(void)
{
	GString *saved = g_string_new(NULL);
	GVariant *result = power_call("Get", g_variant_new("(ss)", "org.gnome.SettingsDaemon.Power.Screen", "Brightness"));
	if (result) {
		GVariant *value;
		g_variant_get(result, "(v)", &value);
		g_string_append_printf(saved, "power %d\n", g_variant_get_int32(value));
		g_variant_unref(value);
		g_variant_unref(result);
	} else {
		GDir *dir = g_dir_open("/sys/class/backlight", 0, NULL);
		for (const gchar *name; dir && (name = g_dir_read_name(dir));) {
			int now = backlight_read(name, "brightness");
			if (now >= 0)
				g_string_append_printf(saved, "backlight %s %d\n", name, now);
		}
		if (dir)
			g_dir_close(dir);
	}
	char *path = saved_path();
	g_file_set_contents(path, saved->str, saved->len, NULL);
	g_free(path);
	g_string_free(saved, TRUE);
}

static void restore_file(const char *path)
{
	char *saved;
	if (g_file_get_contents(path, &saved, NULL, NULL)) {
		char **lines = g_strsplit(saved, "\n", -1);
		for (char **line = lines; *line; line++) {
			int value;
			char name[256];
			if (sscanf(*line, "power %d", &value) == 1)
				power_set(value);
			else if (sscanf(*line, "backlight %255s %d", name, &value) == 2)
				backlight_write(name, value);
		}
		g_strfreev(lines);
		g_free(saved);
		g_unlink(path);
	}
}

static void restore_desktop(void)
{
	char *path = saved_path();
	restore_file(path);
	g_free(path);
}

// 0 is the lowest level, not off (AOSP's PowerManager clamps it too)
static void set_app_level(float brightness)
{
	if (power_set(CLAMP((int)lroundf(brightness * 100), 1, 100)))
		return;
	GDir *dir = g_dir_open("/sys/class/backlight", 0, NULL);
	for (const gchar *name; dir && (name = g_dir_read_name(dir));) {
		int max = backlight_read(name, "max_brightness");
		if (max > 0)
			backlight_write(name, CLAMP((int)lroundf(brightness * max), 1, max));
	}
	if (dir)
		g_dir_close(dir);
}

void host_screen_brightness(struct atl_app *app, float brightness)
{
	if (brightness < 0) {
		restore_desktop();
		return;
	}
	char *path = saved_path();
	if (!g_file_test(path, G_FILE_TEST_EXISTS))
		save_desktop();
	g_free(path);
	set_app_level(brightness);
}

static void on_shutdown(GApplication *application, gpointer data)
{
	restore_desktop();
}

void host_gtk_init(GApplication *application)
{
	// runs that ended without their restore (a crash, a kill)
	GDir *dir = g_dir_open(g_get_user_runtime_dir(), 0, NULL);
	for (const gchar *name; dir && (name = g_dir_read_name(dir));) {
		int pid;
		if (sscanf(name, SAVED_PREFIX "%d", &pid) != 1 || (kill(pid, 0) == 0 || errno == EPERM))
			continue;
		char *path = g_build_filename(g_get_user_runtime_dir(), name, NULL);
		restore_file(path);
		g_free(path);
	}
	if (dir)
		g_dir_close(dir);
	g_signal_connect(application, "shutdown", G_CALLBACK(on_shutdown), NULL);
}

static gboolean quit(gpointer data)
{
	g_application_quit(g_application_get_default());
	return G_SOURCE_REMOVE;
}

// one app per process: GApplication's run returns and the process exits
void host_app_end(struct atl_app *app)
{
	restore_desktop();
	g_idle_add(quit, NULL);
}
