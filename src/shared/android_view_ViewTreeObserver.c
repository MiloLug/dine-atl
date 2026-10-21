#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_view_ViewTreeObserver.h"
#include "src/slots/ui.h"

static void set_observed(JNIEnv *env, jobject this, const char *field, enum ui_observe what, bool observed)
{
	struct ui_window *window = _PTR(_GET_LONG_FIELD(this, "window"));
	if (!window) // a floating observer: merged into its view's window on attach
		return;
	struct ui_observation *observation = _PTR(_GET_LONG_FIELD(this, field));
	if (observed && !observation) {
		_SET_LONG_FIELD(this, field, _INTPTR(ui_window_observe(window, (struct atl_ref *)_WEAK_REF(this), what)));
	} else if (!observed && observation) {
		ui_window_unobserve(window, observation);
		_SET_LONG_FIELD(this, field, 0);
	}
}

JNIEXPORT void JNICALL Java_android_view_ViewTreeObserver_native_1set_1have_1global_1layout_1listeners(JNIEnv *env, jobject this, jboolean have_listeners)
{
	set_observed(env, this, "global_layout_observation", UI_OBSERVE_LAYOUT, have_listeners);
}

JNIEXPORT void JNICALL Java_android_view_ViewTreeObserver_native_1set_1have_1predraw_1listeners(JNIEnv *env, jobject this, jboolean have_listeners)
{
	set_observed(env, this, "pre_draw_observation", UI_OBSERVE_DRAW, have_listeners);
}
