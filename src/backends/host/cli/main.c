#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/shared/host_linux.h"
#include "src/shared/nav.h"
#include "src/shared/startup.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

/* Plain C entry for builds without a desktop toolkit: one app, one main activity. */

#define USAGE "usage: %s [-c] [-d DPI] [-l ACTIVITY] [-X JVM_OPTION]... APK...\n"

// -c: system navigation from stdin, one command per line; the loop slot has no fd watch
static void *read_commands(void *data)
{
	char *line = NULL;
	size_t size = 0;
	while (getline(&line, &size, stdin) != -1) {
		line[strcspn(line, "\n")] = '\0';
		if (!strcmp(line, "back"))
			atl_nav_back();
		else if (!strcmp(line, "home"))
			atl_nav_home();
		else if (!strcmp(line, "front"))
			atl_nav_front();
		else
			fprintf(stderr, "commands: back, home, front; not \"%s\"\n", line);
	}
	free(line);
	return NULL;
}

int main(int argc, char **argv)
{
	atl_linux_host_init();

	const char *activity = NULL;
	bool commands = false;
	struct atl_window_config window = {ATL_WINDOW_WIDTH_DEFAULT, ATL_WINDOW_HEIGHT_DEFAULT, ATL_DENSITY_DPI_DEFAULT, UI_DISPLAY_UNKNOWN};
	char **jvm_options = calloc(argc, sizeof(*jvm_options));
	int n_jvm_options = 0;
	int opt;
	while ((opt = getopt(argc, argv, "cd:l:X:")) != -1) {
		switch (opt) {
			case 'c':
				commands = true;
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
	if (optind == argc) {
		fprintf(stderr, USAGE, argv[0]);
		return 1;
	}

	struct atl_paths paths;
	atl_prepare(&paths, &argv[optind], argc - optind, false);
	JNIEnv *env = atl_vm_start(&paths, jvm_options, &window);

	atl_app_create(env, ui_window_new(NULL));
	if (commands) {
		pthread_t reader;
		pthread_create(&reader, NULL, read_commands, NULL);
		pthread_detach(reader);
	}
	jobject main_activity = atl_activity_create_main(env, activity, NULL);
	activity_start(env, main_activity);
	(*env)->DeleteLocalRef(env, main_activity); // this frame lasts the app's lifetime
	loop_main_run();

	atl_exit(env, 0);
	return 0;
}
