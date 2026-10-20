#ifndef ATL_EMBEDDED_H
#define ATL_EMBEDDED_H

#include <jni.h>

#include "nav.h"

#ifdef __cplusplus
extern "C" {
#endif

/* ATL inside an app that owns the VM. The embedder creates the one VM with its boot class path,
 * VM-wide flags and atl_embedded_vm_options, then calls atl_embedded_main on the thread that
 * becomes Android's main thread. Once per process. The app data dir is ANDROID_APP_DATA_DIR. */

struct atl_embedded_config {
	const char *api_impl_jar;
	const char *framework_res_apk;
	const char *apk;
	const char *activity; // NULL: the manifest's main activity
	int window_width;     // px; 0: ATL's default size
	int window_height;
	int density_dpi;      // 0: 160
	int night;            // Configuration.UI_MODE_NIGHT_YES or _NO; 0: unknown
	void *surface;        // the platform view the app's views go in (ui=uikit: a UIView, which
	                      // may adopt ATLSurface from surface.h); NULL: none
	const struct atl_embedded_listener *listener; // NULL: none; must outlive atl_embedded_main
};

/* NULL on error, after printing it to stderr. */
JavaVMOption *atl_embedded_vm_options(const struct atl_embedded_config *config, int *count);
void atl_embedded_vm_options_free(JavaVMOption *options, int count);
/* Runs the app until its last activity finishes; -1 if it can't start. */
int atl_embedded_main(JNIEnv *env, const struct atl_embedded_config *config);

#ifdef __cplusplus
}
#endif

#endif
