#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_widget_Toast.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/ui.h"

#include "app.h"

// a toast shows only while its app is in front; one from the back never shows
JNIEXPORT void JNICALL Java_android_widget_Toast_nativeShow(JNIEnv *env, jclass class, jstring text, jint gravity, jint x, jint y)
{
	struct atl_app *app = atl_app_current();
	if (!app || !app->window || !app->task_front)
		return;
	char *chars = jstring_to_utf8(env, text);
	ui_toast_show(app->window, chars, gravity, x, y);
	free(chars);
	app->toast_shown = true;
}

void atl_toast_hide(struct atl_app *app)
{
	if (!app->toast_shown)
		return;
	app->toast_shown = false;
	ui_toast_hide(app->window);
}

JNIEXPORT void JNICALL Java_android_widget_Toast_nativeHide(JNIEnv *env, jclass class)
{
	struct atl_app *app = atl_app_current();
	if (app && app->window)
		atl_toast_hide(app);
}
