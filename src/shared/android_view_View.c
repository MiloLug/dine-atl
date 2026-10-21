#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_view_View.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "startup.h"
#include "view.h"

void atl_view_traits_get(JNIEnv *env, jobject view, struct ui_view_traits *traits)
{
	jclass class = _CLASS(view);

	traits->draws = _METHOD(class, "onDraw", "(Landroid/graphics/Canvas;)V") != handle_cache.view.onDraw
	             || _METHOD(class, "draw", "(Landroid/graphics/Canvas;)V") != handle_cache.view.draw
	             || _METHOD(class, "dispatchDraw", "(Landroid/graphics/Canvas;)V") != handle_cache.view.dispatchDraw;
	traits->scrolls = _METHOD(class, "computeScroll", "()V") != handle_cache.view.computeScroll;
	traits->generic_motion = _METHOD(class, "onGenericMotionEvent", "(Landroid/view/MotionEvent;)Z") != handle_cache.view.onGenericMotionEvent
	                      || _METHOD(class, "dispatchGenericMotionEvent", "(Landroid/view/MotionEvent;)Z") != handle_cache.view.dispatchGenericMotionEvent;
	traits->hovers = _METHOD(class, "dispatchHoverEvent", "(Landroid/view/MotionEvent;)Z") != handle_cache.view.dispatchHoverEvent;
}

char *atl_view_class_name(JNIEnv *env, jobject view)
{
	jclass class = _CLASS(view);
	jstring name_jstr = (*env)->CallObjectMethod(env, class, _METHOD_OF(class, "getName", "()Ljava/lang/String;"));
	char *name = jstring_to_utf8(env, name_jstr);
	(*env)->DeleteLocalRef(env, name_jstr);
	(*env)->DeleteLocalRef(env, class);
	return name;
}

JNIEXPORT jlong JNICALL Java_android_view_View_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	struct ui_view_traits traits;
	atl_view_traits_get(env, this, &traits);
	char *name = atl_view_class_name(env, this);
	struct ui_view *view = ui_view_new((struct atl_ref *)_WEAK_REF(this), name, &traits);
	free(name);
	return _INTPTR(view);
}

static void view_free(void *view)
{
	ui_view_free(view);
}

// on the FinalizerDaemon thread; after loop_main_quit the view stays allocated
JNIEXPORT void JNICALL Java_android_view_View_native_1destructor(JNIEnv *env, jobject this, jlong widget_ptr)
{
	if (widget_ptr) // 0: the constructor threw first
		loop_main_post(view_free, _PTR(widget_ptr));
}

JNIEXPORT void JNICALL Java_android_view_View_native_1setLayoutParams(JNIEnv *env, jobject this, jlong widget_ptr, jint width, jint height, jint gravity, jfloat weight,
                                                                      jint leftMargin, jint topMargin, jint rightMargin, jint bottomMargin)
{
	ui_view_set_layout_params(_PTR(widget_ptr), &(struct ui_layout_params){
		.width = width,
		.height = height,
		.gravity = gravity,
		.weight = weight,
		.margin_left = leftMargin,
		.margin_top = topMargin,
		.margin_right = rightMargin,
		.margin_bottom = bottomMargin,
	});
}

JNIEXPORT void JNICALL Java_android_view_View_native_1setPadding(JNIEnv *env, jobject this, jlong widget_ptr, jint left, jint top, jint right, jint bottom)
{
	ui_view_set_padding(_PTR(widget_ptr), left, top, right, bottom);
}

JNIEXPORT void JNICALL Java_android_view_View_native_1requestLayout(JNIEnv *env, jobject this, jlong widget_ptr)
{
	ui_view_request_layout(_PTR(widget_ptr));
}

JNIEXPORT void JNICALL Java_android_view_View_native_1setBackgroundColor(JNIEnv *env, jobject this, jlong widget_ptr, jint color)
{
	ui_view_set_background_color(_PTR(widget_ptr), color);
}

JNIEXPORT void JNICALL Java_android_view_View_native_1setBackgroundDrawable(JNIEnv *env, jobject this, jlong widget_ptr, jlong drawable_ptr)
{
	ui_view_set_background_drawable(_PTR(widget_ptr), _PTR(drawable_ptr));
}

JNIEXPORT void JNICALL Java_android_view_View_native_1setVisibility(JNIEnv *env, jobject this, jlong widget_ptr, jint visibility, jfloat alpha, jboolean enabled)
{
	ui_view_set_visibility(_PTR(widget_ptr), visibility, alpha, enabled);
}

JNIEXPORT void JNICALL Java_android_view_View_nativeInvalidate(JNIEnv *env, jclass class, jlong widget_ptr)
{
	ui_view_invalidate(_PTR(widget_ptr));
}

JNIEXPORT void JNICALL Java_android_view_View_native_1childrenMoved(JNIEnv *env, jobject this, jlong widget_ptr)
{
	ui_view_children_moved(_PTR(widget_ptr));
}

JNIEXPORT void JNICALL Java_android_view_View_native_1measure(JNIEnv *env, jobject this, jlong widget_ptr, jint width_spec, jint height_spec)
{
	int width, height;
	ui_view_measure(_PTR(widget_ptr), width_spec, height_spec, &width, &height);
	(*env)->CallVoidMethod(env, this, handle_cache.view.setMeasuredDimension, width, height);
}

JNIEXPORT void JNICALL Java_android_view_View_native_1scrollTo(JNIEnv *env, jobject this, jlong widget_ptr, jint x, jint y, jboolean animated)
{
	ui_view_scroll_to(_PTR(widget_ptr), x, y, animated);
}

JNIEXPORT void JNICALL Java_android_view_View_native_1setFocused(JNIEnv *env, jobject this, jlong widget_ptr, jboolean focused)
{
	ui_view_set_focused(_PTR(widget_ptr), focused);
}

JNIEXPORT void JNICALL Java_android_view_View_native_1layout(JNIEnv *env, jobject this, jlong widget_ptr, jint l, jint t, jint r, jint b)
{
	ui_view_layout(_PTR(widget_ptr), l, t, r, b, _GET_FLOAT_FIELD(this, "translationX"), _GET_FLOAT_FIELD(this, "translationY"));
}

JNIEXPORT void JNICALL Java_android_view_View_getWindowVisibleDisplayFrame(JNIEnv *env, jobject this, jobject rect)
{
	int width, height;
	ui_window_get_size(atl_window, &width, &height);
	_SET_INT_FIELD(rect, "left", 0);
	_SET_INT_FIELD(rect, "top", 0);
	_SET_INT_FIELD(rect, "right", width);
	_SET_INT_FIELD(rect, "bottom", height);
}
