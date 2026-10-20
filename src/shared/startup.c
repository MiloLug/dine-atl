// for dladdr and asprintf
#define _GNU_SOURCE

#include <dlfcn.h>
#include <errno.h>
#include <libgen.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/util.h"

#include "startup.h"

#ifndef DEFFILEMODE
	#define DEFFILEMODE (S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH | S_IWOTH) /* 0666*/
#endif
#define DIR_MODE                          (DEFFILEMODE | S_IXUSR | S_IXGRP | S_IXOTH)

#define REL_DEX_INSTALL_PATH              "../java/dex"

#define REL_API_IMPL_JAR_INSTALL_PATH     "android_translation_layer/api-impl.jar"
#define REL_API_IMPL_NATIVES_INSTALL_PATH "android_translation_layer/natives"
#define REL_FRAMEWORK_RES_INSTALL_PATH    "android_translation_layer/framework-res.apk"
#define REL_TEST_RUNNER_JAR_INSTALL_PATH  "android_translation_layer/test_runner.jar"

#define API_IMPL_JAR_PATH_LOCAL           "./api-impl.jar"
#define FRAMEWORK_RES_PATH_LOCAL          "./res/framework-res/framework-res.apk"
#define TEST_RUNNER_JAR_PATH_LOCAL        "./test_runner.jar"

#define JDWP_ARG                          "-XjdwpOptions:transport=dt_socket,server=y,suspend=y,address="

char *apk_path;
// the equivalent of /data/data/com.example.app/
char *app_data_dir;

struct ui_window *atl_window;
struct atl_window_config atl_window_config;

char *get_app_data_dir(void)
{
	return app_data_dir;
}

static char *construct_classpath(char *prefix, char *const *cp_array, size_t len)
{
	size_t result_len = strlen(prefix) + 1;
	for (int i = 0; i < len; i++) {
		if (cp_array[i])
			result_len += strlen(cp_array[i]) + 1; // the 1 is for the :
	}

	char *result = malloc(result_len);
	strcpy(result, prefix);
	bool first = true;
	for (int i = 0; i < len; i++) {
		if (cp_array[i]) {
			if (!first)
				strcat(result, ":");
			strcat(result, cp_array[i]);
			first = false;
		}
	}

	return result;
}

static char *find_jar_or_die(char *builddir_path, char *installed_path, char *install_prefix)
{
	char *path;

	if (getenv("RUN_FROM_BUILDDIR")) {
		path = strdup(builddir_path); // for running out of builddir; using strdup so we can always safely call free on this
	} else if (asprintf(&path, "%s/%s", install_prefix, installed_path) < 0) {
		exit(1);
	}

	if (access(path, F_OK) < 0) {
		fprintf(stderr, "error: can't stat %s (%s)\n",
		        path, strerror(errno));
		exit(1);
	}

	return path;
}

// NULL on error
static char *data_dir_base(void)
{
	char *base = getenv("ANDROID_APP_DATA_DIR");
	if (base)
		return base;

	const char *xdg_data_home = getenv("XDG_DATA_HOME");
	const char *home = getenv("HOME");
	if (xdg_data_home && *xdg_data_home) {
		if (asprintf(&base, "%s/android_translation_layer", xdg_data_home) < 0)
			return NULL;
	} else if (home) {
		if (asprintf(&base, "%s/.local/share/android_translation_layer", home) < 0)
			return NULL;
	} else {
		fprintf(stderr, "error: ANDROID_APP_DATA_DIR not set, and HOME is not set either so we can't construct the default path\n");
		return NULL;
	}
	if (mkdir(base, DIR_MODE) && errno != EEXIST) {
		fprintf(stderr, "error: ANDROID_APP_DATA_DIR not set, and the default directory (%s) couldn't be created (error: %s)\n", base, strerror(errno));
		return NULL;
	}
	return base;
}

int atl_set_app(struct atl_paths *paths, char *const *apks, int napks)
{
	for (int i = 0; i < napks; i++) {
		if (access(apks[i], F_OK) < 0) {
			fprintf(stderr, "error: the specified file path (%s) doesn't seem to exist (%s)\n", apks[i], strerror(errno));
			return -1;
		}
	}
	paths->apk_classpath = construct_classpath("", apks, napks);
	// some apps need the apk path since they directly read their apk
	apk_path = strdup(paths->apk_classpath);

	paths->data_dir_base = data_dir_base();
	if (!paths->data_dir_base)
		return -1;

	// TODO: we should possibly use the app id instead, but we don't currently have a way to get that soon enough
	// arguably both the app id and the apk name might have an issue with duplicates, but if two apks use the same app id, chances are it's less of an issue than when two apks have the same name
	// !IMPORTANT! Unity can't comprehend that a directory name could end in .apk, so we have to avoid that here by adding `_`
	char *main_apk = strdup(apks[0]);
	int ret = asprintf(&app_data_dir, "%s/%s_/", paths->data_dir_base, basename(main_apk));
	free(main_apk);
	if (ret < 0)
		return -1;
	if (mkdir(app_data_dir, DIR_MODE) && errno != EEXIST) {
		fprintf(stderr, "can't create app data dir %s (%s)\n", app_data_dir, strerror(errno));
		return -1;
	}

	if (asprintf(&paths->app_lib_dir, "%s/lib", app_data_dir) < 0)
		return -1;
	mkdir(paths->app_lib_dir, DIR_MODE);
	return 0;
}

void atl_prepare(struct atl_paths *paths, char *const *apks, int napks, bool instrument)
{
	if (atl_set_app(paths, apks, napks) < 0)
		exit(1);

	Dl_info libart_so_dl_info;
	// JNI_CreateJavaVM chosen arbitrarily, what matters is that it's a symbol exported by by libart.so
	// TODO: we shouldn't necessarily count on art being installed in the same prefix as we are
	dladdr((void *)JNI_CreateJavaVM, &libart_so_dl_info);
	if (!libart_so_dl_info.dli_fname) {
		fprintf(stderr, "error: couldn't find art install path\n");
		exit(1);
	}
	char *libart_dir = strdup(libart_so_dl_info.dli_fname);
	char *dex_install_dir;
	if (asprintf(&dex_install_dir, "%s/%s", dirname(libart_dir), REL_DEX_INSTALL_PATH) < 0)
		exit(1);
	free(libart_dir);

	// check for jars and apks  in './' (if running from builddir), or in system install path
	paths->api_impl_jar = find_jar_or_die(API_IMPL_JAR_PATH_LOCAL, REL_API_IMPL_JAR_INSTALL_PATH, dex_install_dir);
	paths->framework_res_apk = find_jar_or_die(FRAMEWORK_RES_PATH_LOCAL, REL_FRAMEWORK_RES_INSTALL_PATH, dex_install_dir);
	paths->test_runner_jar = instrument ? find_jar_or_die(TEST_RUNNER_JAR_PATH_LOCAL, REL_TEST_RUNNER_JAR_INSTALL_PATH, dex_install_dir) : NULL;
	if (asprintf(&paths->natives_dir, "%s/%s", dex_install_dir, REL_API_IMPL_NATIVES_INSTALL_PATH) < 0)
		exit(1);
	free(dex_install_dir);
}

JavaVMOption *atl_vm_options(const struct atl_paths *paths, int *count)
{
	JavaVMOption *options = calloc(4, sizeof(*options));
	int n = 0;

	if (getenv("RUN_FROM_BUILDDIR")) {
		options[n++].optionString = construct_classpath("-Djava.library.path=", (char *[]){"./", paths->app_lib_dir}, 2);
	} else {
		options[n++].optionString = construct_classpath("-Djava.library.path=", (char *[]){paths->natives_dir, paths->app_lib_dir}, 2);
	}
	// ATLPaths relies on the first element in java.class.path being api-impl.jar
	options[n++].optionString = construct_classpath("-Djava.class.path=", (char *[]){paths->api_impl_jar, paths->framework_res_apk, paths->test_runner_jar}, 3);
	// ATLLoadedApp relies on the first element in atl.app.class.path being the main apk
	options[n++].optionString = construct_classpath("-Datl.app.class.path=", (char *[]){paths->apk_classpath}, 1);
	options[n++].optionString = construct_classpath("-Datl.app.library.path=", (char *[]){paths->natives_dir, paths->app_lib_dir}, 2);

	*count = n;
	return options;
}

void atl_vm_options_free(JavaVMOption *options, int count)
{
	for (int i = 0; i < count; i++)
		free((void *)options[i].optionString); // const in ART's jni.h
	free(options);
}

// the VM-wide options of the Linux hosts plus ATL's own
static JNIEnv *create_vm(const struct atl_paths *paths, char *const *extra_jvm_options)
{
	int n_atl;
	JavaVMOption *atl_options = atl_vm_options(paths, &n_atl);
	if (!atl_options)
		exit(1);

	const char *jdwp_port = getenv("JDWP_LISTEN");
	int n_extra = 0;
	for (char *const *option = extra_jvm_options; option && *option; option++)
		n_extra++;
	JavaVMOption *options = calloc(n_atl + 3 + n_extra, sizeof(*options));
	int n = 0;

	for (int i = 0; i < n_atl; i++)
		options[n++] = atl_options[i];
	options[n++].optionString = "-Xcheck:jni";
	char *jdwp_option = NULL;
	if (jdwp_port) {
		if (asprintf(&jdwp_option, JDWP_ARG "%s", jdwp_port) < 0)
			exit(1);
		options[n++].optionString = "-XjdwpProvider:internal";
		options[n++].optionString = jdwp_option;
	}
	for (char *const *option = extra_jvm_options; option && *option; option++)
		options[n++].optionString = *option;

	JavaVMInitArgs args = {
		.version = JNI_VERSION_1_6,
		.nOptions = n,
		.options = options,
		.ignoreUnrecognized = JNI_FALSE,
	};
	JavaVM *vm;
	JNIEnv *env;
	// void *: the JDK declares void **, ART JNIEnv **
	if (JNI_CreateJavaVM(&vm, (void *)&env, &args) < 0) {
		fprintf(stderr, "Unable to Launch JVM\n");
		exit(1);
	}
	fprintf(stderr, "JVM launched successfully\n");

	free(jdwp_option);
	free(options);
	atl_vm_options_free(atl_options, n_atl);
	return env;
}

JNIEnv *atl_vm_start(const struct atl_paths *paths, char *const *extra_jvm_options, const struct atl_window_config *window)
{
	JNIEnv *env = create_vm(paths, extra_jvm_options);
	if (atl_vm_init(env, window) < 0)
		exit(1);
	return env;
}

int atl_vm_init(JNIEnv *env, const struct atl_window_config *window)
{
	atl_window_config = *window;

	// DisplayMetrics reads density_dpi once, in <clinit>: set before set_up_handle_cache initializes classes
	jclass display_class = (*env)->FindClass(env, "android/view/Display");
	_SET_STATIC_INT_FIELD(display_class, "window_width", window->width);
	_SET_STATIC_INT_FIELD(display_class, "window_height", window->height);
	_SET_STATIC_INT_FIELD(display_class, "density_dpi", window->density_dpi);

	/* -- register our JNI library under the appropriate classloader -- */

	/* 'android/view/View' is part of the "hax.dex" package, any other function from that package would serve just as well */
	jmethodID getClassLoader = _METHOD((*env)->FindClass(env, "java/lang/Class"), "getClassLoader", "()Ljava/lang/ClassLoader;");
	jobject class_loader = (*env)->CallObjectMethod(env, (*env)->FindClass(env, "android/view/View"), getClassLoader);

	jclass java_runtime_class = (*env)->FindClass(env, "java/lang/Runtime");

	jmethodID getRuntime = _STATIC_METHOD(java_runtime_class, "getRuntime", "()Ljava/lang/Runtime;");
	jobject java_runtime = (*env)->CallStaticObjectMethod(env, java_runtime_class, getRuntime);

	/* this method is private, but it seems we get away with calling it from C */
	jmethodID loadLibrary_with_classloader = _METHOD(java_runtime_class, "loadLibrary", "(Ljava/lang/String;Ljava/lang/ClassLoader;)V");
	(*env)->CallVoidMethod(env, java_runtime, loadLibrary_with_classloader, _JSTRING("translation_layer_main"), class_loader);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		return -1;
	}

	(*env)->GetJavaVM(env, &jvm);
	set_up_handle_cache(env);
	return 0;
}

jobject atl_app_create(JNIEnv *env, struct ui_window *window)
{
	atl_window = window;

	prepare_main_looper(env);

	// construct Application
	jobject application_object = (*env)->CallStaticObjectMethod(env, handle_cache.context.class,
	                                                            _STATIC_METHOD(handle_cache.context.class, "createApplication", "(J)Landroid/app/Application;"), _INTPTR(window));
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);

	jclass content_provider = (*env)->FindClass(env, "android/content/ContentProvider");
	(*env)->CallStaticVoidMethod(env, content_provider, _STATIC_METHOD(content_provider, "createContentProviders", "()V"));
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);

	jmethodID on_create_method = _METHOD(handle_cache.application.class, "onCreate", "()V");
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	(*env)->CallVoidMethod(env, application_object, on_create_method);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);

	return application_object;
}

jobject atl_activity_create_main(JNIEnv *env, const char *activity_class, const char *uri)
{
	jobject activity = (*env)->CallStaticObjectMethod(env, handle_cache.activity.class,
	                                                  _STATIC_METHOD(handle_cache.activity.class, "createMainActivity", "(Ljava/lang/String;JLjava/lang/String;)Landroid/app/Activity;"),
	                                                  _JSTRING(activity_class), _INTPTR(atl_window), uri ? _JSTRING(uri) : NULL);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	return activity;
}

void atl_exit(JNIEnv *env, int status)
{
	jclass system = (*env)->FindClass(env, "java/lang/System");
	jmethodID exit = (*env)->GetStaticMethodID(env, system, "exit", "(I)V");
	(*env)->CallStaticVoidMethod(env, system, exit, status);
}
