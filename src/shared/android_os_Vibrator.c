#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_os_Vibrator.h"

// builds without gtk have no vibrator; gtk's natives are src/api-impl-jni/android_os_Vibrator.c
JNIEXPORT jint JNICALL Java_android_os_Vibrator_native_1constructor(JNIEnv *env, jobject this)
{
	return -1;
}

JNIEXPORT void JNICALL Java_android_os_Vibrator_native_1vibrate(JNIEnv *env, jobject this, jint fd, jlong duration) {}
