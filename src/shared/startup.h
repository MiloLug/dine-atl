#ifndef SHARED_STARTUP_H
#define SHARED_STARTUP_H

#include <jni.h>
#include <stdbool.h>

#include "upcall.h"

/* Start phases that every host runs, in this order, with host-specific steps between them. */

struct atl_app;
struct ui_window;

// the VM's: every app shares them
struct atl_paths {
	char *api_impl_jar;
	char *framework_res_apk;
	char *test_runner_jar; // NULL unless instrumenting
	char *natives_dir;
};
#define ATL_WINDOW_WIDTH_DEFAULT  960
#define ATL_WINDOW_HEIGHT_DEFAULT 540
#define ATL_DENSITY_DPI_DEFAULT   160

/* The screen, set by atl_vm_init (atl_screen_get). */
extern struct atl_window_config atl_screen;

/* Opens the prefix ($DINE_PREFIX, else ~/.dine). -1 on error, after printing it. */
int atl_open_prefix(void);
/* atl_open_prefix plus the jars of a Linux install or build dir. Exits on error. */
void atl_prepare(struct atl_paths *paths, bool instrument);
/* ATL's options for JNI_CreateJavaVM, without VM-wide ones such as -Xcheck:jni; NULL on error.
 * Free with atl_vm_options_free once the VM exists. */
JavaVMOption *atl_vm_options(const struct atl_paths *paths, int *count);
void atl_vm_options_free(JavaVMOption *options, int count);
/* Creates the VM, then atl_vm_init. Exits on error. */
JNIEnv *atl_vm_start(const struct atl_paths *paths, char *const *extra_jvm_options, const struct atl_window_config *window);
/* On a VM created with atl_vm_options: loads translation_layer_main and fills the handle cache.
 * -1 on error. */
int atl_vm_init(JNIEnv *env, const struct atl_window_config *window);
/* After atl_vm_init: a run by path (apks, the base first; installed unless the registry holds
 * them) or of an installed package; binds app to the package's record and makes its data and lib
 * dirs. -1 on error, also for a package another app handle runs in this VM. */
int atl_app_prepare(JNIEnv *env, struct atl_app *app, char *const *apks, int napks, const char *package);
/* On the thread that runs the app, which joins its thread group: main looper, then the record's
 * start (Application, content providers, Application.onCreate). */
jobject atl_app_create(JNIEnv *env, struct atl_app *app, struct ui_window *window);
jobject atl_activity_create_main(JNIEnv *env, struct atl_app *app, const char *activity_class, const char *uri);
/* The hosts' --install (the apks are one package, the base first) and --uninstall, on a VM started
 * without an app; they print the result and return the process's exit status. */
int atl_install(JNIEnv *env, char *const *apks, int napks, bool allow_fake_signature);
int atl_uninstall(JNIEnv *env, const char *package);
/* System.exit(status): shuts the VM down and exits the process. */
void atl_exit(JNIEnv *env, int status);

#endif
