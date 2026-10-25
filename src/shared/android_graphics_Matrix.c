#include <graphene.h>
#include <math.h>
#include <stdbool.h>
#include <stdint.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_Matrix.h"

/* A Matrix is a graphene 4x4 in row-vector form: graphene_matrix_multiply(a, b) applies a, then
 * b. Android's 3x3 (column vectors) sits in rows and columns 0, 1 and 3; z stays identity. */

// Android's value order: MSCALE_X, MSKEW_X, MTRANS_X, MSKEW_Y, MSCALE_Y, MTRANS_Y, MPERSP_0-2
static void set_values(graphene_matrix_t *matrix, const float v[9])
{
	float values4x4[4][4] = {
		/* clang-format off */
		{v[android_graphics_Matrix_MSCALE_X],  v[android_graphics_Matrix_MSKEW_Y], 0, v[android_graphics_Matrix_MPERSP_0]},
		{ v[android_graphics_Matrix_MSKEW_X], v[android_graphics_Matrix_MSCALE_Y], 0, v[android_graphics_Matrix_MPERSP_1]},
		{                                  0,                                   0, 1,                                   0},
		{v[android_graphics_Matrix_MTRANS_X], v[android_graphics_Matrix_MTRANS_Y], 0, v[android_graphics_Matrix_MPERSP_2]},
		/* clang-format on */
	};
	graphene_matrix_init_from_float(matrix, *values4x4);
}

static void set_sin_cos(graphene_matrix_t *matrix, float sin_v, float cos_v, float px, float py)
{
	set_values(matrix, (float[9]){
	                       cos_v, -sin_v, sin_v * py + (1 - cos_v) * px,
	                       sin_v, cos_v, -sin_v * px + (1 - cos_v) * py,
	                       0, 0, 1});
}

static void set_skew(graphene_matrix_t *matrix, float kx, float ky, float px, float py)
{
	set_values(matrix, (float[9]){1, kx, -kx * py, ky, 1, -ky * px, 0, 0, 1});
}

// with the perspective divide, which graphene_matrix_transform_point leaves out
static void map_point(const graphene_matrix_t *matrix, float x, float y, float *out_x, float *out_y)
{
	graphene_vec4_t point;
	graphene_matrix_transform_vec4(matrix, graphene_vec4_init(&point, x, y, 0, 1), &point);
	float w = graphene_vec4_get_w(&point);
	*out_x = graphene_vec4_get_x(&point) / w;
	*out_y = graphene_vec4_get_y(&point) / w;
}

static bool rect_stays_rect(const graphene_matrix_t *matrix)
{
	if (graphene_matrix_get_value(matrix, 0, 3) || graphene_matrix_get_value(matrix, 1, 3) || graphene_matrix_get_value(matrix, 3, 3) != 1)
		return false;
	float scale_x = graphene_matrix_get_value(matrix, 0, 0);
	float skew_x = graphene_matrix_get_value(matrix, 1, 0);
	float skew_y = graphene_matrix_get_value(matrix, 0, 1);
	float scale_y = graphene_matrix_get_value(matrix, 1, 1);
	return (!skew_x && !skew_y && scale_x && scale_y) || (!scale_x && !scale_y && skew_x && skew_y);
}

JNIEXPORT jlong JNICALL Java_android_graphics_Matrix_native_1create(JNIEnv *env, jclass class, jlong src)
{
	graphene_matrix_t *matrix = graphene_matrix_alloc();
	if (src)
		return _INTPTR(graphene_matrix_init_from_matrix(matrix, _PTR(src)));
	return _INTPTR(graphene_matrix_init_identity(matrix));
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1getValues(JNIEnv *env, jclass class, jlong src, jfloatArray values_ref)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(src);
	jfloat *value = (*env)->GetFloatArrayElements(env, values_ref, NULL);
	// add 0.f to all values to avoid failing CTS tests with "expected:<0.0> but was:<-0.0>"
	value[android_graphics_Matrix_MSCALE_X] = graphene_matrix_get_value(matrix, 0, 0) + 0.f;
	value[android_graphics_Matrix_MSKEW_X] = graphene_matrix_get_value(matrix, 1, 0) + 0.f;
	value[android_graphics_Matrix_MTRANS_X] = graphene_matrix_get_value(matrix, 3, 0) + 0.f;
	value[android_graphics_Matrix_MSKEW_Y] = graphene_matrix_get_value(matrix, 0, 1) + 0.f;
	value[android_graphics_Matrix_MSCALE_Y] = graphene_matrix_get_value(matrix, 1, 1) + 0.f;
	value[android_graphics_Matrix_MTRANS_Y] = graphene_matrix_get_value(matrix, 3, 1) + 0.f;
	value[android_graphics_Matrix_MPERSP_0] = graphene_matrix_get_value(matrix, 0, 3) + 0.f;
	value[android_graphics_Matrix_MPERSP_1] = graphene_matrix_get_value(matrix, 1, 3) + 0.f;
	value[android_graphics_Matrix_MPERSP_2] = graphene_matrix_get_value(matrix, 3, 3) + 0.f;
	(*env)->ReleaseFloatArrayElements(env, values_ref, value, 0);
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1set(JNIEnv *env, jclass class, jlong dest_ptr, jlong src_ptr)
{
	graphene_matrix_t *dest = (graphene_matrix_t *)_PTR(dest_ptr);
	graphene_matrix_t *src = (graphene_matrix_t *)_PTR(src_ptr);
	graphene_matrix_init_from_matrix(dest, src);
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1isIdentity(JNIEnv *env, jclass class, jlong matrix_ptr)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	return graphene_matrix_is_identity(matrix);
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1preConcat(JNIEnv *env, jclass class, jlong matrix_ptr, jlong other_ptr)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_t *other = (graphene_matrix_t *)_PTR(other_ptr);
	graphene_matrix_multiply(other, matrix, matrix);

	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1postConcat(JNIEnv *env, jclass class, jlong matrix_ptr, jlong other_ptr)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_t *other = (graphene_matrix_t *)_PTR(other_ptr);
	graphene_matrix_multiply(matrix, other, matrix);
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1mapRect(JNIEnv *env, jclass class, jlong matrix_ptr, jobject dest, jobject src)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	float left = _GET_FLOAT_FIELD(src, "left"), top = _GET_FLOAT_FIELD(src, "top");
	float right = _GET_FLOAT_FIELD(src, "right"), bottom = _GET_FLOAT_FIELD(src, "bottom");
	float x[4], y[4];
	map_point(matrix, left, top, &x[0], &y[0]);
	map_point(matrix, right, top, &x[1], &y[1]);
	map_point(matrix, right, bottom, &x[2], &y[2]);
	map_point(matrix, left, bottom, &x[3], &y[3]);
	float min_x = x[0], max_x = x[0], min_y = y[0], max_y = y[0];
	for (int i = 1; i < 4; i++) {
		min_x = fminf(min_x, x[i]);
		max_x = fmaxf(max_x, x[i]);
		min_y = fminf(min_y, y[i]);
		max_y = fmaxf(max_y, y[i]);
	}

	_SET_FLOAT_FIELD(dest, "left", min_x);
	_SET_FLOAT_FIELD(dest, "top", min_y);
	_SET_FLOAT_FIELD(dest, "right", max_x);
	_SET_FLOAT_FIELD(dest, "bottom", max_y);
	return rect_stays_rect(matrix);
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1reset(JNIEnv *env, jclass class, jlong matrix_ptr)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_init_identity(matrix);
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_finalizer(JNIEnv *env, jclass class, jlong matrix_ptr)
{
	graphene_matrix_free((graphene_matrix_t *)_PTR(matrix_ptr));
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1postTranslate(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat x, jfloat y)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_point3d_t translation = GRAPHENE_POINT3D_INIT(x, y, 0);
	graphene_matrix_translate(matrix, &translation);
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1postScale__JFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat x, jfloat y)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_scale(matrix, x, y, 1.f);
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1postRotate__JFFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat degrees, jfloat px, jfloat py)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_translate(matrix, &GRAPHENE_POINT3D_INIT(-px, -py, 0));
	graphene_matrix_rotate_z(matrix, degrees);
	graphene_matrix_translate(matrix, &GRAPHENE_POINT3D_INIT(px, py, 0));
	return true;
}

JNIEXPORT void Java_android_graphics_Matrix_native_1setScale__JFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat x, jfloat y)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_init_scale(matrix, x, y, 1.f);
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1setScale__JFFFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat x, jfloat y, jfloat px, jfloat py)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_init_translate(matrix, &GRAPHENE_POINT3D_INIT(-px, -py, 0));
	graphene_matrix_scale(matrix, x, y, 1.f);
	graphene_matrix_translate(matrix, &GRAPHENE_POINT3D_INIT(px, py, 0));
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1postScale__JFFFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat x, jfloat y, jfloat px, jfloat py)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_translate(matrix, &GRAPHENE_POINT3D_INIT(-px, -py, 0));
	graphene_matrix_scale(matrix, x, y, 1.f);
	graphene_matrix_translate(matrix, &GRAPHENE_POINT3D_INIT(px, py, 0));
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1postRotate__JF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat degrees)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_rotate_z(matrix, degrees);
	return true;
}

#define SCALE_TO_FIT_FILL   0
#define SCALE_TO_FIT_START  1
#define SCALE_TO_FIT_CENTER 2
#define SCALE_TO_FIT_END    3
JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1setRectToRect(JNIEnv *env, jclass class, jlong matrix_ptr, jobject src, jobject dest, jint stf)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_init_translate(matrix, &GRAPHENE_POINT3D_INIT(-_GET_FLOAT_FIELD(src, "left"), -_GET_FLOAT_FIELD(src, "top"), 0));
	float src_width = _GET_FLOAT_FIELD(src, "right") - _GET_FLOAT_FIELD(src, "left");
	float src_height = _GET_FLOAT_FIELD(src, "bottom") - _GET_FLOAT_FIELD(src, "top");
	float dest_width = _GET_FLOAT_FIELD(dest, "right") - _GET_FLOAT_FIELD(dest, "left");
	float dest_height = _GET_FLOAT_FIELD(dest, "bottom") - _GET_FLOAT_FIELD(dest, "top");
	float factor_x = dest_width / src_width;
	float factor_y = dest_height / src_height;
	if (stf != SCALE_TO_FIT_FILL) {
		factor_x = factor_y = (factor_x < factor_y) ? factor_x : factor_y;
	}
	graphene_matrix_scale(matrix, factor_x, factor_y, 1.f);
	graphene_point3d_t translation = GRAPHENE_POINT3D_INIT(_GET_FLOAT_FIELD(dest, "left"), _GET_FLOAT_FIELD(dest, "top"), 0);
	if (stf == SCALE_TO_FIT_CENTER) {
		translation.x += (dest_width - src_width * factor_x) / 2;
		translation.y += (dest_height - src_height * factor_y) / 2;
	} else if (stf == SCALE_TO_FIT_END) {
		translation.x += (dest_width - src_width * factor_x);
		translation.y += (dest_height - src_height * factor_y);
	}
	graphene_matrix_translate(matrix, &translation);
	return true;
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1mapPoints(JNIEnv *env, jclass class, jlong matrix_ptr, jfloatArray dst_ref, jint dst_idx, jfloatArray src_ref, jint src_idx, jint count, jboolean is_pts)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	// vectors, as Skia: the mapped point minus the mapped origin
	float origin_x = 0, origin_y = 0;
	if (!is_pts)
		map_point(matrix, 0, 0, &origin_x, &origin_y);
	jfloat *src = (*env)->GetFloatArrayElements(env, src_ref, NULL);
	jfloat *dst = (*env)->GetFloatArrayElements(env, dst_ref, NULL);
	for (int i = 0; i < count; i++) {
		float x, y;
		map_point(matrix, src[src_idx + i * 2], src[src_idx + i * 2 + 1], &x, &y);
		dst[dst_idx + i * 2] = x - origin_x;
		dst[dst_idx + i * 2 + 1] = y - origin_y;
	}
	(*env)->ReleaseFloatArrayElements(env, src_ref, src, 0);
	(*env)->ReleaseFloatArrayElements(env, dst_ref, dst, 0);
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1setTranslate(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat x, jfloat y)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_init_translate(matrix, &GRAPHENE_POINT3D_INIT(x, y, 0));
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1preRotate__JF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat degrees)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_t rotation;
	graphene_vec3_t rotation_axis;
	graphene_vec3_init(&rotation_axis, 0, 0, 1);
	graphene_matrix_init_rotate(&rotation, degrees, &rotation_axis);
	graphene_matrix_multiply(&rotation, matrix, matrix);
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1invert(JNIEnv *env, jclass class, jlong matrix_ptr, jlong inverse_ptr)
{
	return graphene_matrix_inverse((graphene_matrix_t *)_PTR(matrix_ptr), (graphene_matrix_t *)_PTR(inverse_ptr));
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1preScale__JFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat x, jfloat y)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_t scale;
	graphene_matrix_init_scale(&scale, x, y, 1);
	graphene_matrix_multiply(&scale, matrix, matrix);
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1preScale__JFFFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat x, jfloat y, jfloat px, jfloat py)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_t scale;
	graphene_matrix_init_translate(&scale, &GRAPHENE_POINT3D_INIT(-px, -py, 0));
	graphene_matrix_scale(&scale, x, y, 1);
	graphene_matrix_translate(&scale, &GRAPHENE_POINT3D_INIT(px, py, 0));
	graphene_matrix_multiply(&scale, matrix, matrix);
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1preTranslate(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat x, jfloat y)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_t translation;
	graphene_matrix_init_translate(&translation, &GRAPHENE_POINT3D_INIT(x, y, 0));
	graphene_matrix_multiply(&translation, matrix, matrix);
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1preRotate__JFFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat degrees, jfloat px, jfloat py)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_t rotation;
	graphene_matrix_init_translate(&rotation, &GRAPHENE_POINT3D_INIT(-px, -py, 0));
	graphene_matrix_rotate_z(&rotation, degrees);
	graphene_matrix_translate(&rotation, &GRAPHENE_POINT3D_INIT(px, py, 0));
	graphene_matrix_multiply(&rotation, matrix, matrix);
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1equals(JNIEnv *env, jclass class, jlong matrix1_ptr, jlong matrix2_ptr)
{
	graphene_matrix_t *matrix1 = (graphene_matrix_t *)_PTR(matrix1_ptr);
	graphene_matrix_t *matrix2 = (graphene_matrix_t *)_PTR(matrix2_ptr);
	return graphene_matrix_equal(matrix1, matrix2);
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1setValues(JNIEnv *env, jclass class, jlong matrix_ptr, jfloatArray values_ref)
{
	jfloat *values = (*env)->GetFloatArrayElements(env, values_ref, NULL);
	set_values(_PTR(matrix_ptr), values);
	(*env)->ReleaseFloatArrayElements(env, values_ref, values, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1setRotate__JFFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat degrees, jfloat px, jfloat py)
{
	graphene_matrix_t *matrix = (graphene_matrix_t *)_PTR(matrix_ptr);
	graphene_matrix_init_translate(matrix, &GRAPHENE_POINT3D_INIT(-px, -py, 0));
	graphene_matrix_rotate_z(matrix, degrees);
	graphene_matrix_translate(matrix, &GRAPHENE_POINT3D_INIT(px, py, 0));
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1setRotate__JF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat degrees)
{
	float radians = DEG2RAD(degrees);
	set_sin_cos(_PTR(matrix_ptr), sinf(radians), cosf(radians), 0, 0);
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1setSinCos__JFFFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat sin_v, jfloat cos_v, jfloat px, jfloat py)
{
	set_sin_cos(_PTR(matrix_ptr), sin_v, cos_v, px, py);
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1setSinCos__JFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat sin_v, jfloat cos_v)
{
	set_sin_cos(_PTR(matrix_ptr), sin_v, cos_v, 0, 0);
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1setSkew__JFFFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat kx, jfloat ky, jfloat px, jfloat py)
{
	set_skew(_PTR(matrix_ptr), kx, ky, px, py);
}

JNIEXPORT void JNICALL Java_android_graphics_Matrix_native_1setSkew__JFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat kx, jfloat ky)
{
	set_skew(_PTR(matrix_ptr), kx, ky, 0, 0);
}

// a × b: b applies first
JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1setConcat(JNIEnv *env, jclass class, jlong matrix_ptr, jlong a_ptr, jlong b_ptr)
{
	graphene_matrix_multiply(_PTR(b_ptr), _PTR(a_ptr), _PTR(matrix_ptr));
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1preSkew__JFFFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat kx, jfloat ky, jfloat px, jfloat py)
{
	graphene_matrix_t skew;
	set_skew(&skew, kx, ky, px, py);
	graphene_matrix_multiply(&skew, _PTR(matrix_ptr), _PTR(matrix_ptr));
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1preSkew__JFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat kx, jfloat ky)
{
	return Java_android_graphics_Matrix_native_1preSkew__JFFFF(env, class, matrix_ptr, kx, ky, 0, 0);
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1postSkew__JFFFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat kx, jfloat ky, jfloat px, jfloat py)
{
	graphene_matrix_t skew;
	set_skew(&skew, kx, ky, px, py);
	graphene_matrix_multiply(_PTR(matrix_ptr), &skew, _PTR(matrix_ptr));
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1postSkew__JFF(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat kx, jfloat ky)
{
	return Java_android_graphics_Matrix_native_1postSkew__JFFFF(env, class, matrix_ptr, kx, ky, 0, 0);
}

/* Skia's Poly2Proc, Poly3Proc and Poly4Proc (SkMatrix.cpp, Android 10): the matrix that maps the
 * unit square's corners to the points */
static bool poly_map(const float *p, int count, graphene_matrix_t *matrix)
{
	float a1 = 0, a2 = 0;
	if (count == 4) {
		float x0 = p[4] - p[0], y0 = p[5] - p[1];
		float x1 = p[4] - p[2], y1 = p[5] - p[3];
		float x2 = p[4] - p[6], y2 = p[5] - p[7];
		float denom;
		if (x2 > 0 ? y2 > 0 ? x2 > y2 : x2 > -y2 : y2 > 0 ? -x2 > y2 : x2 < y2) {
			denom = x1 * y2 / x2 - y1;
			if (denom * denom == 0)
				return false;
			a1 = (((x0 - x1) * y2 / x2) - y0 + y1) / denom;
		} else {
			denom = x1 - y1 * x2 / y2;
			if (denom * denom == 0)
				return false;
			a1 = (x0 - x1 - (y0 - y1) * x2 / y2) / denom;
		}
		if (x1 > 0 ? y1 > 0 ? x1 > y1 : x1 > -y1 : y1 > 0 ? -x1 > y1 : x1 < y1) {
			denom = y2 - x2 * y1 / x1;
			if (denom * denom == 0)
				return false;
			a2 = (y0 - y2 - (x0 - x2) * y1 / x1) / denom;
		} else {
			denom = y2 * x1 / y1 - x2;
			if (denom * denom == 0)
				return false;
			a2 = ((y0 - y2) * x1 / y1 - x0 + x2) / denom;
		}
		set_values(matrix, (float[9]){
		                       a2 * p[6] + p[6] - p[0], a1 * p[2] + p[2] - p[0], p[0],
		                       a2 * p[7] + p[7] - p[1], a1 * p[3] + p[3] - p[1], p[1],
		                       a2, a1, 1});
	} else if (count == 3) {
		set_values(matrix, (float[9]){p[4] - p[0], p[2] - p[0], p[0], p[5] - p[1], p[3] - p[1], p[1], 0, 0, 1});
	} else {
		set_values(matrix, (float[9]){p[3] - p[1], p[2] - p[0], p[0], p[0] - p[2], p[3] - p[1], p[1], 0, 0, 1});
	}
	return true;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1setPolyToPoly(JNIEnv *env, jclass class, jlong matrix_ptr, jfloatArray src_ref, jint src_idx, jfloatArray dst_ref, jint dst_idx, jint count)
{
	graphene_matrix_t *matrix = _PTR(matrix_ptr);
	if (count < 0 || count > 4)
		return false;
	jfloat *src = (*env)->GetFloatArrayElements(env, src_ref, NULL);
	jfloat *dst = (*env)->GetFloatArrayElements(env, dst_ref, NULL);
	bool ok = true;
	if (count == 0) {
		graphene_matrix_init_identity(matrix);
	} else if (count == 1) {
		graphene_matrix_init_translate(matrix, &GRAPHENE_POINT3D_INIT(dst[dst_idx] - src[src_idx], dst[dst_idx + 1] - src[src_idx + 1], 0));
	} else {
		graphene_matrix_t src_map, src_inverse, dst_map;
		ok = poly_map(&src[src_idx], count, &src_map) && graphene_matrix_inverse(&src_map, &src_inverse) && poly_map(&dst[dst_idx], count, &dst_map);
		if (ok)
			graphene_matrix_multiply(&src_inverse, &dst_map, matrix);
	}
	(*env)->ReleaseFloatArrayElements(env, src_ref, src, JNI_ABORT);
	(*env)->ReleaseFloatArrayElements(env, dst_ref, dst, JNI_ABORT);
	return ok;
}

// Skia: the geometric mean of the lengths of (radius, 0) and (0, radius) mapped as vectors
JNIEXPORT jfloat JNICALL Java_android_graphics_Matrix_native_1mapRadius(JNIEnv *env, jclass class, jlong matrix_ptr, jfloat radius)
{
	graphene_matrix_t *matrix = _PTR(matrix_ptr);
	float ox, oy, ax, ay, bx, by;
	map_point(matrix, 0, 0, &ox, &oy);
	map_point(matrix, radius, 0, &ax, &ay);
	map_point(matrix, 0, radius, &bx, &by);
	return sqrtf(hypotf(ax - ox, ay - oy) * hypotf(bx - ox, by - oy));
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Matrix_native_1rectStaysRect(JNIEnv *env, jclass class, jlong matrix_ptr)
{
	return rect_stays_rect(_PTR(matrix_ptr));
}
