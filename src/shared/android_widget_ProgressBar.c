#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_widget_ProgressBar.h"
#include "src/slots/ui.h"

#include "view.h"

JNIEXPORT jlong JNICALL Java_android_widget_ProgressBar_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	struct ui_view_traits traits;
	atl_view_traits_get(env, this, &traits);
	char *name = atl_view_class_name(env, this);
	struct ui_view *view = ui_progress_new((struct atl_ref *)_WEAK_REF(this), name, &traits);
	free(name);
	return _INTPTR(view);
}

JNIEXPORT void JNICALL Java_android_widget_ProgressBar_native_1setProgress(JNIEnv *env, jobject this, jlong widget_ptr, jfloat fraction)
{
	ui_progress_set_fraction(_PTR(widget_ptr), fraction);
}

JNIEXPORT void JNICALL Java_android_widget_ProgressBar_native_1setIndeterminate(JNIEnv *env, jobject this, jboolean indeterminate)
{
	ui_progress_set_indeterminate(_PTR(_GET_LONG_FIELD(this, "widget")), indeterminate);
}
