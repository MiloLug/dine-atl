#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_widget_ImageView.h"
#include "src/slots/ui.h"

#include "view.h"

JNIEXPORT jlong JNICALL Java_android_widget_ImageView_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	struct ui_view_traits traits;
	atl_view_traits_get(env, this, &traits);
	char *name = atl_view_class_name(env, this);
	struct ui_view *view = ui_image_new((struct atl_ref *)_WEAK_REF(this), name, &traits);
	free(name);
	return _INTPTR(view);
}

JNIEXPORT void JNICALL Java_android_widget_ImageView_native_1setDrawable(JNIEnv *env, jobject this, jlong widget_ptr, jlong drawable_ptr, jint width, jint height)
{
	ui_image_set_drawable(_PTR(widget_ptr), _PTR(drawable_ptr), width, height);
}

JNIEXPORT void JNICALL Java_android_widget_ImageView_native_1setScaleType(JNIEnv *env, jobject this, jlong widget_ptr, jint scale_type)
{
	ui_image_set_scale_type(_PTR(widget_ptr), scale_type);
}
