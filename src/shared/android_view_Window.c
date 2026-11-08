#include <stdlib.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_view_Window.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/host.h"
#include "src/slots/ui.h"

#include "app.h"
#include "startup.h"
#include "windows.h"

// the shown activity's window decides, as for the status bar: one toolkit window shows every activity
JNIEXPORT void JNICALL Java_android_view_Window_set_1title(JNIEnv *env, jobject this, jstring title_jstr)
{
	struct atl_app *app = atl_app_of_window(env, this);
	if (!activity_window_shown(env, app, this))
		return;
	char *title = jstring_to_utf8(env, title_jstr);
	ui_window_set_title(app->window, title);
	free(title);
}

JNIEXPORT void JNICALL Java_android_view_Window_clear_1background(JNIEnv *env, jobject this, jlong window)
{
	ui_window_clear_background(_PTR(window));
}

/* A window over the activities keeps its state from its creation on, since it shows later; an
 * activity's counts while it is the shown one, whose show sends it again (Window.updateFrame). */
struct atl_window_state *atl_window_state_of(JNIEnv *env, struct atl_app *app, jobject window, jlong subwindow)
{
	if (subwindow)
		return windows_state(_PTR(subwindow));
	return activity_window_shown(env, app, window) ? &app->activity_state : NULL;
}

JNIEXPORT void JNICALL Java_android_view_Window_set_1keep_1screen_1on(JNIEnv *env, jobject this, jlong subwindow, jboolean keep)
{
	struct atl_app *app = atl_app_of_window(env, this);
	struct atl_window_state *state = atl_window_state_of(env, app, this, subwindow);
	if (!state)
		return;
	state->keep_screen_on = keep;
	windows_state_changed(app);
}

void atl_window_state_reset(struct atl_app *app)
{
	app->activity_state.keep_screen_on = false;
	app->activity_state.brightness = -1;
	windows_state_changed(app);
}

JNIEXPORT void JNICALL Java_android_view_Window_set_1screen_1brightness(JNIEnv *env, jobject this, jlong subwindow, jfloat brightness)
{
	struct atl_app *app = atl_app_of_window(env, this);
	struct atl_window_state *state = atl_window_state_of(env, app, this, subwindow);
	if (!state)
		return;
	state->brightness = brightness < 0 ? -1 : brightness;
	windows_state_changed(app);
}

// a window over the activities adds only its fullscreen flag
JNIEXPORT void JNICALL Java_android_view_Window_set_1frame(JNIEnv *env, jobject this, jlong subwindow, jboolean has_color, jint color, jboolean light, jboolean fullscreen)
{
	struct atl_app *app = atl_app_of_window(env, this);
	struct atl_window_state *state = atl_window_state_of(env, app, this, subwindow);
	if (!state)
		return;
	state->fullscreen = fullscreen;
	if (!subwindow && (!app->bar_set || app->bar_has_color != has_color || app->bar_color != color || app->bar_light != light)) {
		app->bar_set = true;
		app->bar_has_color = has_color;
		app->bar_color = color;
		app->bar_light = light;
		app->frame_sent = false;
	}
	windows_state_changed(app);
}
