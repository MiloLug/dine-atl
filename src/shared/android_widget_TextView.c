#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_widget_TextView.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/ui.h"

#include "view.h"

static struct ui_view *text_view(JNIEnv *env, jobject this)
{
	return _PTR(_GET_LONG_FIELD(this, "widget"));
}

JNIEXPORT jlong JNICALL Java_android_widget_TextView_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	char *text = attribute_set_get_string(env, attrs, "text", NULL);

	struct ui_view_traits traits;
	atl_view_traits_get(env, this, &traits);
	char *name = atl_view_class_name(env, this);
	struct ui_view *view = ui_text_new((struct atl_ref *)_WEAK_REF(this), name, &traits, text);
	free(name);
	free(text);

	return _INTPTR(view);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setText(JNIEnv *env, jobject this, jobject charseq)
{
	char *text = jstring_to_utf8(env, charseq);
	ui_text_set_text(text_view(env, this), text ?: "");
	free(text);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setTextColor(JNIEnv *env, jobject this, jint color)
{
	ui_text_set_color(text_view(env, this), color);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setTextSize(JNIEnv *env, jobject this, jfloat size)
{
	ui_text_set_size(text_view(env, this), size);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setTextStyle(JNIEnv *env, jobject this, jint style)
{
	ui_text_set_style(text_view(env, this), style);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1set_1markup(JNIEnv *env, jobject this, jint markup)
{
	ui_text_set_markup(_PTR(_GET_LONG_FIELD(this, "widget")), markup);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setCompoundDrawables(JNIEnv *env, jobject this, jlong widget_ptr, jlong left, jlong top, jlong right, jlong bottom)
{
	ui_text_set_drawables(_PTR(widget_ptr), _PTR(left), _PTR(top), _PTR(right), _PTR(bottom));
}
