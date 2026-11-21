#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_widget_EditText.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/ui.h"

#include "view.h"

_Static_assert(UI_KEYBOARD_TEXT == android_widget_EditText_KEYBOARD_TEXT && UI_KEYBOARD_NUMBER == android_widget_EditText_KEYBOARD_NUMBER
               && UI_KEYBOARD_DECIMAL == android_widget_EditText_KEYBOARD_DECIMAL && UI_KEYBOARD_PHONE == android_widget_EditText_KEYBOARD_PHONE
               && UI_KEYBOARD_EMAIL == android_widget_EditText_KEYBOARD_EMAIL && UI_KEYBOARD_URL == android_widget_EditText_KEYBOARD_URL
               && UI_KEYBOARD_DATETIME == android_widget_EditText_KEYBOARD_DATETIME,
               "EditText's KEYBOARD_* are the slot's ui_keyboard");
_Static_assert(UI_RETURN_DEFAULT == android_widget_EditText_RETURN_DEFAULT && UI_RETURN_DONE == android_widget_EditText_RETURN_DONE
               && UI_RETURN_GO == android_widget_EditText_RETURN_GO && UI_RETURN_SEARCH == android_widget_EditText_RETURN_SEARCH
               && UI_RETURN_NEXT == android_widget_EditText_RETURN_NEXT && UI_RETURN_SEND == android_widget_EditText_RETURN_SEND,
               "EditText's RETURN_* are the slot's ui_return_key");
_Static_assert(UI_AUTOCAP_NONE == android_widget_EditText_AUTOCAP_NONE && UI_AUTOCAP_WORDS == android_widget_EditText_AUTOCAP_WORDS
               && UI_AUTOCAP_SENTENCES == android_widget_EditText_AUTOCAP_SENTENCES && UI_AUTOCAP_CHARACTERS == android_widget_EditText_AUTOCAP_CHARACTERS,
               "EditText's AUTOCAP_* are the slot's ui_autocap");

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
                                                                     jboolean secure, jboolean hidden, jboolean multiline, jboolean autocorrect, jboolean tap_focus,
                                                                     jboolean tap_keyboard, jboolean editable, jstring hint)
{
	char *hint_utf8 = jstring_to_utf8(env, hint);
	ui_edit_set_config(edit_view(env, this), &(struct ui_edit_config){
		.keyboard = keyboard,
		.return_key = return_key,
		.autocap = autocap,
		.secure = secure,
		.hidden = hidden,
		.multiline = multiline,
		.autocorrect = autocorrect,
		.tap_focus = tap_focus,
		.tap_keyboard = tap_keyboard,
		.editable = editable,
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
