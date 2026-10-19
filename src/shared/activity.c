#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_app_Activity.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "startup.h"

/* the activity stack; the last element is the top */
static jobject *backlog = NULL;
static size_t backlog_len = 0;
static size_t backlog_cap = 0;
static jobject activity_current = NULL;

static void backlog_push(jobject activity)
{
	if (backlog_len == backlog_cap) {
		backlog_cap = backlog_cap ? backlog_cap * 2 : 8;
		backlog = realloc(backlog, backlog_cap * sizeof(*backlog));
	}
	backlog[backlog_len++] = activity;
}

static struct ui_view *get_decor_view(JNIEnv *env, jobject activity)
{
	jobject window_jobj = _GET_OBJ_FIELD(activity, "window", "Landroid/view/Window;");
	jobject decor_view_jobj = _GET_OBJ_FIELD(window_jobj, "decorView", "Landroid/view/ViewGroup;");
	return _PTR(_GET_LONG_FIELD(decor_view_jobj, "widget"));
}

static void activity_close(JNIEnv *env, jobject activity)
{
	// in case some exception was left unhandled in native code, print it here so we don't confuse it with an exception thrown by onDestroy
	if ((*env)->ExceptionCheck(env)) {
		fprintf(stderr, "activity.onDestroy: seems there was a pending exception... :");
		(*env)->ExceptionDescribe(env);
	}

	ui_window_remove_activity(atl_window, get_decor_view(env, activity));

	/* -- run the activity's onDestroy -- */
	(*env)->CallVoidMethod(env, activity, handle_cache.activity.onDestroy);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

static void activity_unfocus(JNIEnv *env, jobject activity)
{
	if (!_GET_BOOL_FIELD(activity, "paused")) {
		(*env)->CallVoidMethod(env, activity, handle_cache.activity.onPause);
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
	}

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.onStop);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.onWindowFocusChanged, false);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

static void activity_focus(JNIEnv *env, jobject activity)
{
	if (_GET_BOOL_FIELD(activity, "finishing"))
		return;

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.onStart);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	if (_GET_BOOL_FIELD(activity, "finishing"))
		return;

	ui_window_show_activity(atl_window, get_decor_view(env, activity));

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.onResume);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	if (_GET_BOOL_FIELD(activity, "finishing"))
		return;

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.onPostResume);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	if (_GET_BOOL_FIELD(activity, "finishing"))
		return;

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.onWindowFocusChanged, true);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

// whether back does more than closing the last activity
static bool back_has_target(JNIEnv *env)
{
	jclass current_activity_class = (*env)->GetObjectClass(env, activity_current);
	jmethodID current_activity_on_back_pressed_method_id = (*env)->GetMethodID(env, current_activity_class, "onBackPressed", "()V");
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);

	return backlog_len > 1 || handle_cache.activity.onBackPressed != current_activity_on_back_pressed_method_id;
}

static void activity_update_current(JNIEnv *env)
{
	jobject activity_new = backlog_len ? backlog[backlog_len - 1] : NULL;

	if (activity_current != activity_new) {
		if (activity_current)
			activity_unfocus(env, activity_current);
		activity_current = NULL;

		if (activity_new)
			activity_focus(env, activity_new);
		if (activity_new && _GET_BOOL_FIELD(activity_new, "finishing"))
			return;

		activity_current = activity_new;
	}

	if (activity_current != NULL)
		ui_window_set_back_enabled(atl_window, back_has_target(env));
}

void activity_window_ready(void)
{
	JNIEnv *env = get_jni_env();

	for (size_t i = backlog_len; i-- > 0;) {
		(*env)->CallVoidMethod(env, backlog[i], handle_cache.activity.onWindowFocusChanged, true);
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
	}

	if (getenv("ATL_DUMP_VIEWTREE"))
		ui_window_dump(atl_window, stdout);
}

void current_activity_back_pressed(void)
{
	JNIEnv *env = get_jni_env();

	// Either a new activity was added to the backlog or the current activity's onBackPressed method was changed
	if (back_has_target(env)) {
		(*env)->CallVoidMethod(env, activity_current, handle_cache.activity.onBackPressed);
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
	} else {
		ui_window_set_back_enabled(atl_window, false);
	}
}

// closes activities[n-1] down to activities[0], the order the stack held them in
static void close_activities(JNIEnv *env, jobject *activities, size_t n)
{
	for (size_t i = n; i-- > 0;) {
		activity_close(env, activities[i]);
		_UNREF(activities[i]);
	}
}

void activity_close_all(void)
{
	JNIEnv *env = get_jni_env();
	// local backup of the backlog
	jobject *activities = backlog;
	size_t n = backlog_len;
	// deactivate all activities
	backlog = NULL;
	backlog_len = backlog_cap = 0;
	activity_update_current(env);
	// destroy all activities
	close_activities(env, activities, n);
	free(activities);
}

static void close_window_if_empty(void *window)
{
	if (backlog_len == 0)
		ui_window_close(window);
}

void activity_start(JNIEnv *env, jobject activity_object)
{
	if (activity_current)
		activity_unfocus(env, activity_current);
	activity_current = NULL;
	/* -- run the activity's onCreate -- */
	(*env)->CallVoidMethod(env, activity_object, handle_cache.activity.onCreate, NULL);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);

	if (_GET_BOOL_FIELD(activity_object, "finishing")) { // finish() was called before the activity was created
		return;
	}

	(*env)->CallVoidMethod(env, activity_object, handle_cache.activity.onPostCreate, NULL);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);

	backlog_push(_REF(activity_object));

	ui_window_add_activity(atl_window, get_decor_view(env, activity_object));

	activity_update_current(env);
}

JNIEXPORT void JNICALL Java_android_app_Activity_nativeFinish(JNIEnv *env, jobject this, jlong window)
{
	jobject removed_activity = NULL;
	for (size_t i = backlog_len; i-- > 0;) {
		if ((*env)->IsSameObject(env, this, backlog[i])) {
			removed_activity = backlog[i];
			memmove(&backlog[i], &backlog[i + 1], (backlog_len - i - 1) * sizeof(*backlog));
			backlog_len--;
			break;
		}
	}
	activity_update_current(env);
	if (removed_activity) {
		activity_close(env, removed_activity);
		_UNREF(removed_activity);
	}
	if (backlog_len == 0 && window)
		loop_main_post(close_window_if_empty, _PTR(window));
}

JNIEXPORT void JNICALL Java_android_app_Activity_nativeStartActivity(JNIEnv *env, jclass class, jobject activity)
{
	activity_start(env, activity);
}

JNIEXPORT jboolean JNICALL Java_android_app_Activity_nativeResumeActivity(JNIEnv *env, jclass class, jclass activity_class, jobject intent)
{
	jobject *activities_to_close = NULL;
	size_t n_to_close = 0;
	jboolean found = JNI_FALSE;
	for (size_t i = backlog_len; i-- > 0;) {
		if ((*env)->IsSameObject(env, activity_class, _CLASS(backlog[i]))) {
			n_to_close = backlog_len - i - 1;
			if (n_to_close) {
				activities_to_close = malloc(n_to_close * sizeof(*activities_to_close));
				memcpy(activities_to_close, &backlog[i + 1], n_to_close * sizeof(*activities_to_close));
				backlog_len = i + 1;
			}

			/* -- run the activity's onNewIntent -- */
			(*env)->CallVoidMethod(env, backlog[i], handle_cache.activity.onNewIntent, intent);
			if ((*env)->ExceptionCheck(env))
				(*env)->ExceptionDescribe(env);
			found = JNI_TRUE;
			break;
		}
	}
	activity_update_current(env);

	close_activities(env, activities_to_close, n_to_close);
	free(activities_to_close);

	return found;
}

JNIEXPORT jboolean JNICALL Java_android_app_Activity_isTaskRoot(JNIEnv *env, jobject this)
{
	jobject root_activity = backlog_len ? backlog[0] : NULL;
	// NULL means that we are currently creating the root activity, so no other activity can exist yet
	return root_activity == NULL || (*env)->IsSameObject(env, this, root_activity);
}
