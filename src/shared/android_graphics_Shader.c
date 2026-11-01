#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_LinearGradient.h"
#include "src/api-impl-jni/generated_headers/android_graphics_RadialGradient.h"
#include "src/api-impl-jni/generated_headers/android_graphics_Shader.h"
#include "src/api-impl-jni/generated_headers/android_graphics_SweepGradient.h"

#include "shader.h"

// Java checked the arrays' lengths
static jlong create(JNIEnv *env, enum shader_kind kind, jint tile, const float geometry[5], jlong matrix, jintArray colors_array, jfloatArray positions_array)
{
	jsize n = (*env)->GetArrayLength(env, colors_array);
	jint *colors = (*env)->GetIntArrayElements(env, colors_array, NULL);
	jfloat *positions = positions_array ? (*env)->GetFloatArrayElements(env, positions_array, NULL) : NULL;
	struct gfx_shader *shader = shader_new(kind, tile, geometry, _PTR(matrix), (const uint32_t *)colors, positions, n);
	if (positions)
		(*env)->ReleaseFloatArrayElements(env, positions_array, positions, JNI_ABORT);
	(*env)->ReleaseIntArrayElements(env, colors_array, colors, JNI_ABORT);
	return _INTPTR(shader);
}

JNIEXPORT jlong JNICALL Java_android_graphics_LinearGradient_nativeCreate(JNIEnv *env, jclass class, jlong matrix, jfloat x0, jfloat y0, jfloat x1, jfloat y1, jintArray colors, jfloatArray positions, jint tile)
{
	return create(env, SHADER_LINEAR, tile, (float[]){x0, y0, x1, y1, 0}, matrix, colors, positions);
}

JNIEXPORT jlong JNICALL Java_android_graphics_RadialGradient_nativeCreate(JNIEnv *env, jclass class, jlong matrix, jfloat x, jfloat y, jfloat radius, jintArray colors, jfloatArray positions, jint tile)
{
	return create(env, SHADER_RADIAL, tile, (float[]){x, y, 0, 0, radius}, matrix, colors, positions);
}

JNIEXPORT jlong JNICALL Java_android_graphics_SweepGradient_nativeCreate(JNIEnv *env, jclass class, jlong matrix, jfloat x, jfloat y, jintArray colors, jfloatArray positions)
{
	return create(env, SHADER_SWEEP, SHADER_CLAMP, (float[]){x, y, 0, 0, 0}, matrix, colors, positions);
}

JNIEXPORT void JNICALL Java_android_graphics_Shader_nativeUnref(JNIEnv *env, jclass class, jlong shader)
{
	shader_unref(_PTR(shader));
}
