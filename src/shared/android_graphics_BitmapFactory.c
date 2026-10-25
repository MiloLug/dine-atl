#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_BitmapFactory.h"
#include "src/slots/gfx.h"

JNIEXPORT jlong JNICALL Java_android_graphics_BitmapFactory_nativeDecodeByteArray(JNIEnv *env, jclass class, jbyteArray data, jint offset, jint length)
{
	jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
	struct gfx_bitmap *bitmap = gfx_bitmap_decode(bytes + offset, length);
	(*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
	return _INTPTR(bitmap);
}
