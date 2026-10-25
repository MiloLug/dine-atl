#include <math.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_atl_ATLCanvas.h"
#include "src/slots/gfx.h"

/* A 0 handle draws nothing: a canvas whose recording ended (View.atlDraw, RenderNode.end) or one
 * without a target. The legacy gtk file holds the natives not behind the gfx slot yet. */

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1save(JNIEnv *env, jclass class, jlong canvas)
{
	if (canvas)
		gfx_canvas_save(_PTR(canvas));
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1restore(JNIEnv *env, jclass class, jlong canvas)
{
	if (canvas)
		gfx_canvas_restore(_PTR(canvas));
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1translate(JNIEnv *env, jclass class, jlong canvas, jfloat dx, jfloat dy)
{
	if (canvas)
		gfx_canvas_translate(_PTR(canvas), dx, dy);
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1rotate(JNIEnv *env, jclass class, jlong canvas, jfloat degrees)
{
	if (canvas)
		gfx_canvas_rotate(_PTR(canvas), degrees);
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1scale(JNIEnv *env, jclass class, jlong canvas, jfloat sx, jfloat sy)
{
	if (canvas)
		gfx_canvas_scale(_PTR(canvas), sx, sy);
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1concat(JNIEnv *env, jclass class, jlong canvas, jlong matrix)
{
	if (canvas)
		gfx_canvas_concat(_PTR(canvas), _PTR(matrix));
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1drawRect(JNIEnv *env, jclass class, jlong canvas, jfloat left, jfloat top, jfloat right, jfloat bottom, jlong paint)
{
	if (canvas)
		gfx_canvas_draw_rect(_PTR(canvas), left, top, right, bottom, _PTR(paint));
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1drawRoundRect(JNIEnv *env, jclass class, jlong canvas, jfloat left, jfloat top, jfloat right, jfloat bottom, jfloat rx, jfloat ry, jlong paint)
{
	if (canvas)
		gfx_canvas_draw_round_rect(_PTR(canvas), left, top, right, bottom, rx, ry, _PTR(paint));
}

static void draw_line(jlong canvas, float x0, float y0, float x1, float y1, jlong paint)
{
	if (canvas && !isnan(x0) && !isnan(y0) && !isnan(x1) && !isnan(y1))
		gfx_canvas_draw_line(_PTR(canvas), x0, y0, x1, y1, _PTR(paint));
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1drawLine(JNIEnv *env, jclass class, jlong canvas, jfloat x0, jfloat y0, jfloat x1, jfloat y1, jlong paint)
{
	draw_line(canvas, x0, y0, x1, y1, paint);
}

// count floats from offset, four per line; Java checked the bounds
JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1drawLines(JNIEnv *env, jclass class, jlong canvas, jfloatArray points_arr, jint offset, jint count, jlong paint)
{
	jfloat *points = (*env)->GetFloatArrayElements(env, points_arr, NULL);
	for (int i = offset; i + 4 <= offset + count; i += 4)
		draw_line(canvas, points[i], points[i + 1], points[i + 2], points[i + 3], paint);
	(*env)->ReleaseFloatArrayElements(env, points_arr, points, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1drawBitmap(JNIEnv *env, jclass class, jlong canvas, jlong bitmap, jint left, jint top, jint right, jint bottom, jint src_left, jint src_top, jint src_right, jint src_bottom, jlong paint)
{
	if (!canvas || !bitmap)
		return;
	struct gfx_rect src = {src_left, src_top, src_right, src_bottom}, dst = {left, top, right, bottom};
	gfx_canvas_draw_bitmap(_PTR(canvas), _PTR(bitmap), src_right < 0 ? NULL : &src, &dst, _PTR(paint));
}

JNIEXPORT void JNICALL Java_android_atl_ATLCanvas_native_1clipRect(JNIEnv *env, jclass class, jlong canvas, jfloat left, jfloat top, jfloat right, jfloat bottom, jboolean difference)
{
	if (canvas)
		gfx_canvas_clip_rect(_PTR(canvas), left, top, right, bottom, difference ? GFX_CLIP_DIFFERENCE : GFX_CLIP_INTERSECT);
}
