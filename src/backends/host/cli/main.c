#include <getopt.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/handle_cache.h"
#include "src/shared/host_linux.h"
#include "src/shared/nav.h"
#include "src/shared/startup.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

/* Plain C entry for builds without a desktop toolkit: one app, and its main activity or an
 * instrumentation. */

#define USAGE "usage: %s [-c] [-d DPI] [-l ACTIVITY | --instrument CLASS] [-X JVM_OPTION]... APK...\n"

// "insets" and 12 dp: system gestures, mandatory gestures, navigation bars, each left top right bottom
static bool parse_insets(const char *line, struct atl_window_insets *insets)
{
	struct atl_edges *e[] = {&insets->system_gestures, &insets->mandatory_gestures, &insets->navigation_bars};
	return sscanf(line, "insets %f %f %f %f %f %f %f %f %f %f %f %f",
	              &e[0]->left, &e[0]->top, &e[0]->right, &e[0]->bottom, &e[1]->left, &e[1]->top, &e[1]->right, &e[1]->bottom,
	              &e[2]->left, &e[2]->top, &e[2]->right, &e[2]->bottom) == 12;
}

// -c: system navigation from stdin, one command per line; the loop slot has no fd watch
static void *read_commands(void *data)
{
	char *line = NULL;
	size_t size = 0;
	while (getline(&line, &size, stdin) != -1) {
		line[strcspn(line, "\n")] = '\0';
		struct atl_window_insets insets;
		if (!strcmp(line, "back"))
			atl_nav_back();
		else if (!strcmp(line, "home"))
			atl_nav_home();
		else if (!strcmp(line, "front"))
			atl_nav_front();
		else if (parse_insets(line, &insets))
			atl_nav_set_insets(&insets);
		else
			fprintf(stderr, "commands: back, home, front, insets <12 dp>; not \"%s\"\n", line);
	}
	free(line);
	return NULL;
}

static void start_instrumentation(JNIEnv *env, const char *class_name)
{
	jmethodID create = _STATIC_METHOD(handle_cache.instrumentation.class, "create", "(Ljava/lang/String;Landroid/content/Intent;)Landroid/app/Instrumentation;");
	(*env)->CallStaticObjectMethod(env, handle_cache.instrumentation.class, create, _JSTRING(class_name), NULL);
	if ((*env)->ExceptionCheck(env)) {
		// create handles Exceptions itself; after an Error no runner thread would ever exit
		(*env)->ExceptionDescribe(env);
		exit(1);
	}
}

int main(int argc, char **argv)
{
	atl_linux_host_init();

	const char *activity = NULL;
	const char *instrumentation = NULL;
	bool commands = false;
	struct atl_window_config window = {ATL_WINDOW_WIDTH_DEFAULT, ATL_WINDOW_HEIGHT_DEFAULT, ATL_DENSITY_DPI_DEFAULT, UI_DISPLAY_UNKNOWN};
	char **jvm_options = calloc(argc, sizeof(*jvm_options));
	int n_jvm_options = 0;
	static const struct option long_options[] = {
		{"instrument", required_argument, NULL, 'I'},
		{0},
	};
	int opt;
	while ((opt = getopt_long(argc, argv, "cd:l:X:", long_options, NULL)) != -1) {
		switch (opt) {
			case 'c':
				commands = true;
				break;
			case 'I':
				instrumentation = optarg;
				break;
			case 'd':
				window.density_dpi = atoi(optarg);
				if (window.density_dpi <= 0) {
					fprintf(stderr, USAGE, argv[0]);
					return 1;
				}
				break;
			case 'l':
				activity = optarg;
				break;
			case 'X':
				jvm_options[n_jvm_options++] = optarg;
				break;
			default:
				fprintf(stderr, USAGE, argv[0]);
				return 1;
		}
	}
	if (optind == argc || (instrumentation && activity)) {
		fprintf(stderr, USAGE, argv[0]);
		return 1;
	}

	struct atl_paths paths;
	atl_prepare(&paths, &argv[optind], argc - optind, instrumentation != NULL);
	JNIEnv *env = atl_vm_start(&paths, jvm_options, &window);

	atl_app_create(env, ui_window_new(NULL));
	if (commands) {
		pthread_t reader;
		pthread_create(&reader, NULL, read_commands, NULL);
		pthread_detach(reader);
	}
	if (instrumentation) {
		// the runner's thread ends the process with System.exit
		start_instrumentation(env, instrumentation);
	} else {
		jobject main_activity = atl_activity_create_main(env, activity, NULL);
		activity_start(env, main_activity);
		(*env)->DeleteLocalRef(env, main_activity); // this frame lasts the app's lifetime
	}
	loop_main_run();

	atl_exit(env, 0);
	return 0;
}
