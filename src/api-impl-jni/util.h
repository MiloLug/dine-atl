#ifndef _UTILS_H_
#define _UTILS_H_

#include <jni.h>

#include "defines.h"
#include "handle_cache.h"

extern JavaVM *jvm;

JNIEnv *get_jni_env(void);

/* Text for a ui slot, NULL for NULL; free() it. Valid UTF-8, unlike GetStringUTFChars' modified
 * UTF-8: U+0000 and lone surrogates become U+FFFD. */
char *jstring_to_utf8(JNIEnv *env, jstring string);
/* The attribute's text through jstring_to_utf8; free() it. */
char *attribute_set_get_string(JNIEnv *env, jobject attrs, char *attribute, char *schema);
int attribute_set_get_int(JNIEnv *env, jobject attrs, char *attribute, char *schema, int default_value);
void extract_from_apk(const char *path, const char *target);

extern char *apk_path;
extern char *app_data_dir;
char *get_app_data_dir(void);

void prepare_main_looper(JNIEnv *env);

/* we don't (currently?) install the headers for liblog */
typedef enum {
	LOG_ID_MAIN = 0,
	LOG_ID_RADIO = 1,
	LOG_ID_EVENTS = 2,
	LOG_ID_SYSTEM = 3,

	LOG_ID_MAX
} log_id_t;

typedef enum android_LogPriority {
	ANDROID_LOG_UNKNOWN = 0,
	ANDROID_LOG_DEFAULT, /* only for SetMinPriority() */
	ANDROID_LOG_VERBOSE,
	ANDROID_LOG_DEBUG,
	ANDROID_LOG_INFO,
	ANDROID_LOG_WARN,
	ANDROID_LOG_ERROR,
	ANDROID_LOG_FATAL,
	ANDROID_LOG_SILENT, /* only for SetMinPriority(); must be last */
} android_LogPriority;

/* TODO: do we really need the bufID, or can we use our function below which has a stderr fallback */
int __android_log_buf_write(int bufID, int prio, const char *tag, const char *text);

/* defined in util.c */
int android_log_printf(android_LogPriority prio, const char *tag, const char *fmt, ...);

void *get_nio_buffer(JNIEnv *env, jobject buffer, jarray *array_ref, jbyte **array);
void release_nio_buffer(JNIEnv *env, jarray array_ref, jbyte *array);
int get_nio_buffer_size(JNIEnv *env, jobject buffer);

#endif
