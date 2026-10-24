#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_widget_Button.h"
#include "src/slots/ui.h"

#include "view.h"

JNIEXPORT jlong JNICALL Java_android_widget_Button_native_1new(JNIEnv *env, jobject this, jint kind)
{
	struct ui_view_traits traits;
	atl_view_traits_get(env, this, &traits);
	char *name = atl_view_class_name(env, this);
	struct ui_view *view = ui_button_new((struct atl_ref *)_WEAK_REF(this), name, &traits, kind);
	free(name);
	return _INTPTR(view);
}

JNIEXPORT void JNICALL Java_android_widget_Button_native_1setState(JNIEnv *env, jobject this, jlong widget_ptr, jboolean pressed, jboolean checked)
{
	ui_button_set_state(_PTR(widget_ptr), pressed, checked);
}
