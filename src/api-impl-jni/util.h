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
/* Standard UTF-8, which NewStringUTF's Modified UTF-8 isn't for a character beyond the BMP. */
jstring utf8_to_jstring(JNIEnv *env, const char *utf8);
/* The attribute's text through jstring_to_utf8; free() it. */
char *attribute_set_get_string(JNIEnv *env, jobject attrs, char *attribute, char *schema);
int attribute_set_get_int(JNIEnv *env, jobject attrs, char *attribute, char *schema, int default_value);
struct atl_app;
struct loop_main;
/* Files of the app's APKs into its data dir: AssetManager.extractFromAPK. */
void extract_from_apk(struct atl_app *app, const char *path, const char *target);

/* $DINE_PREFIX or ~/.dine, opened by atl_open_prefix */
char *get_prefix_dir(void);

struct loop_main *prepare_main_looper(JNIEnv *env);

/* For an exception thrown into C code where Android's main loop would see it: the thread's uncaught
 * handler gets it and the process ends, as an Android app's does. Returns only when none is pending. */
void exit_on_exception(JNIEnv *env);

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
