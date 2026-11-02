#include <gio/gio.h>
#include <jni.h>

#include "../api-impl-jni/defines.h"
#include "../api-impl-jni/gtk_util.h"
#include "../shared/app.h"

/* user_data: the gtk host's struct atl_app ** */

static void action_start_activity(GSimpleAction *action, GVariant *parameter, gpointer user_data)
{
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 8); // GLib calls it from the base JNI frame

	jobject context = atl_app_application(env, *(struct atl_app **)user_data);
	jobject intent = intent_deserialize(env, context, parameter);
	(*env)->CallVoidMethod(env, context, handle_cache.context.startActivity, intent);
	exit_on_exception(env);
	(*env)->PopLocalFrame(env, NULL);
}

static void action_start_service(GSimpleAction *action, GVariant *parameter, gpointer user_data)
{
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 8); // GLib calls it from the base JNI frame

	jobject context = atl_app_application(env, *(struct atl_app **)user_data);
	jobject intent = intent_deserialize(env, context, parameter);
	(*env)->CallObjectMethod(env, context, handle_cache.context.startService, intent);
	exit_on_exception(env);
	(*env)->PopLocalFrame(env, NULL);
}

static void action_send_broadcast(GSimpleAction *action, GVariant *parameter, gpointer user_data)
{
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 8); // GLib calls it from the base JNI frame

	jobject context = atl_app_application(env, *(struct atl_app **)user_data);
	jobject intent = intent_deserialize(env, context, parameter);
	(*env)->CallVoidMethod(env, context, handle_cache.context.sendBroadcast, intent);
	exit_on_exception(env);
	(*env)->PopLocalFrame(env, NULL);
}

const GActionEntry action_entries[] = {
	{"startActivity", action_start_activity, INTENT_G_VARIANT_TYPE_STRING},
	{ "startService",  action_start_service, INTENT_G_VARIANT_TYPE_STRING},
	{"sendBroadcast", action_send_broadcast, INTENT_G_VARIANT_TYPE_STRING},
};

const int action_entries_count = ARRAY_SIZE(action_entries);
