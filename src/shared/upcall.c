#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/util.h"

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/slots/ui.h"

#include "display.h"
#include "startup.h"
#include "upcall.h"

void atl_window_size(int *width, int *height)
{
	*width = atl_window_width;
	*height = atl_window_height;
}

void atl_upcall_looper_loop(void)
{
	JNIEnv *env = get_jni_env();
	(*env)->CallStaticVoidMethod(env, handle_cache.looper.class, handle_cache.looper.loop);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

void atl_upcall_display_changed(const struct ui_display *display)
{
	atl_display_apply(get_jni_env(), NULL, display);
}

struct atl_ref *atl_ref_dup(struct atl_ref *ref)
{
	JNIEnv *env = get_jni_env();
	return (struct atl_ref *)_WEAK_REF((jobject)ref);
}

void atl_ref_free(struct atl_ref *ref)
{
	JNIEnv *env = get_jni_env();
	_WEAK_UNREF((jobject)ref);
}

void atl_upcall_view_measure(struct atl_ref *view, int width_spec, int height_spec)
{
	JNIEnv *env = get_jni_env();
	(*env)->CallVoidMethod(env, (jobject)view, handle_cache.view.measure, width_spec, height_spec);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
}

int atl_upcall_view_get_measured(struct atl_ref *view, bool vertical)
{
	JNIEnv *env = get_jni_env();
	return (*env)->CallIntMethod(env, (jobject)view, vertical ? handle_cache.view.getMeasuredHeight : handle_cache.view.getMeasuredWidth);
}

int atl_upcall_view_get_suggested_min(struct atl_ref *view, bool vertical)
{
	JNIEnv *env = get_jni_env();
	return (*env)->CallIntMethod(env, (jobject)view, vertical ? handle_cache.view.getSuggestedMinimumHeight : handle_cache.view.getSuggestedMinimumWidth);
}

void atl_upcall_view_get_old_specs(struct atl_ref *view, int *width_spec, int *height_spec)
{
	JNIEnv *env = get_jni_env();
	*width_spec = _GET_INT_FIELD((jobject)view, "oldWidthMeasureSpec");
	*height_spec = _GET_INT_FIELD((jobject)view, "oldHeightMeasureSpec");
}

void atl_upcall_view_layout(struct atl_ref *view, int width, int height)
{
	JNIEnv *env = get_jni_env();
	(*env)->CallVoidMethod(env, (jobject)view, handle_cache.view.layoutInternal, width, height);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

void atl_upcall_window_ready(void)
{
	activity_window_ready();
}
