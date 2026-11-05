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

#include "app.h"
#include "input.h"
#include "nav.h"
#include "startup.h"
#include "test_hooks.h"

static void backlog_push(struct atl_app *app, jobject activity)
{
	if (app->backlog_len == app->backlog_cap) {
		app->backlog_cap = app->backlog_cap ? app->backlog_cap * 2 : 8;
		app->backlog = realloc(app->backlog, app->backlog_cap * sizeof(*app->backlog));
	}
	app->backlog[app->backlog_len++] = activity;
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
	exit_on_exception(env);
	(*env)->DeleteLocalRef(env, decor_view_jobj);
	(*env)->DeleteLocalRef(env, window_jobj);
}

static void activity_close(JNIEnv *env, struct atl_app *app, jobject activity)
{
	set_decor_attached(env, activity, false);
	ui_window_remove_activity(app->window, get_decor_view(env, activity));

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.performDestroy);
	exit_on_exception(env);
	windows_owner_closed(env, app, activity); // after onDestroy, as AOSP's closeAll: it may dismiss them
}

static void activity_unfocus(JNIEnv *env, struct atl_app *app, jobject activity)
{
	if (!_GET_BOOL_FIELD(activity, "paused")) {
		(*env)->CallVoidMethod(env, activity, handle_cache.activity.performPause);
		exit_on_exception(env);
	}
	windows_token_visible(env, app, activity, false); // AOSP: hidden once paused, before onStop

	if (!_GET_BOOL_FIELD(activity, "stopped")) {
		_SET_BOOL_FIELD(activity, "stopped", true);
		(*env)->CallVoidMethod(env, activity, handle_cache.activity.performStop);
		exit_on_exception(env);
	}

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.onWindowFocusChanged, false);
	exit_on_exception(env);
}

static void activity_focus(JNIEnv *env, struct atl_app *app, jobject activity)
{
	if (_GET_BOOL_FIELD(activity, "finishing"))
		return;

	if (_GET_BOOL_FIELD(activity, "stopped")) {
		_SET_BOOL_FIELD(activity, "stopped", false);
		(*env)->CallVoidMethod(env, activity, handle_cache.activity.onRestart);
		exit_on_exception(env);
		if (_GET_BOOL_FIELD(activity, "finishing"))
			return;
	}

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.performStart);
	exit_on_exception(env);
	if (_GET_BOOL_FIELD(activity, "finishing"))
		return;

	ui_window_show_activity(app->window, get_decor_view(env, activity));
	jobject window = _GET_OBJ_FIELD(activity, "window", "Landroid/view/Window;");
	(*env)->CallVoidMethod(env, window, handle_cache.window.updateFrame);
	exit_on_exception(env);
	(*env)->CallVoidMethod(env, window, handle_cache.window.dispatchInsets);
	exit_on_exception(env);
	(*env)->DeleteLocalRef(env, window);

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.performResume);
	exit_on_exception(env);
	if (_GET_BOOL_FIELD(activity, "finishing"))
		return;

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.performPostResume);
	exit_on_exception(env);
	if (_GET_BOOL_FIELD(activity, "finishing"))
		return;
	windows_token_visible(env, app, activity, true); // AOSP's makeVisible, after the resume

	(*env)->CallVoidMethod(env, activity, handle_cache.activity.onWindowFocusChanged, true);
	exit_on_exception(env);
}

// whether back does more than closing the last activity
static bool back_has_target(JNIEnv *env, struct atl_app *app)
{
	jmethodID on_back_pressed = _METHOD_OF(app->activity_current, "onBackPressed", "()V");
	exit_on_exception(env);

	return app->backlog_len > 1 || handle_cache.activity.onBackPressed != on_back_pressed;
}

static void activity_update_current(JNIEnv *env, struct atl_app *app)
{
	jobject activity_new = app->task_front && app->backlog_len ? app->backlog[app->backlog_len - 1] : NULL;

	if (app->activity_current != activity_new) {
		if (app->activity_current)
			activity_unfocus(env, app, app->activity_current);
		app->activity_current = NULL;

		if (activity_new)
			activity_focus(env, app, activity_new);
		if (activity_new && _GET_BOOL_FIELD(activity_new, "finishing"))
			return;

		app->activity_current = activity_new;
	}

	if (app->activity_current != NULL)
		ui_window_set_back_enabled(app->window, back_has_target(env, app));
	else
		atl_window_state_reset(app);
	windows_update(env, app);
}

void activity_window_ready(struct atl_app *app)
{
	JNIEnv *env = get_jni_env();

	for (size_t i = app->backlog_len; i-- > 0;) {
		(*env)->CallVoidMethod(env, app->backlog[i], handle_cache.activity.onWindowFocusChanged, true);
		exit_on_exception(env);
	}

	atl_test_window_ready(app->window);
}

jobject activity_shown_window(JNIEnv *env, struct atl_app *app)
{
	if (!app->task_front || !app->backlog_len || _GET_BOOL_FIELD(app->backlog[app->backlog_len - 1], "finishing"))
		return NULL;
	return _GET_OBJ_FIELD(app->backlog[app->backlog_len - 1], "window", "Landroid/view/Window;");
}

bool activity_window_shown(JNIEnv *env, struct atl_app *app, jobject window)
{
	jobject shown = activity_shown_window(env, app);
	bool same = shown && (*env)->IsSameObject(env, shown, window);
	(*env)->DeleteLocalRef(env, shown);
	return same;
}

static void task_move(struct atl_app *app, bool front, bool by_host)
{
	if (app->task_front == front)
		return;
	app->task_front = front;
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 16);
	activity_update_current(env, app);
	(*env)->PopLocalFrame(env, NULL);
	host_task_moved(app, front, by_host);
}

// the BACK key through the key chain: a shown dialog, the focused views, the activity
static void nav_back(void *data)
{
	struct atl_app *app = data;
	JNIEnv *env = get_jni_env();
	if (!app->activity_current)
		return;
	(*env)->PushLocalFrame(env, 4);
	jobject window = _GET_OBJ_FIELD(app->activity_current, "window", "Landroid/view/Window;");
	jobject decor = _GET_OBJ_FIELD(window, "decorView", "Landroid/view/ViewGroup;");
	struct atl_key key = {.action = ATL_KEY_DOWN, .code = ATL_KEYCODE_BACK, .down_time = atl_uptime_ms()};
	key.time = key.down_time;
	atl_upcall_key((struct atl_ref *)decor, &key);
	key.action = ATL_KEY_UP;
	key.time = atl_uptime_ms();
	atl_upcall_key((struct atl_ref *)decor, &key);
	(*env)->PopLocalFrame(env, NULL);
}

static void nav_home(void *data)
{
	task_move(data, false, true);
}

static void nav_front(void *data)
{
	task_move(data, true, true);
}

bool atl_nav_back(struct atl_app *app)
{
	return app->running && loop_main_post(app->loop, nav_back, app);
}

bool atl_nav_home(struct atl_app *app)
{
	return app->running && loop_main_post(app->loop, nav_home, app);
}

bool atl_nav_front(struct atl_app *app)
{
	return app->running && loop_main_post(app->loop, nav_front, app);
}

// closes activities[n-1] down to activities[0], the order the stack held them in
static void close_activities(JNIEnv *env, struct atl_app *app, jobject *activities, size_t n)
{
	for (size_t i = n; i-- > 0;) {
		activity_close(env, app, activities[i]);
		_UNREF(activities[i]);
	}
}

void activity_close_all(struct atl_app *app)
{
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 16);
	jobject *activities = app->backlog;
	size_t n = app->backlog_len;
	app->backlog = NULL;
	app->backlog_len = app->backlog_cap = 0;
	activity_update_current(env, app);
	close_activities(env, app, activities, n);
	free(activities);
	(*env)->PopLocalFrame(env, NULL);
}

static void close_window_if_empty(void *data)
{
	struct atl_app *app = data;
	if (app->backlog_len == 0) {
		app->running = false;
		ui_window_close(app->window);
	}
}

void activity_start(JNIEnv *env, struct atl_app *app, jobject activity_object)
{
	if (app->activity_current)
		activity_unfocus(env, app, app->activity_current);
	app->activity_current = NULL;
	(*env)->CallVoidMethod(env, activity_object, handle_cache.activity.performCreate, NULL);
	exit_on_exception(env);

	if (_GET_BOOL_FIELD(activity_object, "finishing")) { // finish() was called before the activity was created
		return;
	}

	(*env)->CallVoidMethod(env, activity_object, handle_cache.activity.onPostCreate, NULL);
	exit_on_exception(env);

	backlog_push(app, _REF(activity_object));

	ui_window_add_activity(app->window, get_decor_view(env, activity_object));
	set_decor_attached(env, activity_object, true);

	activity_update_current(env, app);
}

JNIEXPORT void JNICALL Java_android_app_Activity_nativeFinish(JNIEnv *env, jobject this, jlong window)
{
	struct atl_app *app = atl_app_of(env, this);
	jobject removed_activity = NULL;
	for (size_t i = app->backlog_len; i-- > 0;) {
		if ((*env)->IsSameObject(env, this, app->backlog[i])) {
			removed_activity = app->backlog[i];
			memmove(&app->backlog[i], &app->backlog[i + 1], (app->backlog_len - i - 1) * sizeof(*app->backlog));
			app->backlog_len--;
			break;
		}
	}
	activity_update_current(env, app);
	if (removed_activity) {
		activity_close(env, app, removed_activity);
		_UNREF(removed_activity);
	}
	if (app->backlog_len == 0 && window)
		loop_main_post(app->loop, close_window_if_empty, app);
}

JNIEXPORT void JNICALL Java_android_app_Activity_nativeStartActivity(JNIEnv *env, jclass class, jobject activity)
{
	activity_start(env, atl_app_of(env, activity), activity);
}

JNIEXPORT jboolean JNICALL Java_android_app_Activity_nativeResumeActivity(JNIEnv *env, jclass class, jobject context, jclass activity_class, jobject intent)
{
	struct atl_app *app = atl_app_of(env, context);
	jobject *activities_to_close = NULL;
	size_t n_to_close = 0;
	jboolean found = JNI_FALSE;
	for (size_t i = app->backlog_len; i-- > 0;) {
		if ((*env)->IsSameObject(env, activity_class, _CLASS(app->backlog[i]))) {
			n_to_close = app->backlog_len - i - 1;
			if (n_to_close) {
				activities_to_close = malloc(n_to_close * sizeof(*activities_to_close));
				memcpy(activities_to_close, &app->backlog[i + 1], n_to_close * sizeof(*activities_to_close));
				app->backlog_len = i + 1;
			}

			(*env)->CallVoidMethod(env, app->backlog[i], handle_cache.activity.onNewIntent, intent);
			exit_on_exception(env);
			found = JNI_TRUE;
			break;
		}
	}
	activity_update_current(env, app);

	close_activities(env, app, activities_to_close, n_to_close);
	free(activities_to_close);

	return found;
}

static void task_to_back(void *data)
{
	task_move(data, false, false);
}

JNIEXPORT jboolean JNICALL Java_android_app_Activity_nativeMoveTaskToBack(JNIEnv *env, jobject this)
{
	struct atl_app *app = atl_app_of(env, this);
	// posted: the activity's callbacks don't run inside the app's onBackPressed
	return host_task_to_back(app) && loop_main_post(app->loop, task_to_back, app);
}

JNIEXPORT jboolean JNICALL Java_android_app_Activity_isTaskRoot(JNIEnv *env, jobject this)
{
	struct atl_app *app = atl_app_of(env, this);
	jobject root_activity = app->backlog_len ? app->backlog[0] : NULL;
	// NULL means that we are currently creating the root activity, so no other activity can exist yet
	return root_activity == NULL || (*env)->IsSameObject(env, this, root_activity);
}

JNIEXPORT jboolean JNICALL Java_android_app_Activity_isInMultiWindowMode(JNIEnv *env, jobject this)
{
	return ui_window_is_multi_window(atl_app_of(env, this)->window);
}
