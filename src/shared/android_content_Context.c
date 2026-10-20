#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_content_Context.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/ui.h"

#include "display.h"
#include "upcall.h"

void atl_display_apply(JNIEnv *env, jobject config, const struct ui_display *display)
{
	if (!config)
		config = _GET_STATIC_OBJ_FIELD(handle_cache.context.class, "sys_config", "Landroid/content/res/Configuration;");

	if (display->width != UI_DISPLAY_UNKNOWN && display->height != UI_DISPLAY_UNKNOWN) {
		int density_dpi = atl_window_get()->density_dpi;
		int width_dp = display->width * 160 / density_dpi;
		int height_dp = display->height * 160 / density_dpi;
		_SET_INT_FIELD(config, "screenWidthDp", width_dp);
		_SET_INT_FIELD(config, "screenHeightDp", height_dp);
		if (width_dp >= 800 && height_dp >= 800)
			_SET_INT_FIELD(config, "screenLayout", /*SCREENLAYOUT_SIZE_LARGE*/ 0x03);
		else
			_SET_INT_FIELD(config, "screenLayout", /*SCREENLAYOUT_SIZE_NORMAL*/ 0x02);
	}
	// a partial update leaves night UNKNOWN: keep the night bits there
	int ui_mode = _GET_INT_FIELD(config, "uiMode");
	if (display->night != UI_DISPLAY_UNKNOWN)
		ui_mode = (ui_mode & ~/*UI_MODE_NIGHT_MASK*/ 0x30) | display->night;
	_SET_INT_FIELD(config, "uiMode", (ui_mode & ~/*UI_MODE_TYPE_MASK*/ 0x0f) | /*UI_MODE_TYPE_NORMAL*/ 0x01);
}

JNIEXPORT void JNICALL Java_android_content_Context_native_1updateConfig(JNIEnv *env, jclass this, jobject config)
{
	struct ui_display display;
	ui_display_get(&display);
	atl_display_apply(env, config, &display);
}
