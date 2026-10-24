#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_widget_EditText.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/ui.h"

#include "view.h"

static struct ui_view *edit_view(JNIEnv *env, jobject this)
{
	return _PTR(_GET_LONG_FIELD(this, "widget"));
}

JNIEXPORT jlong JNICALL Java_android_widget_EditText_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	struct ui_view_traits traits;
	atl_view_traits_get(env, this, &traits);
	char *name = atl_view_class_name(env, this);
	struct ui_view *view = ui_edit_new((struct atl_ref *)_WEAK_REF(this), name, &traits);
	free(name);
	return _INTPTR(view);
}

JNIEXPORT void JNICALL Java_android_widget_EditText_native_1setConfig(JNIEnv *env, jobject this, jint keyboard, jint return_key, jint autocap,
                                                                     jboolean secure, jboolean multiline, jboolean autocorrect, jstring hint)
{
	char *hint_utf8 = jstring_to_utf8(env, hint);
	ui_edit_set_config(edit_view(env, this), &(struct ui_edit_config){
		.keyboard = keyboard,
		.return_key = return_key,
		.autocap = autocap,
		.secure = secure,
		.multiline = multiline,
		.autocorrect = autocorrect,
		.hint = hint_utf8,
	});
	free(hint_utf8);
}

JNIEXPORT void JNICALL Java_android_widget_EditText_native_1setState(JNIEnv *env, jobject this, jstring text, jint start, jint end, jint ack)
{
	char *utf8 = jstring_to_utf8(env, text);
	ui_edit_set_state(edit_view(env, this), utf8 ?: "", start, end, (uint32_t)ack);
	free(utf8);
}

JNIEXPORT jboolean JNICALL Java_android_widget_EditText_native_1setKeyboard(JNIEnv *env, jobject this, jboolean shown)
{
	return ui_edit_set_keyboard(edit_view(env, this), shown);
}
