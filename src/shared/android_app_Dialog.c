#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_app_AlertDialog.h"
#include "src/api-impl-jni/generated_headers/android_app_Dialog.h"
#include "src/slots/ui.h"

_Static_assert(UI_DIALOG_CANCEL == android_app_Dialog_RESULT_CANCEL, "Dialog.RESULT_CANCEL is the slot's cancel");

JNIEXPORT jlong JNICALL Java_android_app_Dialog_nativeInit(JNIEnv *env, jobject this, jlong decor_view)
{
	return _INTPTR(ui_dialog_new((struct atl_ref *)_WEAK_REF(this), _PTR(decor_view)));
}

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeSetTitle(JNIEnv *env, jobject this, jlong ptr, jstring title_jstr)
{
	const char *title = (*env)->GetStringUTFChars(env, title_jstr, NULL);
	ui_window_set_title(_PTR(ptr), title);
	(*env)->ReleaseStringUTFChars(env, title_jstr, title);
}

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeShow(JNIEnv *env, jobject this, jlong ptr)
{
	ui_dialog_show(_PTR(ptr), NULL);
}

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeClose(JNIEnv *env, jobject this, jlong ptr)
{
	ui_dialog_close(_PTR(ptr));
}

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeSetCanceledOnTouchOutside(JNIEnv *env, jobject this, jlong ptr, jboolean cancel)
{
	ui_dialog_set_cancel_on_touch_outside(_PTR(ptr), cancel);
}

static const char *get_chars(JNIEnv *env, jstring string)
{
	return string ? (*env)->GetStringUTFChars(env, string, NULL) : NULL;
}

static void release_chars(JNIEnv *env, jstring string, const char *chars)
{
	if (string)
		(*env)->ReleaseStringUTFChars(env, string, chars);
}

// array elements as strings, NULL for a NULL array; free with release_array
static const char **get_array(JNIEnv *env, jobjectArray array, int *length)
{
	*length = array ? (*env)->GetArrayLength(env, array) : 0;
	if (!array)
		return NULL;
	const char **chars = calloc(*length, sizeof(*chars));
	for (int i = 0; i < *length; i++) {
		jstring string = (*env)->GetObjectArrayElement(env, array, i);
		chars[i] = get_chars(env, string);
		(*env)->DeleteLocalRef(env, string);
	}
	return chars;
}

static void release_array(JNIEnv *env, jobjectArray array, const char **chars, int length)
{
	for (int i = 0; i < length; i++) {
		jstring string = (*env)->GetObjectArrayElement(env, array, i);
		release_chars(env, string, chars[i]);
		(*env)->DeleteLocalRef(env, string);
	}
	free(chars);
}

JNIEXPORT void JNICALL Java_android_app_AlertDialog_nativeShowAlert(JNIEnv *env, jobject this, jlong ptr, jstring title, jstring message,
                                                                    jobjectArray items, jobjectArray buttons, jboolean cancelable)
{
	int n_items, n_buttons;
	const char **item_chars = get_array(env, items, &n_items);
	const char **button_chars = get_array(env, buttons, &n_buttons);
	struct ui_alert alert = {
		.title = get_chars(env, title),
		.message = get_chars(env, message),
		.items = item_chars,
		.n_items = n_items,
		.cancelable = cancelable,
	};
	for (int i = 0; i < n_buttons && i < 3; i++)
		alert.buttons[i] = button_chars[i];

	ui_dialog_show(_PTR(ptr), &alert);

	release_chars(env, title, alert.title);
	release_chars(env, message, alert.message);
	release_array(env, items, item_chars, n_items);
	release_array(env, buttons, button_chars, n_buttons);
}
