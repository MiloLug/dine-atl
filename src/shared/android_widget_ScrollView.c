#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_widget_HorizontalScrollView.h"
#include "src/api-impl-jni/generated_headers/android_widget_ScrollView.h"
#include "src/slots/ui.h"

#include "view.h"

static jlong scroll_new(JNIEnv *env, jobject this, bool horizontal)
{
	struct ui_view_traits traits;
	atl_view_traits_get(env, this, &traits);
	char *name = atl_view_class_name(env, this);
	struct ui_view *view = ui_scroll_new((struct atl_ref *)_WEAK_REF(this), name, &traits, horizontal);
	free(name);
	return _INTPTR(view);
}

JNIEXPORT jlong JNICALL Java_android_widget_ScrollView_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	return scroll_new(env, this, false);
}

JNIEXPORT jlong JNICALL Java_android_widget_HorizontalScrollView_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	return scroll_new(env, this, true);
}
