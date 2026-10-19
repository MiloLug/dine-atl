#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/shared/host_linux.h"
#include "src/shared/startup.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

/* Plain C entry for builds without a desktop toolkit: one app, one main activity. */

int main(int argc, char **argv)
{
	atl_linux_host_init();

	const char *activity = NULL;
	char **jvm_options = calloc(argc, sizeof(*jvm_options));
	int n_jvm_options = 0;
	int opt;
	while ((opt = getopt(argc, argv, "l:X:")) != -1) {
		switch (opt) {
			case 'l':
				activity = optarg;
				break;
			case 'X':
				jvm_options[n_jvm_options++] = optarg;
				break;
			default:
				fprintf(stderr, "usage: %s [-l ACTIVITY] [-X JVM_OPTION]... APK...\n", argv[0]);
				return 1;
		}
	}
	if (optind == argc) {
		fprintf(stderr, "usage: %s [-l ACTIVITY] [-X JVM_OPTION]... APK...\n", argv[0]);
		return 1;
	}

	struct atl_paths paths;
	atl_prepare(&paths, &argv[optind], argc - optind, false);
	JNIEnv *env = atl_vm_start(&paths, NULL, jvm_options, ATL_WINDOW_WIDTH_DEFAULT, ATL_WINDOW_HEIGHT_DEFAULT);

	atl_app_create(env, ui_window_new());
	activity_start(env, atl_activity_create_main(env, activity, NULL));
	loop_main_run();

	atl_exit(env, 0);
	return 0;
}
