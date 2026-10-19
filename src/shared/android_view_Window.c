#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_view_Window.h"
#include "src/slots/ui.h"

JNIEXPORT void JNICALL Java_android_view_Window_set_1title(JNIEnv *env, jobject this, jlong window, jstring title_jstr)
{
	const char *title = (*env)->GetStringUTFChars(env, title_jstr, NULL);
	ui_window_set_title(_PTR(window), title);
	(*env)->ReleaseStringUTFChars(env, title_jstr, title);
}

JNIEXPORT void JNICALL Java_android_view_Window_set_1jobject(JNIEnv *env, jclass this, jlong window, jobject window_jobj)
{
	ui_window_set_java(_PTR(window), (struct atl_ref *)_WEAK_REF(window_jobj));
}

JNIEXPORT void JNICALL Java_android_view_Window_remove_1gtk_1background(JNIEnv *env, jobject this, jlong window)
{
	ui_window_clear_background(_PTR(window));
}
