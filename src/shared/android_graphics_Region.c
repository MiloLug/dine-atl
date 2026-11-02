#include <stdio.h>
#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_Region.h"

#include "region.h"

static struct region *rect_region(int left, int top, int right, int bottom)
{
	struct region *rect = region_new();
	region_set_rect(rect, left, top, right, bottom);
	return rect;
}

JNIEXPORT jlong JNICALL Java_android_graphics_Region_nativeConstructor(JNIEnv *env, jclass class)
{
	return _INTPTR(region_new());
}

JNIEXPORT void JNICALL Java_android_graphics_Region_nativeDestructor(JNIEnv *env, jclass class, jlong region)
{
	if (region)
		region_free(_PTR(region));
}

JNIEXPORT void JNICALL Java_android_graphics_Region_nativeSetRegion(JNIEnv *env, jclass class, jlong dst, jlong src)
{
	region_set(_PTR(dst), _PTR(src));
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeSetRect(JNIEnv *env, jclass class, jlong dst, jint left, jint top, jint right, jint bottom)
{
	region_set_rect(_PTR(dst), left, top, right, bottom);
	return !region_is_empty(_PTR(dst));
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeSetPath(JNIEnv *env, jclass class, jlong dst, jlong path, jlong clip, jboolean even_odd, jboolean inverse)
{
	region_set_path(_PTR(dst), _PTR(path), clip ? _PTR(clip) : NULL, even_odd, inverse);
	return !region_is_empty(_PTR(dst));
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeIsEmpty(JNIEnv *env, jclass class, jlong region)
{
	return region_is_empty(_PTR(region));
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeIsRect(JNIEnv *env, jclass class, jlong region)
{
	return region_rect_count(_PTR(region)) == 1;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeIsComplex(JNIEnv *env, jclass class, jlong region)
{
	return region_rect_count(_PTR(region)) > 1;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeGetBounds(JNIEnv *env, jclass class, jlong region, jobject bounds)
{
	struct region_rect r = region_bounds(_PTR(region));
	_SET_INT_FIELD(bounds, "left", r.left);
	_SET_INT_FIELD(bounds, "top", r.top);
	_SET_INT_FIELD(bounds, "right", r.right);
	_SET_INT_FIELD(bounds, "bottom", r.bottom);
	return !region_is_empty(_PTR(region));
}

JNIEXPORT jintArray JNICALL Java_android_graphics_Region_nativeGetBoundaryPathRects(JNIEnv *env, jclass class, jlong region)
{
	size_t count = region_rect_count(_PTR(region));
	jintArray array = (*env)->NewIntArray(env, count * 4);
	if (!array || !count)
		return array;
	jint *values = (*env)->GetIntArrayElements(env, array, NULL);
	for (size_t i = 0; i < count; i++) {
		struct region_rect r = region_rect_at(_PTR(region), i);
		values[i * 4 + 0] = r.left;
		values[i * 4 + 1] = r.top;
		values[i * 4 + 2] = r.right;
		values[i * 4 + 3] = r.bottom;
	}
	(*env)->ReleaseIntArrayElements(env, array, values, 0);
	return array;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeContains(JNIEnv *env, jclass class, jlong region, jint x, jint y)
{
	return region_contains(_PTR(region), x, y);
}

// Android's quick checks only promise their true answers: a complex region contains nothing quickly
JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeQuickContains(JNIEnv *env, jclass class, jlong region, jint left, jint top, jint right, jint bottom)
{
	return region_rect_count(_PTR(region)) == 1 && region_contains_rect(_PTR(region), left, top, right, bottom);
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeQuickRejectRect(JNIEnv *env, jclass class, jlong region, jint left, jint top, jint right, jint bottom)
{
	return !region_intersects_rect(_PTR(region), left, top, right, bottom);
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeQuickRejectRegion(JNIEnv *env, jclass class, jlong region, jlong other)
{
	struct region *both = region_new();
	region_op(both, _PTR(region), _PTR(other), REGION_INTERSECT);
	bool rejected = region_is_empty(both);
	region_free(both);
	return rejected;
}

JNIEXPORT void JNICALL Java_android_graphics_Region_nativeTranslate(JNIEnv *env, jclass class, jlong src, jint dx, jint dy, jlong dst)
{
	if (dst)
		region_set(_PTR(dst), _PTR(src));
	region_translate(_PTR(dst ? dst : src), dx, dy);
}

JNIEXPORT void JNICALL Java_android_graphics_Region_nativeScale(JNIEnv *env, jclass class, jlong src, jfloat scale, jlong dst)
{
	region_scale(_PTR(dst ? dst : src), _PTR(src), scale);
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeOpRect(JNIEnv *env, jclass class, jlong dst, jint left, jint top, jint right, jint bottom, jint op)
{
	struct region *rect = rect_region(left, top, right, bottom);
	region_op(_PTR(dst), _PTR(dst), rect, op);
	region_free(rect);
	return !region_is_empty(_PTR(dst));
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeOpRegionRect(JNIEnv *env, jclass class, jlong dst, jlong other, jint left, jint top, jint right, jint bottom, jint op)
{
	struct region *rect = rect_region(left, top, right, bottom);
	region_op(_PTR(dst), rect, _PTR(other), op);
	region_free(rect);
	return !region_is_empty(_PTR(dst));
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeOpRegionRegion(JNIEnv *env, jclass class, jlong dst, jlong region1, jlong region2, jint op)
{
	region_op(_PTR(dst), _PTR(region1), _PTR(region2), op);
	return !region_is_empty(_PTR(dst));
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Region_nativeEquals(JNIEnv *env, jclass class, jlong region1, jlong region2)
{
	return region_equal(_PTR(region1), _PTR(region2));
}

JNIEXPORT jstring JNICALL Java_android_graphics_Region_nativeToString(JNIEnv *env, jclass class, jlong region)
{
	size_t count = region_rect_count(_PTR(region));
	// "Region(" and ")", each rect 4 ints of up to 11 characters, 3 commas, brackets and a space
	char *text = malloc(9 + count * 51);
	size_t n = sprintf(text, "Region(");
	for (size_t i = 0; i < count; i++) {
		struct region_rect r = region_rect_at(_PTR(region), i);
		n += sprintf(text + n, "%s[%d,%d,%d,%d]", i ? " " : "", r.left, r.top, r.right, r.bottom);
	}
	sprintf(text + n, ")");
	jstring string = _JSTRING(text);
	free(text);
	return string;
}
