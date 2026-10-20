#include <stdlib.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_app_AlertDialog.h"
#include "src/api-impl-jni/generated_headers/android_app_Dialog.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "test_hooks.h"

_Static_assert(UI_DIALOG_CANCEL == android_app_Dialog_RESULT_CANCEL, "Dialog.RESULT_CANCEL is the slot's cancel");

JNIEXPORT jlong JNICALL Java_android_app_Dialog_nativeInit(JNIEnv *env, jobject this, jlong decor_view)
{
	return _INTPTR(ui_dialog_new((struct atl_ref *)_WEAK_REF(this), _PTR(decor_view)));
}

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeSetTitle(JNIEnv *env, jobject this, jlong ptr, jstring title_jstr)
{
	char *title = jstring_to_utf8(env, title_jstr);
	ui_window_set_title(_PTR(ptr), title);
	free(title);
}

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeShow(JNIEnv *env, jobject this, jlong ptr)
{
	ui_dialog_show(_PTR(ptr), NULL);
}

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeClose(JNIEnv *env, jobject this, jlong ptr)
{
	ui_dialog_close(_PTR(ptr));
}

static void dialog_free(void *dialog)
{
	atl_test_dialog_freed();
	ui_dialog_free(dialog);
}

// finalize calls it on the FinalizerDaemon thread; after loop_main_quit the dialog stays allocated
JNIEXPORT void JNICALL Java_android_app_Dialog_nativeDestroy(JNIEnv *env, jobject this, jlong ptr)
{
	loop_main_post(dialog_free, _PTR(ptr));
}

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeSetCanceledOnTouchOutside(JNIEnv *env, jobject this, jlong ptr, jboolean cancel)
{
	ui_dialog_set_cancel_on_touch_outside(_PTR(ptr), cancel);
}

JNIEXPORT jboolean JNICALL Java_android_app_Dialog_nativeTaskFront(JNIEnv *env, jclass class)
{
	return activity_task_front();
}

// array elements as strings, NULL for a NULL array; free with free_array
static char **get_array(JNIEnv *env, jobjectArray array, int *length)
{
	*length = array ? (*env)->GetArrayLength(env, array) : 0;
	if (!array)
		return NULL;
	char **chars = calloc(*length, sizeof(*chars));
	for (int i = 0; i < *length; i++) {
		jstring string = (*env)->GetObjectArrayElement(env, array, i);
		chars[i] = jstring_to_utf8(env, string);
		(*env)->DeleteLocalRef(env, string);
	}
	return chars;
}

static void free_array(char **chars, int length)
{
	for (int i = 0; i < length; i++)
		free(chars[i]);
	free(chars);
}

JNIEXPORT void JNICALL Java_android_app_AlertDialog_nativeShowAlert(JNIEnv *env, jobject this, jlong ptr, jstring title, jstring message,
                                                                    jobjectArray items, jobjectArray buttons, jboolean cancelable)
{
	int n_items, n_buttons;
	char **item_chars = get_array(env, items, &n_items);
	char **button_chars = get_array(env, buttons, &n_buttons);
	char *title_chars = jstring_to_utf8(env, title);
	char *message_chars = jstring_to_utf8(env, message);
	struct ui_alert alert = {
		.title = title_chars,
		.message = message_chars,
		.items = (const char *const *)item_chars,
		.n_items = n_items,
		.cancelable = cancelable,
	};
	for (int i = 0; i < n_buttons && i < 3; i++)
		alert.buttons[i] = button_chars[i];

	ui_dialog_show(_PTR(ptr), &alert);

	free(title_chars);
	free(message_chars);
	free_array(item_chars, n_items);
	free_array(button_chars, n_buttons);
}
