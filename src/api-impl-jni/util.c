#include <dlfcn.h>
#include <inttypes.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/api-impl-jni/defines.h"
#include "util.h"

char *jstring_to_utf8(JNIEnv *env, jstring string)
{
	if (!string)
		return NULL;
	jsize length = (*env)->GetStringLength(env, string);
	const jchar *chars = (*env)->GetStringChars(env, string, NULL);
	if (!chars)
		return NULL;
	char *utf8 = malloc((size_t)length * 3 + 1); // a pair takes 4 bytes for 2 chars, any other char at most 3
	if (!utf8) {
		(*env)->ReleaseStringChars(env, string, chars);
		return NULL;
	}
	char *out = utf8;
	for (jsize i = 0; i < length; i++) {
		uint32_t c = chars[i];
		if (c >= 0xd800 && c <= 0xdbff && i + 1 < length && chars[i + 1] >= 0xdc00 && chars[i + 1] <= 0xdfff)
			c = 0x10000 + ((c - 0xd800) << 10) + (chars[++i] - 0xdc00);
		else if (c == 0 || (c >= 0xd800 && c <= 0xdfff))
			c = 0xfffd;

		if (c < 0x80) {
			*out++ = c;
		} else if (c < 0x800) {
			*out++ = 0xc0 | c >> 6;
			*out++ = 0x80 | (c & 0x3f);
		} else if (c < 0x10000) {
			*out++ = 0xe0 | c >> 12;
			*out++ = 0x80 | (c >> 6 & 0x3f);
			*out++ = 0x80 | (c & 0x3f);
		} else {
			*out++ = 0xf0 | c >> 18;
			*out++ = 0x80 | (c >> 12 & 0x3f);
			*out++ = 0x80 | (c >> 6 & 0x3f);
			*out++ = 0x80 | (c & 0x3f);
		}
	}
	*out = '\0';
	(*env)->ReleaseStringChars(env, string, chars);
	return utf8;
}

jstring utf8_to_jstring(JNIEnv *env, const char *utf8)
{
	size_t size = strlen(utf8);
	jchar *chars = malloc((size + 1) * sizeof(*chars)); // no more units than bytes
	jsize length = 0;
	const unsigned char *in = (const unsigned char *)utf8;
	while (*in) {
		uint32_t c = *in++;
		int more = c >= 0xf0 ? 3 : c >= 0xe0 ? 2 : c >= 0xc0 ? 1 : 0;
		if (more)
			c &= 0x3f >> more;
		for (; more && (*in & 0xc0) == 0x80; more--)
			c = c << 6 | (*in++ & 0x3f);
		if (more || c > 0x10ffff || (c >= 0xd800 && c <= 0xdfff))
			c = 0xfffd;
		if (c >= 0x10000) {
			chars[length++] = 0xd800 + ((c - 0x10000) >> 10);
			chars[length++] = 0xdc00 + ((c - 0x10000) & 0x3ff);
		} else {
			chars[length++] = c;
		}
	}
	jstring string = (*env)->NewString(env, chars, length);
	free(chars);
	return string;
}

char *attribute_set_get_string(JNIEnv *env, jobject attrs, char *attribute, char *schema)
{
	if (!attrs)
		return NULL;

	if (!schema)
		schema = "http://schemas.android.com/apk/res/android";

	jstring string = (jstring)(*env)->CallObjectMethod(env, attrs, handle_cache.attribute_set.getAttributeValue_string, _JSTRING(schema), _JSTRING(attribute));
	return jstring_to_utf8(env, string);
}

int attribute_set_get_int(JNIEnv *env, jobject attrs, char *attribute, char *schema, int default_value)
{
	if (!attrs)
		return default_value;

	if (!schema)
		schema = "http://schemas.android.com/apk/res/android";

	return (*env)->CallIntMethod(env, attrs, handle_cache.attribute_set.getAttributeValue_int, _JSTRING(schema), _JSTRING(attribute), default_value);
}

JavaVM *jvm;

// TODO: use this everywhere, not just for gdb helper functions
JNIEnv *get_jni_env(void)
{
	JNIEnv *env;
	(*jvm)->GetEnv(jvm, (void **)&env, JNI_VERSION_1_6);
	return env;
}

JNIEnv *_gdb_get_jni_env(void)
{
	return get_jni_env();
}

void _gdb_get_java_stack_trace(void)
{
	JNIEnv *env = get_jni_env();
	(*env)->ExceptionDescribe(env);
}

void _gdb_force_java_stack_trace(void)
{
	JNIEnv *env = get_jni_env();
	(*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/Exception"), "forced stack trace");
	(*env)->ExceptionDescribe(env);
	(*env)->ExceptionClear(env);
}

void extract_from_apk(const char *path, const char *target)
{
	JNIEnv *env = get_jni_env();
	(*env)->CallStaticVoidMethod(env, handle_cache.asset_manager.class, handle_cache.asset_manager.extractFromAPK, _JSTRING(apk_path), _JSTRING(path), _JSTRING(target));
}

/* logging with fallback to stderr */

typedef int __android_log_vprint_type(int prio, const char *tag, const char *fmt, va_list ap);

static int fallback_verbose_log(int prio, const char *tag, const char *fmt, va_list ap)
{
	int ret;

	static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
	pthread_mutex_lock(&mutex);
	static char buf[1024];
	ret = vsnprintf(buf, sizeof(buf), fmt, ap);
	fprintf(stderr, "%" PRIu64 ": %s\n", (uint64_t)pthread_self(), buf);
	pthread_mutex_unlock(&mutex);

	return ret;
}

static int android_log_vprintf(int prio, const char *tag, const char *fmt, va_list ap)
{

	static __android_log_vprint_type *_android_log_vprintf = NULL;
	if (!_android_log_vprintf) {
		_android_log_vprintf = dlsym(RTLD_DEFAULT, "__android_log_vprint");

		if (!_android_log_vprintf) {
			_android_log_vprintf = &fallback_verbose_log;
		}
	}

	return _android_log_vprintf(prio, tag, fmt, ap);
}

int android_log_printf(android_LogPriority prio, const char *tag, const char *fmt, ...)
{
	int ret;

	va_list ap;
	va_start(ap, fmt);

	ret = android_log_vprintf(prio, tag, fmt, ap);

	va_end(ap);

	return ret;
}

void *get_nio_buffer(JNIEnv *env, jobject buffer, jarray *array_ref, jbyte **array)
{
	jclass class;
	void *pointer;
	int elementSizeShift, position;

	if (!buffer) {
		*array_ref = NULL;
		return NULL;
	}
	class = _CLASS(buffer);
	pointer = _PTR((*env)->GetLongField(env, buffer, _FIELD_ID(class, "address", "J")));
	elementSizeShift = (*env)->GetIntField(env, buffer, _FIELD_ID(class, "_elementSizeShift", "I"));
	position = (*env)->GetIntField(env, buffer, _FIELD_ID(class, "position", "I"));
	if (pointer) { // buffer is direct
		*array_ref = NULL;
		pointer += position << elementSizeShift;
	} else { // buffer is indirect
		*array_ref = (*env)->CallObjectMethod(env, buffer, _METHOD(class, "array", "()Ljava/lang/Object;"));
		jint offset = (*env)->CallIntMethod(env, buffer, _METHOD(class, "arrayOffset", "()I"));
		pointer = *array = (*env)->GetPrimitiveArrayCritical(env, *array_ref, NULL);
		pointer += (offset + position) << elementSizeShift;
	}
	return pointer;
}

void release_nio_buffer(JNIEnv *env, jarray array_ref, jbyte *array)
{
	if (array_ref)
		(*env)->ReleasePrimitiveArrayCritical(env, array_ref, array, 0);
}

int get_nio_buffer_size(JNIEnv *env, jobject buffer)
{
	jclass class = _CLASS(buffer);
	;
	int limit = (*env)->GetIntField(env, buffer, _FIELD_ID(class, "limit", "I"));
	int position = (*env)->GetIntField(env, buffer, _FIELD_ID(class, "position", "I"));

	return limit - position;
}
