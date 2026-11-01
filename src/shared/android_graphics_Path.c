#include <graphene.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_graphics_Path.h"
#include "src/slots/gfx.h"

#include "path.h"

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1new(JNIEnv *env, jclass class)
{
	return _INTPTR(path_new());
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1ref(JNIEnv *env, jclass class, jlong path)
{
	return _INTPTR(path_ref(_PTR(path)));
}

JNIEXPORT void JNICALL Java_android_graphics_Path_native_1unref(JNIEnv *env, jclass class, jlong path)
{
	path_unref(_PTR(path));
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1reset(JNIEnv *env, jclass class, jlong path_ptr)
{
	return _INTPTR(path_reset(_PTR(path_ptr)));
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1close(JNIEnv *env, jclass class, jlong path_ptr)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	path_close(path);
	return _INTPTR(path);
}

// the relative calls' origin: the last point; Skia's segments start after a close at the contour's start
static void origin(const struct gfx_path *path, jboolean relative, bool segment, float *x, float *y)
{
	*x = *y = 0;
	if (!relative)
		return;
	if (segment && path->n_verbs && path->verbs[path->n_verbs - 1] == PATH_CLOSE) {
		*x = path->move_x;
		*y = path->move_y;
	} else {
		path_last_point(path, x, y);
	}
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1move_1to(JNIEnv *env, jclass class, jlong path_ptr, jfloat x, jfloat y, jboolean relative)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	float ox, oy;
	origin(path, relative, false, &ox, &oy);
	path_move_to(path, ox + x, oy + y);
	return _INTPTR(path);
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1line_1to(JNIEnv *env, jclass class, jlong path_ptr, jfloat x, jfloat y, jboolean relative)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	float ox, oy;
	origin(path, relative, true, &ox, &oy);
	path_line_to(path, ox + x, oy + y);
	return _INTPTR(path);
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1quad_1to(JNIEnv *env, jclass class, jlong path_ptr, jfloat x1, jfloat y1, jfloat x2, jfloat y2, jboolean relative)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	float ox, oy;
	origin(path, relative, true, &ox, &oy);
	path_quad_to(path, ox + x1, oy + y1, ox + x2, oy + y2);
	return _INTPTR(path);
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1cubic_1to(JNIEnv *env, jclass class, jlong path_ptr, jfloat x1, jfloat y1, jfloat x2, jfloat y2, jfloat x3, jfloat y3, jboolean relative)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	float ox, oy;
	origin(path, relative, true, &ox, &oy);
	path_cubic_to(path, ox + x1, oy + y1, ox + x2, oy + y2, ox + x3, oy + y3);
	return _INTPTR(path);
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1arc_1to(JNIEnv *env, jclass class, jlong path_ptr, jfloat left, jfloat top, jfloat right, jfloat bottom, jfloat start, jfloat sweep, jboolean force_move_to)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	path_arc_to(path, left, top, right, bottom, start, sweep, force_move_to);
	return _INTPTR(path);
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1add_1arc(JNIEnv *env, jclass class, jlong path_ptr, jfloat left, jfloat top, jfloat right, jfloat bottom, jfloat start, jfloat sweep)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	path_add_arc(path, left, top, right, bottom, start, sweep);
	return _INTPTR(path);
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1add_1path(JNIEnv *env, jclass class, jlong path_ptr, jlong src, jlong matrix)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	path_add_path(path, _PTR(src), _PTR(matrix));
	return _INTPTR(path);
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1add_1rect(JNIEnv *env, jclass class, jlong path_ptr, jfloat left, jfloat top, jfloat right, jfloat bottom, jboolean ccw)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	path_add_rect(path, left, top, right, bottom, ccw);
	return _INTPTR(path);
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1add_1oval(JNIEnv *env, jclass class, jlong path_ptr, jfloat left, jfloat top, jfloat right, jfloat bottom, jboolean ccw)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	path_add_oval(path, left, top, right, bottom, ccw);
	return _INTPTR(path);
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1add_1round_1rect(JNIEnv *env, jclass class, jlong path_ptr, jfloat left, jfloat top, jfloat right, jfloat bottom, jfloatArray radii_array, jboolean ccw)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	float radii[8];
	(*env)->GetFloatArrayRegion(env, radii_array, 0, 8, radii);
	path_add_round_rect(path, left, top, right, bottom, radii, ccw);
	return _INTPTR(path);
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1transform(JNIEnv *env, jclass class, jlong path_ptr, jlong matrix)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	path_transform(path, _PTR(matrix));
	return _INTPTR(path);
}

JNIEXPORT jlong JNICALL Java_android_graphics_Path_native_1set_1last_1point(JNIEnv *env, jclass class, jlong path_ptr, jfloat x, jfloat y)
{
	struct gfx_path *path = path_edit(_PTR(path_ptr));
	path_set_last_point(path, x, y);
	return _INTPTR(path);
}

static void set_rect(JNIEnv *env, jobject rect, const struct gfx_rect *r)
{
	_SET_FLOAT_FIELD(rect, "left", r->left);
	_SET_FLOAT_FIELD(rect, "top", r->top);
	_SET_FLOAT_FIELD(rect, "right", r->right);
	_SET_FLOAT_FIELD(rect, "bottom", r->bottom);
}

JNIEXPORT void JNICALL Java_android_graphics_Path_native_1get_1bounds(JNIEnv *env, jclass class, jlong path, jobject rect)
{
	struct gfx_rect bounds;
	path_bounds(_PTR(path), &bounds);
	set_rect(env, rect, &bounds);
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Path_native_1contains(JNIEnv *env, jclass class, jlong path, jboolean even_odd, jfloat x, jfloat y)
{
	return path_contains(_PTR(path), even_odd, x, y);
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Path_native_1is_1empty(JNIEnv *env, jclass class, jlong path)
{
	return !((struct gfx_path *)_PTR(path))->n_verbs;
}

JNIEXPORT jboolean JNICALL Java_android_graphics_Path_native_1is_1rect(JNIEnv *env, jclass class, jlong path, jobject rect)
{
	struct gfx_rect r;
	if (!path_is_rect(_PTR(path), &r))
		return false;
	set_rect(env, rect, &r);
	return true;
}
