#ifndef SHARED_STARTUP_H
#define SHARED_STARTUP_H

#include <jni.h>
#include <stdbool.h>

/* Start phases that every host runs, in this order, with host-specific steps between them. */

struct ui_window;

struct atl_paths {
	char *apk_classpath; // the apks joined with ':', the main apk first
	char *data_dir_base; // ANDROID_APP_DATA_DIR, or android_translation_layer in the XDG data dir
	char *app_lib_dir;   // <app data dir>/lib
	char *api_impl_jar;
	char *framework_res_apk;
	char *test_runner_jar; // NULL unless instrumenting
	char *natives_dir;
};

#define ATL_WINDOW_WIDTH_DEFAULT  960
#define ATL_WINDOW_HEIGHT_DEFAULT 540

/* Set by atl_vm_start and atl_app_create. */
extern struct ui_window *atl_window;
extern int atl_window_width;
extern int atl_window_height;

/* Sets the apk class path and creates the data dirs; sets apk_path and app_data_dir.
 * -1 on error, after printing it. */
int atl_set_app(struct atl_paths *paths, char *const *apks, int napks);
/* atl_set_app plus the jars of a Linux install or build dir. Exits on error. */
void atl_prepare(struct atl_paths *paths, char *const *apks, int napks, bool instrument);
/* ATL's options for JNI_CreateJavaVM, without VM-wide ones such as -Xcheck:jni; NULL on error.
 * Free with atl_vm_options_free once the VM exists. */
JavaVMOption *atl_vm_options(const struct atl_paths *paths, const char *sdk_int, int *count);
void atl_vm_options_free(JavaVMOption *options, int count);
/* Creates the VM, then atl_vm_init. Exits on error. */
JNIEnv *atl_vm_start(const struct atl_paths *paths, const char *sdk_int, char *const *extra_jvm_options, int window_width, int window_height);
/* On a VM created with atl_vm_options: loads translation_layer_main and fills the handle cache.
 * -1 on error. */
int atl_vm_init(JNIEnv *env, int window_width, int window_height);
/* Main looper, Application, content providers, Application.onCreate. */
jobject atl_app_create(JNIEnv *env, struct ui_window *window);
jobject atl_activity_create_main(JNIEnv *env, const char *activity_class, const char *uri);
/* System.exit(status): shuts the VM down and exits the process. */
void atl_exit(JNIEnv *env, int status);

#endif
