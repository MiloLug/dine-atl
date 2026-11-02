#include <stdbool.h>
#include <stdio.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/shared/app.h"
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
	if (atl_set_app(&paths, (char *const[]){(char *)config->apk}, 1, NULL) < 0)
		return NULL;
	return atl_vm_options(&paths, count);
}

void atl_embedded_vm_options_free(JavaVMOption *options, int count)
{
	atl_vm_options_free(options, count);
}

struct atl_app *atl_embedded_app_new(void)
{
	return atl_app_new();
}

bool host_task_to_back(struct atl_app *app)
{
	return true;
}

void host_task_moved(struct atl_app *app, bool front, bool by_host)
{
	if (app->listener && app->listener->task_moved)
		app->listener->task_moved(app->listener->context, app, front, by_host);
}

void host_gesture_exclusion_changed(struct atl_app *app, const struct atl_gesture_exclusion *exclusion)
{
	if (app->listener && app->listener->exclusion_changed)
		app->listener->exclusion_changed(app->listener->context, app, exclusion);
}

void host_keep_screen_on(struct atl_app *app, bool on)
{
	if (app->listener && app->listener->keep_screen_on)
		app->listener->keep_screen_on(app->listener->context, app, on);
}

static int exited(struct atl_app *app, int status)
{
	if (app->listener && app->listener->exited)
		app->listener->exited(app->listener->context, app, status);
	return status;
}

int atl_embedded_main(JNIEnv *env, const struct atl_embedded_config *config)
{
	struct atl_app *app = config->app;
	app->listener = config->listener;
	struct atl_window_config window = {
		.width = config->window_width ?: ATL_WINDOW_WIDTH_DEFAULT,
		.height = config->window_height ?: ATL_WINDOW_HEIGHT_DEFAULT,
		.density_dpi = config->density_dpi ?: ATL_DENSITY_DPI_DEFAULT,
		.night = config->night ?: UI_DISPLAY_UNKNOWN,
	};
	if (atl_vm_init(env, &window) < 0 || atl_app_prepare(env, &paths, app) < 0)
		return exited(app, -1);

	atl_app_create(env, app, ui_window_new(app, config->surface));
	jobject activity = atl_activity_create_main(env, app, config->activity, NULL);
	activity_start(env, app, activity);
	(*env)->DeleteLocalRef(env, activity); // this frame lasts the app's lifetime
	loop_main_run();
	return exited(app, 0);
}
