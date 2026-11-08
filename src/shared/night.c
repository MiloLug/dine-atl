#include <pthread.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/loop.h"

#include "app.h"
#include "nav.h"

void atl_night_apply(JNIEnv *env, struct atl_app *app)
{
	pthread_mutex_lock(&app->insets_lock);
	int night = app->night;
	pthread_mutex_unlock(&app->insets_lock);
	if (night < 0) // UI_DISPLAY_UNKNOWN: the host doesn't say
		return;
	(*env)->PushLocalFrame(env, 8); // posted callbacks run in the base JNI frame, which never pops
	jobjectArray activities = (*env)->NewObjectArray(env, app->backlog_len, (*env)->FindClass(env, "android/app/Activity"), NULL);
	for (size_t i = 0; i < app->backlog_len; i++)
		(*env)->SetObjectArrayElement(env, activities, i, app->backlog[i]);
	(*env)->CallVoidMethod(env, app->record, _METHOD(_CLASS(app->record), "applyNight", "(I[Landroid/app/Activity;)V"), night, activities);
	exit_on_exception(env);
	(*env)->PopLocalFrame(env, NULL);
}

static void night_changed(void *data)
{
	atl_night_apply(get_jni_env(), data);
}

void atl_nav_set_night(struct atl_app *app, int night)
{
	pthread_mutex_lock(&app->insets_lock);
	app->night = night;
	pthread_mutex_unlock(&app->insets_lock);
	if (app->running) // before the start: its start applies it (atl_app_create)
		loop_main_post(app->loop, night_changed, app);
}
