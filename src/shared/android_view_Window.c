#include <stdlib.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_view_Window.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/host.h"
#include "src/slots/ui.h"

#include "app.h"
#include "startup.h"

JNIEXPORT void JNICALL Java_android_view_Window_set_1title(JNIEnv *env, jobject this, jlong window, jstring title_jstr)
{
	char *title = jstring_to_utf8(env, title_jstr);
	ui_window_set_title(_PTR(window), title);
	free(title);
}

JNIEXPORT void JNICALL Java_android_view_Window_clear_1background(JNIEnv *env, jobject this, jlong window)
{
	ui_window_clear_background(_PTR(window));
}

// the shown activity's window decides, as for the frame
JNIEXPORT void JNICALL Java_android_view_Window_set_1keep_1screen_1on(JNIEnv *env, jobject this, jboolean keep)
{
	struct atl_app *app = atl_app_of_window(env, this);
	if (!activity_window_shown(env, app, this) || app->keep_screen_on == keep)
		return;
	app->keep_screen_on = keep;
	host_keep_screen_on(app, keep);
}

JNIEXPORT void JNICALL Java_android_view_Window_set_1screen_1brightness(JNIEnv *env, jobject this, jfloat brightness)
{
	struct atl_app *app = atl_app_of_window(env, this);
	if (brightness < 0)
		brightness = -1;
	if (!activity_window_shown(env, app, this) || app->screen_brightness == brightness)
		return;
	app->screen_brightness = brightness;
	host_screen_brightness(app, brightness);
}

JNIEXPORT void JNICALL Java_android_view_Window_set_1layout(JNIEnv *env, jobject this, jlong window, jint flags, jint gravity, jint x, jint y, jint width, jint height, jint min_width)
{
	ui_window_set_layout(_PTR(window), &(struct ui_window_layout){flags, gravity, x, y, width, height, min_width});
}

JNIEXPORT void JNICALL Java_android_view_Window_set_1frame(JNIEnv *env, jobject this, jboolean has_color, jint color, jboolean light, jboolean fullscreen)
{
	struct atl_app *app = atl_app_of_window(env, this);
	if (!activity_window_shown(env, app, this))
		return;
	ui_window_set_frame(app->window, &(struct ui_frame){.has_color = has_color, .color = color, .light = light, .fullscreen = fullscreen});
}
