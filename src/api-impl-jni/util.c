#include <dlfcn.h>
#include <inttypes.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>

#include "src/api-impl-jni/defines.h"
#include "util.h"

const char *attribute_set_get_string(JNIEnv *env, jobject attrs, char *attribute, char *schema)
{
	if (!attrs)
		return NULL;

	if (!schema)
		schema = "http://schemas.android.com/apk/res/android";

	jstring string = (jstring)(*env)->CallObjectMethod(env, attrs, handle_cache.attribute_set.getAttributeValue_string, _JSTRING(schema), _JSTRING(attribute));
	return string ? _CSTRING(string) : NULL;
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
