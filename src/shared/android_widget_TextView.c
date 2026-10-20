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

	jclass class = _CLASS(this);
	jstring name_jstr = (*env)->CallObjectMethod(env, class, _METHOD(_CLASS(class), "getName", "()Ljava/lang/String;"));
	const char *name = (*env)->GetStringUTFChars(env, name_jstr, NULL);
	struct ui_view *view = ui_text_new((struct atl_ref *)_WEAK_REF(this), name, &traits, text);
	(*env)->ReleaseStringUTFChars(env, name_jstr, name);
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

JNIEXPORT void JNICALL Java_android_widget_TextView_setTextSize(JNIEnv *env, jobject this, jfloat size)
{
	ui_text_set_size(text_view(env, this), size);
}
