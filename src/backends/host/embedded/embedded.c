#include <stdbool.h>
#include <stdio.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/shared/startup.h"
#include "src/slots/host.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "atl_embedded.h"

static struct atl_paths paths;
static bool started;

/* ART on Apple loads a library linked into the app only if this hook exists (System.loadLibrary
 * finds natives with dlsym on the main image). ART rejects JNI_VERSION_1_8. */
JNIEXPORT jint JNICALL JNI_OnLoad_translation_layer_main(JavaVM *vm, void *reserved)
{
	return JNI_VERSION_1_6;
}

JavaVMOption *atl_embedded_vm_options(const struct atl_embedded_config *config, int *count)
{
	if (started) {
		fprintf(stderr, "atl: already started in this process\n");
		return NULL;
	}
	started = true;
	paths = (struct atl_paths){
		.api_impl_jar = (char *)config->api_impl_jar,
		.framework_res_apk = (char *)config->framework_res_apk,
	};
	if (atl_set_app(&paths, (char *const[]){(char *)config->apk}, 1) < 0)
		return NULL;
	return atl_vm_options(&paths, config->sdk_int, count);
}

void atl_embedded_vm_options_free(JavaVMOption *options, int count)
{
	atl_vm_options_free(options, count);
}

bool host_task_to_back(void)
{
	return true;
}

void host_task_moved(bool front, bool by_host) {}

int atl_embedded_main(JNIEnv *env, const struct atl_embedded_config *config)
{
	int width = config->window_width ?: ATL_WINDOW_WIDTH_DEFAULT;
	int height = config->window_height ?: ATL_WINDOW_HEIGHT_DEFAULT;
	if (atl_vm_init(env, width, height) < 0)
		return -1;

	atl_app_create(env, ui_window_new());
	activity_start(env, atl_activity_create_main(env, config->activity, NULL));
	loop_main_run();
	return 0;
}
