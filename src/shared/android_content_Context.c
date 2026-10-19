#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_content_Context.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/ui.h"

#include "display.h"

void atl_display_apply(JNIEnv *env, jobject config, const struct ui_display *display)
{
	if (!config)
		config = _GET_STATIC_OBJ_FIELD(handle_cache.context.class, "sys_config", "Landroid/content/res/Configuration;");

	if (display->width != UI_DISPLAY_UNKNOWN && display->height != UI_DISPLAY_UNKNOWN) {
		_SET_INT_FIELD(config, "screenWidthDp", display->width);
		_SET_INT_FIELD(config, "screenHeightDp", display->height);
		if (display->width >= 800 && display->height >= 800)
			_SET_INT_FIELD(config, "screenLayout", /*SCREENLAYOUT_SIZE_LARGE*/ 0x03);
		else
			_SET_INT_FIELD(config, "screenLayout", /*SCREENLAYOUT_SIZE_NORMAL*/ 0x02);
	}
	if (display->night != UI_DISPLAY_UNKNOWN)
		_SET_INT_FIELD(config, "uiMode", display->night);
}

JNIEXPORT void JNICALL Java_android_content_Context_native_1updateConfig(JNIEnv *env, jclass this, jobject config)
{
	struct ui_display display;
	ui_display_get(&display);
	atl_display_apply(env, config, &display);
}
