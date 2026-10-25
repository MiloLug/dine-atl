#include <stddef.h>
#include <stdlib.h>

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

// a weak view ref for a call that takes no event: each frees its local refs
static jobject view_local(JNIEnv *env, struct atl_ref *view)
{
	return (*env)->NewLocalRef(env, (jobject)view);
}

static void clear_exception(JNIEnv *env)
{
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
}

void atl_upcall_edit_report(struct atl_ref *view, const struct atl_edit_report *report)
{
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 8);
	jobject view_obj = view_local(env, view);
	if (view_obj) {
		(*env)->CallVoidMethod(env, view_obj, handle_cache.edit_text.atlEdited, utf8_to_jstring(env, report->prev), report->prev_start, report->prev_end,
		                       utf8_to_jstring(env, report->text), report->start, report->end, (jint)report->seq);
		clear_exception(env);
	}
	(*env)->PopLocalFrame(env, NULL);
}

void atl_upcall_edit_action(struct atl_ref *view)
{
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 4);
	jobject view_obj = view_local(env, view);
	if (view_obj) {
		(*env)->CallVoidMethod(env, view_obj, handle_cache.edit_text.atlEditorAction);
		clear_exception(env);
	}
	(*env)->PopLocalFrame(env, NULL);
}

void atl_upcall_edit_focus(struct atl_ref *view, bool focused, bool keyboard)
{
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 4);
	jobject view_obj = view_local(env, view);
	if (view_obj) {
		(*env)->CallVoidMethod(env, view_obj, handle_cache.edit_text.atlToolkitFocus, focused, keyboard);
		clear_exception(env);
	}
	(*env)->PopLocalFrame(env, NULL);
}

// a sequence's lead byte: 4-byte sequences are a surrogate pair in UTF-16
static int utf8_units(unsigned char lead)
{
	return lead >= 0xF0 ? 2 : 1;
}

static int utf8_size(unsigned char lead)
{
	return lead >= 0xF0 ? 4 : lead >= 0xE0 ? 3 : lead >= 0xC0 ? 2 : 1;
}

size_t atl_utf8_offset(const char *text, int utf16)
{
	size_t offset = 0;
	while (utf16 > 0 && text[offset]) {
		utf16 -= utf8_units(text[offset]);
		offset += utf8_size(text[offset]);
	}
	return offset;
}

int atl_utf16_length(const char *text, size_t bytes)
{
	int length = 0;
	for (size_t offset = 0; offset < bytes && text[offset]; offset += utf8_size(text[offset]))
		length += utf8_units(text[offset]);
	return length;
}

uint16_t *atl_utf16_from_utf8(const char *text, size_t bytes, size_t *len)
{
	uint16_t *units = malloc((bytes ?: 1) * sizeof(*units));
	*len = 0;
	for (size_t offset = 0; offset < bytes && text[offset];) {
		const unsigned char *bytes_at = (const unsigned char *)text + offset;
		int size = utf8_size(*bytes_at);
		uint32_t c = size == 1 ? *bytes_at : *bytes_at & (0x7F >> size);
		for (int i = 1; i < size; i++)
			c = c << 6 | (bytes_at[i] & 0x3F);
		if (c >= 0x10000) {
			units[(*len)++] = 0xD800 + ((c - 0x10000) >> 10);
			units[(*len)++] = 0xDC00 + ((c - 0x10000) & 0x3FF);
		} else {
			units[(*len)++] = c;
		}
		offset += size;
	}
	return units;
}
