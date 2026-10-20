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
static const struct atl_embedded_listener *listener;

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
	return atl_vm_options(&paths, count);
}

void atl_embedded_vm_options_free(JavaVMOption *options, int count)
{
	atl_vm_options_free(options, count);
}

bool host_task_to_back(void)
{
	return true;
}

void host_task_moved(bool front, bool by_host)
{
	if (listener && listener->task_moved)
		listener->task_moved(listener->context, front, by_host);
}

static int exited(int status)
{
	if (listener && listener->exited)
		listener->exited(listener->context, status);
	return status;
}

int atl_embedded_main(JNIEnv *env, const struct atl_embedded_config *config)
{
	listener = config->listener;
	struct atl_window_config window = {
		.width = config->window_width ?: ATL_WINDOW_WIDTH_DEFAULT,
		.height = config->window_height ?: ATL_WINDOW_HEIGHT_DEFAULT,
		.density_dpi = config->density_dpi ?: ATL_DENSITY_DPI_DEFAULT,
		.night = config->night ?: UI_DISPLAY_UNKNOWN,
	};
	if (atl_vm_init(env, &window) < 0)
		return exited(-1);

	atl_app_create(env, ui_window_new(config->surface));
	jobject activity = atl_activity_create_main(env, config->activity, NULL);
	activity_start(env, activity);
	(*env)->DeleteLocalRef(env, activity); // this frame lasts the app's lifetime
	loop_main_run();
	return exited(0);
}
