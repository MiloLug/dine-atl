#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_widget_TextView.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/ui.h"

#include "view.h"

static struct ui_view *text_view(JNIEnv *env, jobject this)
{
	return _PTR(_GET_LONG_FIELD(this, "widget"));
}

JNIEXPORT jlong JNICALL Java_android_widget_TextView_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	char *text = attribute_set_get_string(env, attrs, "text", NULL);

	struct ui_view_traits traits;
	atl_view_traits_get(env, this, &traits);
	char *name = atl_view_class_name(env, this);
	struct ui_view *view = ui_text_new((struct atl_ref *)_WEAK_REF(this), name, &traits, text);
	free(name);
	free(text);

	return _INTPTR(view);
}

/* runs: 7 ints (start, end, set, color, background, style, family) and 3 floats (size, baseline
 * shift, scale x) a run, as TextView packs them; NULL: none */
JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setText(JNIEnv *env, jobject this, jobject charseq, jintArray run_ints, jfloatArray run_floats)
{
	char *text = jstring_to_utf8(env, charseq);
	size_t n = run_ints ? (*env)->GetArrayLength(env, run_ints) / 7 : 0;
	struct ui_text_run *runs = n ? calloc(n, sizeof(*runs)) : NULL;
	if (n) {
		jint *ints = (*env)->GetIntArrayElements(env, run_ints, NULL);
		jfloat *floats = (*env)->GetFloatArrayElements(env, run_floats, NULL);
		for (size_t i = 0; i < n; i++) {
			const jint *in = ints + i * 7;
			runs[i] = (struct ui_text_run){.start = in[0], .end = in[1], .set = in[2], .color = in[3], .background = in[4],
			                               .style = in[5], .family = in[6], .size = floats[i * 3], .baseline_shift = floats[i * 3 + 1], .scale_x = floats[i * 3 + 2]};
		}
		(*env)->ReleaseFloatArrayElements(env, run_floats, floats, JNI_ABORT);
		(*env)->ReleaseIntArrayElements(env, run_ints, ints, JNI_ABORT);
	}
	ui_text_set_styled(text_view(env, this), text ?: "", runs, n);
	free(runs);
	free(text);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setTextColor(JNIEnv *env, jobject this, jint color)
{
	ui_text_set_color(text_view(env, this), color);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setTextSize(JNIEnv *env, jobject this, jfloat size)
{
	ui_text_set_size(text_view(env, this), size);
}

JNIEXPORT jfloat JNICALL Java_android_widget_TextView_native_1getTextSize(JNIEnv *env, jobject this)
{
	return ui_text_get_size(text_view(env, this));
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setLines(JNIEnv *env, jobject this, jint min, jint max)
{
	ui_text_set_lines(text_view(env, this), min, max);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setTextStyle(JNIEnv *env, jobject this, jint style)
{
	ui_text_set_style(text_view(env, this), style);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setCompoundDrawables(JNIEnv *env, jobject this, jlong widget_ptr, jlong left, jlong top, jlong right, jlong bottom)
{
	ui_text_set_drawables(_PTR(widget_ptr), _PTR(left), _PTR(top), _PTR(right), _PTR(bottom));
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setSelectable(JNIEnv *env, jobject this, jboolean selectable)
{
	ui_text_set_selectable(text_view(env, this), selectable);
}
