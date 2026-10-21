#include <stddef.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/util.h"

#include "input.h"

#define SOURCE_TOUCHSCREEN  0x1002
#define SOURCE_KEYBOARD     0x101
#define VIRTUAL_KEYBOARD    (-1)

/* Backends call these from posted code or toolkit callbacks, in the Android thread's base JNI
 * frame, which never pops: each frees its local refs. */

static jobject motion_event_new(JNIEnv *env, const struct atl_touch *touch)
{
	jint ids[ATL_POINTERS_MAX];
	jfloat coords[ATL_POINTERS_MAX * 4]; // x y raw_x raw_y; the window's origin is the screen's
	for (int i = 0; i < touch->count; i++) {
		ids[i] = touch->pointers[i].id;
		coords[i * 4] = coords[i * 4 + 2] = touch->pointers[i].x;
		coords[i * 4 + 1] = coords[i * 4 + 3] = touch->pointers[i].y;
	}
	jintArray ids_array = (*env)->NewIntArray(env, touch->count);
	jfloatArray coords_array = (*env)->NewFloatArray(env, touch->count * 4);
	(*env)->SetIntArrayRegion(env, ids_array, 0, touch->count, ids);
	(*env)->SetFloatArrayRegion(env, coords_array, 0, touch->count * 4, coords);
	return (*env)->NewObject(env, handle_cache.motion_event.class, handle_cache.motion_event.constructor, SOURCE_TOUCHSCREEN,
	                         touch->action, (jlong)touch->down_time, (jlong)touch->time, ids_array, coords_array);
}

static bool call_entry(JNIEnv *env, jmethodID entry, jobject root, jobject event)
{
	jboolean handled = (*env)->CallStaticBooleanMethod(env, handle_cache.atl_input.class, entry, root, event);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
		return false;
	}
	return handled;
}

bool atl_upcall_touch(struct atl_ref *root, const struct atl_touch *touch)
{
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 8);
	jobject root_obj = (*env)->NewLocalRef(env, (jobject)root);
	bool handled = root_obj && call_entry(env, handle_cache.atl_input.touch, root_obj, motion_event_new(env, touch));
	(*env)->PopLocalFrame(env, NULL);
	return handled;
}

// the character Android's Generic.kcm gives: '\n' for Enter, none for the other control keys but Tab
static int key_character(int unicode)
{
	if (unicode == '\r')
		return '\n';
	if ((unicode < 0x20 && unicode != '\t' && unicode != '\n') || unicode == 0x7f)
		return 0;
	return unicode;
}

bool atl_upcall_key(struct atl_ref *root, const struct atl_key *key)
{
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 8);
	jobject root_obj = (*env)->NewLocalRef(env, (jobject)root);
	bool handled = false;
	if (root_obj) {
		jobject event = (*env)->NewObject(env, handle_cache.key_event.class, handle_cache.key_event.constructor_full, (jlong)key->down_time,
		                                  (jlong)key->time, key->action, key->code, key->repeat, key->meta, VIRTUAL_KEYBOARD, 0, 0, SOURCE_KEYBOARD);
		_SET_INT_FIELD(event, "unicodeValue", key_character(key->unicode));
		handled = call_entry(env, handle_cache.atl_input.key, root_obj, event);
	}
	(*env)->PopLocalFrame(env, NULL);
	return handled;
}

void atl_upcall_scrolled(struct atl_ref *view, int x, int y)
{
	JNIEnv *env = get_jni_env();
	jobject view_obj = (*env)->NewLocalRef(env, (jobject)view);
	if (!view_obj) // collected; its node goes with the finalizer's free
		return;
	(*env)->CallVoidMethod(env, view_obj, handle_cache.view.atlScrolled, x, y);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
	(*env)->DeleteLocalRef(env, view_obj);
}
