#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_BitmapFactory.h"
#include "src/slots/gfx.h"

#include "ninepatch.h"

JNIEXPORT jlong JNICALL Java_android_graphics_BitmapFactory_nativeDecodeByteArray(JNIEnv *env, jclass class, jbyteArray data, jint offset, jint length)
{
	jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
	struct gfx_bitmap *bitmap = gfx_bitmap_decode(bytes + offset, length);
	(*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
	return _INTPTR(bitmap);
}

// the PNG's nine-patch chunk for Bitmap.getNinePatchChunk, or null
JNIEXPORT jbyteArray JNICALL Java_android_graphics_BitmapFactory_nativeNinePatchChunk(JNIEnv *env, jclass class, jbyteArray data, jint offset, jint length)
{
	jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
	size_t size;
	void *chunk = ninepatch_chunk_from_png((const uint8_t *)bytes + offset, length, &size);
	(*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
	if (!chunk)
		return NULL;
	jbyteArray array = (*env)->NewByteArray(env, size);
	(*env)->SetByteArrayRegion(env, array, 0, size, chunk);
	free(chunk);
	return array;
}
