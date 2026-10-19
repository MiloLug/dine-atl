#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_view_ViewGroup.h"
#include "src/slots/ui.h"

JNIEXPORT void JNICALL Java_android_view_ViewGroup_native_1addView(JNIEnv *env, jobject this, jlong widget, jlong child, jint index, jobject layout_params)
{
	ui_view_add_child(_PTR(widget), _PTR(child), index);
}
