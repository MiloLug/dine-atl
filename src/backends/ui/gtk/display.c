#include <gtk/gtk.h>
#include <libportal/portal.h>
#include <string.h>
#ifdef XDP_TYPE_INPUT_CAPTURE_SESSION // libportal >= 0.8
	#include <libportal/settings.h>
#endif

#include "src/api-impl-jni/content/android_content_Context.h"
#include "src/shared/upcall.h"
#include "src/slots/ui.h"

static void monitor_size(GdkMonitor *monitor, struct ui_display *display)
{
	GdkRectangle geometry;
	gdk_monitor_get_geometry(monitor, &geometry);
	display->width = geometry.width;
	display->height = geometry.height;
}

static void monitor_changed_cb(GdkSurface *surface, GdkMonitor *monitor, gpointer user_data)
{
	struct ui_display display = {.night = UI_DISPLAY_UNKNOWN};
	monitor_size(monitor, &display);
	atl_upcall_display_changed(&display);
}

#ifdef XDP_TYPE_INPUT_CAPTURE_SESSION // libportal >= 0.8
static int night_from_color_scheme(GVariant *value)
{
	int color_sheme = g_variant_get_uint32(value);
	g_object_set(gtk_settings_get_default(), "gtk-application-prefer-dark-theme", color_sheme == 1, NULL);
	if (color_sheme == 1) // Prefer dark appearance
		return /*UI_MODE_NIGHT_YES*/ 0x20;
	else if (color_sheme == 2) // Prefer light appearance
		return /*UI_MODE_NIGHT_NO*/ 0x10;
	else // No preference
		return /*UI_MODE_NIGHT_UNDEFINED*/ 0x00;
}

static void settings_changed_cb(XdpSettings *xdp_settings, gchar *namestpace, gchar *key, GVariant *value, gpointer user_data)
{
	if (!strcmp(namestpace, "org.freedesktop.appearance") && !strcmp(key, "color-scheme")) {
		struct ui_display display = {UI_DISPLAY_UNKNOWN, UI_DISPLAY_UNKNOWN, night_from_color_scheme(value)};
		atl_upcall_display_changed(&display);
	}
}

static XdpSettings *xdp_settings = NULL;
#endif

void ui_display_get(struct ui_display *display)
{
	GdkDisplay *gdk_display = gdk_display_get_default();
	monitor_size(g_list_model_get_item(gdk_display_get_monitors(gdk_display), 0), display);
	display->night = UI_DISPLAY_UNKNOWN;

#ifdef XDP_TYPE_INPUT_CAPTURE_SESSION // libportal >= 0.8
	if (!xdp_settings) {
		GError *error = NULL;
		XdpPortal *portal = xdp_portal_initable_new(&error);
		if (!portal) {
			printf("xdp_portal_initable_new failed: %s\n", error->message);
			g_error_free(error);
			return;
		}
		xdp_settings = xdp_portal_get_settings(portal);
		g_object_unref(portal);
		g_signal_connect(xdp_settings, "changed", G_CALLBACK(settings_changed_cb), NULL);
	}
	GVariant *color_sheme = xdp_settings_read_value(xdp_settings, "org.freedesktop.appearance", "color-scheme", NULL, NULL);
	if (color_sheme) {
		display->night = night_from_color_scheme(color_sheme);
		g_variant_unref(color_sheme);
	}
#endif
}

void update_config_for_window(GtkWindow *window)
{
	GdkSurface *surface = gtk_native_get_surface(GTK_NATIVE(window));
	monitor_changed_cb(surface, gdk_display_get_monitor_at_surface(gdk_display_get_default(), surface), NULL);
	g_signal_connect(surface, "enter-monitor", G_CALLBACK(monitor_changed_cb), NULL);
}
