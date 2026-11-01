#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_NinePatch.h"

#include "ninepatch.h"

JNIEXPORT jboolean JNICALL Java_android_graphics_NinePatch_nativeIsNinePatchChunk(JNIEnv *env, jclass class, jbyteArray chunk_array)
{
	jsize size = (*env)->GetArrayLength(env, chunk_array);
	jbyte *chunk = (*env)->GetByteArrayElements(env, chunk_array, NULL);
	struct gfx_ninepatch *ninepatch = ninepatch_parse(chunk, size);
	(*env)->ReleaseByteArrayElements(env, chunk_array, chunk, JNI_ABORT);
	ninepatch_free(ninepatch);
	return ninepatch != NULL;
}
