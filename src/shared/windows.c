/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/view/Gravity.java at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_view_Window.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "app.h"
#include "upcall.h"
#include "windows.h"

#define FIRST_SYSTEM_WINDOW 2000 // LayoutParams' types from here on are the app's, not an activity's

struct atl_subwindow {
	struct atl_app *app;
	struct ui_view *root;     // NULL: an alert, which the ui slot shows itself
	struct ui_window *chrome; // an AlertDialog's alert: around root, or alone
	jweak java_window;
	jobject held;  // global ref to it while added: the window manager holds a shown Dialog (WindowManagerGlobal)
	jweak decor;   // the root's View
	jweak owner;   // its context's Activity; NULL: none
	struct ui_window_layout layout;
	struct atl_window_frame frame;
	struct atl_window_frame panel; // with a chrome around root: the frame and the chrome
	bool requested; // added by a show, until a dismiss
	bool hidden;    // Dialog.hide: still added
	bool visible;   // as Java last heard it (Window.atlSetVisible)
	bool leaked;    // went with its activity: a dismiss throws, as on Android
	struct atl_subwindow *next; // the app's in show order, the topmost last
};

static void call_window(JNIEnv *env, struct atl_subwindow *window, jmethodID method, ...)
{
	jobject java_window = (*env)->NewLocalRef(env, window->java_window);
	if (!java_window)
		return;
	va_list args;
	va_start(args, method);
	(*env)->CallVoidMethodV(env, java_window, method, args);
	va_end(args);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
	(*env)->DeleteLocalRef(env, java_window);
}

// the activity it belongs to: a system type's lifecycle is the app's (Window.adjustLayoutParamsForSubWindow)
static jweak token(const struct atl_subwindow *window)
{
	return window->layout.type < FIRST_SYSTEM_WINDOW ? window->owner : NULL;
}

// AOSP's token lives from attach to destroy: a window shown from onCreate waits for the start
static bool token_alive(JNIEnv *env, jweak activity)
{
	jobject local = (*env)->NewLocalRef(env, activity);
	bool alive = local && !_GET_BOOL_FIELD(local, "destroyed");
	if (local)
		(*env)->DeleteLocalRef(env, local);
	return alive;
}

static bool shows(JNIEnv *env, struct atl_app *app, const struct atl_subwindow *window)
{
	if (!window->requested || window->hidden || window->leaked || !app->task_front)
		return false;
	if (!token(window))
		return true;
	return app->token_shown && (*env)->IsSameObject(env, token(window), app->token_shown);
}

static void release(JNIEnv *env, struct atl_subwindow *window)
{
	if (window->held)
		_UNREF(window->held);
	window->held = NULL;
}

static void push_roots(struct atl_app *app)
{
	if (!app->window)
		return;
	size_t n = 0;
	for (struct atl_subwindow *window = app->subwindows; window; window = window->next)
		n += window->visible && (window->root || window->chrome);
	struct ui_root *roots = n ? malloc(n * sizeof(*roots)) : NULL, *root = roots;
	for (struct atl_subwindow *window = app->subwindows; window; window = window->next) {
		if (window->visible && (window->root || window->chrome))
			*root++ = (struct ui_root){window, window->root, window->chrome, window->layout};
	}
	ui_window_set_roots(app->window, roots, n);
	free(roots);
}

void windows_update(JNIEnv *env, struct atl_app *app)
{
	for (struct atl_subwindow *window = app->subwindows; window; window = window->next) {
		bool visible = shows(env, app, window);
		if (window->visible == visible)
			continue;
		window->visible = visible;
		// a listener may show or dismiss windows: that update has pushed them by its return
		call_window(env, window, handle_cache.window.atlSetVisible, visible);
	}
	push_roots(app);
}

void windows_owner_closed(JNIEnv *env, struct atl_app *app, jobject activity)
{
	if (app->token_shown && (*env)->IsSameObject(env, app->token_shown, activity))
		app->token_shown = NULL;
	for (struct atl_subwindow *window = app->subwindows; window; window = window->next) {
		if (!token(window) || !window->requested || !(*env)->IsSameObject(env, token(window), activity))
			continue;
		window->leaked = true;
		call_window(env, window, handle_cache.window.atlLeaked, activity);
		release(env, window);
	}
	windows_update(env, app);
}

void windows_token_visible(JNIEnv *env, struct atl_app *app, jobject activity, bool visible)
{
	if (visible)
		app->token_shown = activity;
	else if (app->token_shown && (*env)->IsSameObject(env, app->token_shown, activity))
		app->token_shown = NULL;
	jobject java_window = _GET_OBJ_FIELD(activity, "window", "Landroid/view/Window;");
	(*env)->CallVoidMethod(env, java_window, handle_cache.window.atlSetVisible, visible);
	exit_on_exception(env);
	(*env)->DeleteLocalRef(env, java_window);
	windows_update(env, app);
}

JNIEXPORT jlong JNICALL Java_android_view_Window_nativeAddSubwindow(JNIEnv *env, jobject this, jobject owner)
{
	struct atl_subwindow *window = calloc(1, sizeof(*window));
	window->app = atl_app_of_window(env, this);
	if (!window->app) // a Context of a package no host runs: the app whose main thread makes the window
		window->app = atl_app_current();
	window->java_window = (*env)->NewWeakGlobalRef(env, this);
	jobject decor = _GET_OBJ_FIELD(this, "decorView", "Landroid/view/ViewGroup;");
	window->decor = (*env)->NewWeakGlobalRef(env, decor);
	(*env)->DeleteLocalRef(env, decor);
	window->owner = owner ? (*env)->NewWeakGlobalRef(env, owner) : NULL;
	struct atl_subwindow **last = &window->app->subwindows;
	while (*last)
		last = &(*last)->next;
	*last = window;
	return _INTPTR(window);
}

// at a show: an AlertDialog's content decides whether its decor is drawn, inside its alert's panel
JNIEXPORT void JNICALL Java_android_view_Window_nativeSetSubwindowRoot(JNIEnv *env, jobject this, jlong ptr, jlong root, jlong chrome)
{
	struct atl_subwindow *window = _PTR(ptr);
	window->root = _PTR(root);
	window->chrome = _PTR(chrome);
}

void windows_chrome_changed(struct ui_window *chrome)
{
	struct atl_app *app = ui_window_app(chrome);
	for (struct atl_subwindow *window = app ? app->subwindows : NULL; window; window = window->next) {
		if (window->chrome == chrome && window->root && window->visible) {
			push_roots(app);
			return;
		}
	}
}

JNIEXPORT jboolean JNICALL Java_android_view_Window_nativeShowSubwindow(JNIEnv *env, jobject this, jlong ptr)
{
	struct atl_subwindow *window = _PTR(ptr);
	struct atl_app *app = window->app;
	if (token(window) ? !token_alive(env, token(window)) : window->layout.type < FIRST_SYSTEM_WINDOW)
		return false; // BadTokenException
	window->requested = true;
	window->hidden = false;
	if (!window->held)
		window->held = _REF(this);
	for (struct atl_subwindow **link = &app->subwindows; *link; link = &(*link)->next) {
		if (*link == window) {
			*link = window->next;
			break;
		}
	}
	window->next = NULL;
	struct atl_subwindow **last = &app->subwindows;
	while (*last)
		last = &(*last)->next;
	*last = window;
	windows_update(env, app);
	return true;
}

// remove: a dismiss, else a hide that keeps it added
JNIEXPORT jboolean JNICALL Java_android_view_Window_nativeHideSubwindow(JNIEnv *env, jobject this, jlong ptr, jboolean remove)
{
	struct atl_subwindow *window = _PTR(ptr);
	if (window->leaked)
		return false; // "not attached to window manager"
	if (remove) {
		window->requested = false;
		release(env, window);
	} else {
		window->hidden = true;
	}
	windows_update(env, window->app);
	return true;
}

static void free_subwindow(void *data)
{
	struct atl_subwindow *window = data;
	JNIEnv *env = get_jni_env();
	for (struct atl_subwindow **link = &window->app->subwindows; *link; link = &(*link)->next) {
		if (*link == window) {
			*link = window->next;
			break;
		}
	}
	if (window->visible)
		push_roots(window->app);
	release(env, window);
	(*env)->DeleteWeakGlobalRef(env, window->java_window);
	(*env)->DeleteWeakGlobalRef(env, window->decor);
	if (window->owner)
		(*env)->DeleteWeakGlobalRef(env, window->owner);
	free(window);
}

// from finalize: any thread; a window of an app whose loop quit stays allocated
JNIEXPORT void JNICALL Java_android_view_Window_nativeFreeSubwindow(JNIEnv *env, jclass class, jlong ptr)
{
	struct atl_subwindow *window = _PTR(ptr);
	if (!window->app || !loop_main_post(window->app->loop, free_subwindow, window))
		fprintf(stderr, "atl: a window of an app that ended stays allocated\n");
}

JNIEXPORT jobject JNICALL Java_android_view_Window_nativeKeyWindow(JNIEnv *env, jclass class, jobject context)
{
	struct atl_app *app = atl_app_of(env, context);
	struct atl_subwindow *top = NULL;
	for (struct atl_subwindow *window = app ? app->subwindows : NULL; window; window = window->next) {
		if (window->visible && !(window->layout.flags & UI_WINDOW_NOT_FOCUSABLE))
			top = window;
	}
	return top ? (*env)->NewLocalRef(env, top->java_window) : NULL;
}

JNIEXPORT void JNICALL Java_android_view_Window_set_1layout(JNIEnv *env, jobject this, jlong ptr, jint type, jint flags, jint gravity, jint x, jint y, jint width, jint height, jint min_width, jfloat dim_amount)
{
	struct atl_subwindow *window = _PTR(ptr);
	window->layout = (struct ui_window_layout){type, flags, gravity, x, y, width, height, min_width, dim_amount};
	if (window->visible)
		push_roots(window->app);
}

#define GRAVITY_PULL_BEFORE  0x2
#define GRAVITY_PULL_AFTER   0x4
#define GRAVITY_Y_SHIFT      4
#define MEASURE_SPEC_EXACTLY (1 << 30)
#define MEASURE_SPEC_AT_MOST (2 << 30)

/* Copyright (C) 2006 The Android Open Source Project: Gravity.apply on one axis, left to right
 * (START and END are LEFT and RIGHT), without AXIS_CLIP; FILL stretches *size */
static int gravity_apply(int gravity, int *size, int container, int adjust)
{
	switch (gravity & (GRAVITY_PULL_BEFORE | GRAVITY_PULL_AFTER)) {
	case 0:
		return (container - *size) / 2 + adjust;
	case GRAVITY_PULL_BEFORE:
		return adjust;
	case GRAVITY_PULL_AFTER:
		return container - adjust - *size;
	default:
		*size = container;
		return adjust;
	}
}

// LayoutParams' width or height: px, MATCH_PARENT (-1) or WRAP_CONTENT (-2)
static int window_spec(int size, int available)
{
	return size >= 0 ? size | MEASURE_SPEC_EXACTLY : size == -1 ? available | MEASURE_SPEC_EXACTLY : available | MEASURE_SPEC_AT_MOST;
}

// a panel alert: the chrome sets the width, the root fills it inside the chrome at its own height
static void place_panel(struct atl_subwindow *window, int screen_width, int screen_height)
{
	struct atl_ref *root = (struct atl_ref *)window->decor;
	const struct ui_window_layout *layout = &window->layout;
	struct ui_chrome chrome;
	ui_dialog_chrome(window->chrome, screen_width, screen_height, &chrome);
	int width = chrome.width - chrome.left - chrome.right;
	int room = chrome.max_height - chrome.top - chrome.bottom;
	atl_upcall_view_measure(root, (width > 0 ? width : 0) | MEASURE_SPEC_EXACTLY, (room > 0 ? room : 0) | MEASURE_SPEC_AT_MOST);
	int height = atl_upcall_view_get_measured(root, true);
	int panel_width = chrome.width, panel_height = chrome.top + height + chrome.bottom;
	int left = gravity_apply(layout->gravity, &panel_width, screen_width, layout->x);
	int top = gravity_apply(layout->gravity >> GRAVITY_Y_SHIFT, &panel_height, screen_height, layout->y);
	window->panel = (struct atl_window_frame){left, top, chrome.width, panel_height};
	window->frame = (struct atl_window_frame){left + chrome.left, top + chrome.top, width, height};
}

void windows_place(struct atl_subwindow *window, int screen_width, int screen_height, struct atl_window_frame *frame)
{
	if (window->chrome) {
		place_panel(window, screen_width, screen_height);
		*frame = window->frame;
		call_window(get_jni_env(), window, handle_cache.window.atlSetFrame, frame->left, frame->top);
		return;
	}
	struct atl_ref *root = (struct atl_ref *)window->decor;
	const struct ui_window_layout *layout = &window->layout;
	atl_upcall_view_measure(root, window_spec(layout->width, screen_width), window_spec(layout->height, screen_height));
	int width = atl_upcall_view_get_measured(root, false), height = atl_upcall_view_get_measured(root, true);
	if (layout->width == -2 && width < layout->min_width) {
		atl_upcall_view_measure(root, layout->min_width | MEASURE_SPEC_EXACTLY, window_spec(layout->height, screen_height));
		width = atl_upcall_view_get_measured(root, false);
		height = atl_upcall_view_get_measured(root, true);
	}
	int measured_width = width, measured_height = height;
	int left = gravity_apply(layout->gravity, &width, screen_width, layout->x);
	int top = gravity_apply(layout->gravity >> GRAVITY_Y_SHIFT, &height, screen_height, layout->y);
	if (width != measured_width || height != measured_height)
		atl_upcall_view_measure(root, width | MEASURE_SPEC_EXACTLY, height | MEASURE_SPEC_EXACTLY);
	window->frame = (struct atl_window_frame){left, top, width, height};
	*frame = window->frame;
	call_window(get_jni_env(), window, handle_cache.window.atlSetFrame, left, top);
}

const struct atl_window_frame *windows_frame(const struct atl_subwindow *window)
{
	return &window->frame;
}

const struct atl_window_frame *windows_panel(const struct atl_subwindow *window)
{
	return window->chrome ? &window->panel : &window->frame;
}

static bool frame_holds(const struct atl_window_frame *frame, float x, float y)
{
	return x >= frame->left && x < frame->left + frame->width && y >= frame->top && y < frame->top + frame->height;
}

const struct ui_root *windows_touch_target(const struct ui_root *roots, size_t n, float x, float y)
{
	for (size_t i = n; i-- > 0;) {
		int flags = roots[i].layout.flags;
		if (!roots[i].view || flags & UI_WINDOW_NOT_TOUCHABLE)
			continue;
		bool inside = frame_holds(windows_panel(roots[i].window), x, y);
		if (inside || !(flags & (UI_WINDOW_NOT_FOCUSABLE | UI_WINDOW_NOT_TOUCH_MODAL)))
			return &roots[i];
	}
	return NULL;
}

bool windows_in_chrome(const struct atl_subwindow *window, float x, float y)
{
	return window->chrome && frame_holds(&window->panel, x, y) && !frame_holds(&window->frame, x, y);
}
