#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <art_embedder.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_atl_ATLLoadedApp.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/host.h"

#include "app.h"
#include "upcall.h"

struct atl_app *atl_app_new(void)
{
	struct atl_app *app = calloc(1, sizeof(struct atl_app));
	pthread_mutex_init(&app->insets_lock, NULL);
	app->task_front = true;
	app->screen_brightness = -1;
	app->activity_state.brightness = -1;
	return app;
}

static jobject group_of(JNIEnv *env, struct atl_app *app)
{
	return _GET_OBJ_FIELD(app->record, "group", "Ljava/lang/ThreadGroup;");
}

static char *string_of(JNIEnv *env, jobject object, const char *method)
{
	jstring string = (*env)->CallObjectMethod(env, object, _METHOD(_CLASS(object), method, "()Ljava/lang/String;"));
	const char *chars = (*env)->GetStringUTFChars(env, string, NULL);
	char *copy = strdup(chars);
	(*env)->ReleaseStringUTFChars(env, string, chars);
	(*env)->DeleteLocalRef(env, string);
	return copy;
}

void atl_app_bind(JNIEnv *env, struct atl_app *app, jobject record)
{
	app->record = _REF(record);
	app->apk_class_path = string_of(env, record, "classPath");
	app->data_dir = string_of(env, record, "dataDir");
	jobject group = group_of(env, app);
	jclass group_class = (*env)->FindClass(env, "java/lang/ThreadGroup");
	(*env)->SetLongField(env, group, (*env)->GetFieldID(env, group_class, "embedderTag", "J"), _INTPTR(app));
	(*env)->DeleteLocalRef(env, group_class);
	(*env)->DeleteLocalRef(env, group);
}

int atl_app_join(JNIEnv *env, struct atl_app *app)
{
	jobject group = group_of(env, app);
	bool joined = art_join_thread_group(env, group);
	(*env)->DeleteLocalRef(env, group);
	if (!joined) {
		if ((*env)->ExceptionCheck(env)) {
			(*env)->ExceptionDescribe(env);
			(*env)->ExceptionClear(env);
		}
		return -1;
	}
	return 0;
}

void atl_app_take_attaches(JNIEnv *env, struct atl_app *app)
{
	jobject group = group_of(env, app);
	art_set_default_attach_group(env, group);
	(*env)->DeleteLocalRef(env, group);
}

jobject atl_app_application(JNIEnv *env, struct atl_app *app)
{
	return (*env)->CallObjectMethod(env, app->record, _METHOD(_CLASS(app->record), "getApplication", "()Landroid/app/Application;"));
}

struct atl_app *atl_app_of(JNIEnv *env, jobject context)
{
	static jmethodID atl_process_app;
	static jfieldID native_app;
	if (!atl_process_app) { // native_app first: another thread may see atl_process_app set
		native_app = (*env)->GetFieldID(env, (*env)->FindClass(env, "android/atl/ATLLoadedApp"), "native_app", "J");
		atl_process_app = _METHOD(handle_cache.context.class, "atl_process_app", "()Landroid/atl/ATLLoadedApp;");
	}
	jobject record = (*env)->CallObjectMethod(env, context, atl_process_app);
	exit_on_exception(env);
	struct atl_app *app = record ? _PTR((*env)->GetLongField(env, record, native_app)) : NULL;
	(*env)->DeleteLocalRef(env, record);
	return app;
}

struct atl_app *atl_app_of_window(JNIEnv *env, jobject window)
{
	jobject context = _GET_OBJ_FIELD(window, "context", "Landroid/content/Context;");
	struct atl_app *app = atl_app_of(env, context);
	(*env)->DeleteLocalRef(env, context);
	return app;
}

void atl_app_end(struct atl_app *app)
{
	app->running = false;
	host_app_end(app);
}

JNIEXPORT void JNICALL Java_android_atl_ATLLoadedApp_nativeEnd(JNIEnv *env, jclass this, jlong app)
{
	atl_app_end(_PTR(app));
}

struct loop_main *atl_app_loop(struct atl_app *app)
{
	return app->loop;
}

void *atl_app_ui(struct atl_app *app)
{
	return app->ui;
}

void atl_app_set_ui(struct atl_app *app, void *ui)
{
	app->ui = ui;
}

// NULL for a dialog made on a thread of no app (android_app_Dialog.c)
struct ui_window *atl_app_window(struct atl_app *app)
{
	return app ? app->window : NULL;
}

struct ui_window *atl_thread_app_window(void)
{
	struct atl_app *app = atl_app_current();
	return app ? app->window : NULL;
}

struct atl_app *atl_app_current(void)
{
	return art_thread_embedder_tag();
}

JNIEXPORT jobject JNICALL Java_android_atl_ATLLoadedApp_ofCurrentThread(JNIEnv *env, jclass this)
{
	struct atl_app *app = atl_app_current();
	return app ? (*env)->NewLocalRef(env, app->record) : NULL;
}
