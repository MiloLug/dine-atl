#include <stdlib.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_view_Window.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/host.h"
#include "src/slots/ui.h"

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
	static bool kept;
	if (!activity_window_shown(env, this) || kept == keep)
		return;
	kept = keep;
	host_keep_screen_on(keep);
}

JNIEXPORT void JNICALL Java_android_view_Window_set_1frame(JNIEnv *env, jobject this, jboolean has_color, jint color, jboolean light, jboolean fullscreen)
{
	if (!activity_window_shown(env, this))
		return;
	ui_window_set_frame(atl_window, &(struct ui_frame){.has_color = has_color, .color = color, .light = light, .fullscreen = fullscreen});
}
