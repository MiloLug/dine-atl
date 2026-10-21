#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_app_Activity.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/host.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "nav.h"
#include "startup.h"
#include "test_hooks.h"

/* the activity stack; the last element is the top */
static jobject *backlog = NULL;
static size_t backlog_len = 0;
static size_t backlog_cap = 0;
static jobject activity_current = NULL;
// in the back, no activity is focused; the stack and the window stay
static bool task_front = true;

static void backlog_push(jobject activity)
{
	if (backlog_len == backlog_cap) {
		backlog_cap = backlog_cap ? backlog_cap * 2 : 8;
		backlog = realloc(backlog, backlog_cap * sizeof(*backlog));
	}
	backlog[backlog_len++] = activity;
}

/* Posted callbacks and host startup run in the Android thread's base JNI frame, which never pops: a
 * local ref left there keeps its object forever. Posted paths into Java push a local frame. */

static struct ui_view *get_decor_view(JNIEnv *env, jobject activity)
{
	jobject window_jobj = _GET_OBJ_FIELD(activity, "window", "Landroid/view/Window;");
	jobject decor_view_jobj = _GET_OBJ_FIELD(window_jobj, "decorView", "Landroid/view/ViewGroup;");
	struct ui_view *decor = _PTR(_GET_LONG_FIELD(decor_view_jobj, "widget"));
	(*env)->DeleteLocalRef(env, decor_view_jobj);
	(*env)->DeleteLocalRef(env, window_jobj);
	return decor;
}

/* An activity's views are attached while its decor is in the app's window (Android: from the first
 * traversal after onResume to after onDestroy). */
static void set_decor_attached(JNIEnv *env, jobject activity, bool attached)
{
	jobject window_jobj = _GET_OBJ_FIELD(activity, "window", "Landroid/view/Window;");
	jobject decor_view_jobj = _GET_OBJ_FIELD(window_jobj, "decorView", "Landroid/view/ViewGroup;");
	(*env)->CallVoidMethod(env, decor_view_jobj, attached ? handle_cache.view.dispatchAttachedToWindow : handle_cache.view.dispatchDetachedFromWindow);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	(*env)->DeleteLocalRef(env, decor_view_jobj);
	(*env)->DeleteLocalRef(env, window_jobj);
}

static void activity_close(JNIEnv *env, jobject activity)
{
	// a pending exception from native code, printed apart from what onDestroy throws
	if ((*env)->ExceptionCheck(env)) {
		fprintf(stderr, "activity.onDestroy: seems there was a pending exception... :");
		(*env)->ExceptionDescribe(env);
	}

	set_decor_attached(env, activity, false);
	ui_window_remove_activity(atl_window, get_decor_view(env, activity));

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

	if (!_GET_BOOL_FIELD(activity, "stopped")) {
		_SET_BOOL_FIELD(activity, "stopped", true);
		(*env)->CallVoidMethod(env, activity, handle_cache.activity.onStop);
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
	}

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.onWindowFocusChanged, false);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

static void activity_focus(JNIEnv *env, jobject activity)
{
	if (_GET_BOOL_FIELD(activity, "finishing"))
		return;

	if (_GET_BOOL_FIELD(activity, "stopped")) {
		_SET_BOOL_FIELD(activity, "stopped", false);
		(*env)->CallVoidMethod(env, activity, handle_cache.activity.onRestart);
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
		if (_GET_BOOL_FIELD(activity, "finishing"))
			return;
	}

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.onStart);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	if (_GET_BOOL_FIELD(activity, "finishing"))
		return;

	ui_window_show_activity(atl_window, get_decor_view(env, activity));
	jobject window = _GET_OBJ_FIELD(activity, "window", "Landroid/view/Window;");
	(*env)->CallVoidMethod(env, window, handle_cache.window.updateFrame);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	(*env)->CallVoidMethod(env, window, handle_cache.window.dispatchInsets);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	(*env)->DeleteLocalRef(env, window);

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
	jmethodID on_back_pressed = _METHOD_OF(activity_current, "onBackPressed", "()V");
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);

	return backlog_len > 1 || handle_cache.activity.onBackPressed != on_back_pressed;
}

static void activity_update_current(JNIEnv *env)
{
	jobject activity_new = task_front && backlog_len ? backlog[backlog_len - 1] : NULL;

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

	atl_test_window_ready(atl_window);
}

bool activity_task_front(void)
{
	return task_front;
}

jobject activity_shown_window(JNIEnv *env)
{
	if (!task_front || !backlog_len || _GET_BOOL_FIELD(backlog[backlog_len - 1], "finishing"))
		return NULL;
	return _GET_OBJ_FIELD(backlog[backlog_len - 1], "window", "Landroid/view/Window;");
}

bool activity_window_shown(JNIEnv *env, jobject window)
{
	jobject shown = activity_shown_window(env);
	bool same = shown && (*env)->IsSameObject(env, shown, window);
	(*env)->DeleteLocalRef(env, shown);
	return same;
}

static void task_move(bool front, bool by_host)
{
	if (task_front == front)
		return;
	task_front = front;
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 16);
	// before the lifecycle: shows from onStop wait for the front, shows from onResume go on top
	(*env)->CallStaticVoidMethod(env, handle_cache.dialog.class, handle_cache.dialog.atlTaskMoved, front);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	activity_update_current(env);
	(*env)->PopLocalFrame(env, NULL);
	host_task_moved(front, by_host);
}

static void nav_back(void *data)
{
	JNIEnv *env = get_jni_env();
	if (!activity_current)
		return;
	(*env)->CallVoidMethod(env, activity_current, handle_cache.activity.onBackPressed);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

static void nav_home(void *data)
{
	task_move(false, true);
}

static void nav_front(void *data)
{
	task_move(true, true);
}

bool atl_nav_back(void)
{
	return loop_main_post(nav_back, NULL);
}

bool atl_nav_home(void)
{
	return loop_main_post(nav_home, NULL);
}

bool atl_nav_front(void)
{
	return loop_main_post(nav_front, NULL);
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
	(*env)->PushLocalFrame(env, 16);
	jobject *activities = backlog;
	size_t n = backlog_len;
	backlog = NULL;
	backlog_len = backlog_cap = 0;
	activity_update_current(env);
	close_activities(env, activities, n);
	free(activities);
	(*env)->PopLocalFrame(env, NULL);
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
	set_decor_attached(env, activity_object, true);

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

static void task_to_back(void *data)
{
	task_move(false, false);
}

JNIEXPORT jboolean JNICALL Java_android_app_Activity_nativeMoveTaskToBack(JNIEnv *env, jclass class)
{
	// posted: the activity's callbacks don't run inside the app's onBackPressed
	return host_task_to_back() && loop_main_post(task_to_back, NULL);
}

JNIEXPORT jboolean JNICALL Java_android_app_Activity_isTaskRoot(JNIEnv *env, jobject this)
{
	jobject root_activity = backlog_len ? backlog[0] : NULL;
	// NULL means that we are currently creating the root activity, so no other activity can exist yet
	return root_activity == NULL || (*env)->IsSameObject(env, this, root_activity);
}

JNIEXPORT jboolean JNICALL Java_android_app_Activity_isInMultiWindowMode(JNIEnv *env, jobject this)
{
	return ui_window_is_multi_window(atl_window);
}
