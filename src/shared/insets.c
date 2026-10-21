#include <math.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_view_Window.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/host.h"
#include "src/slots/loop.h"

#include "nav.h"
#include "upcall.h"

// AOSP's MIN_GESTURE_EXCLUSION_LIMIT_DP: per side edge, granted from the bottom-most rect up
#define EXCLUSION_LIMIT_DP 200

static pthread_mutex_t insets_lock = PTHREAD_MUTEX_INITIALIZER;
static struct atl_window_insets insets; // set from any thread

// the Android main thread's
static int *exclusion_rects; // px in the window: left, top, right, bottom per rect
static int n_exclusion_rects;
static struct atl_gesture_exclusion exclusion_sent;

static struct atl_window_insets insets_get(void)
{
	pthread_mutex_lock(&insets_lock);
	struct atl_window_insets current = insets;
	pthread_mutex_unlock(&insets_lock);
	return current;
}

static float px_per_dp(void)
{
	return atl_window_get()->density_dpi / 160.f;
}

static int span_compare(const void *a, const void *b)
{
	float d = ((const struct atl_span *)a)->top - ((const struct atl_span *)b)->top;
	return d < 0 ? -1 : d > 0;
}

/* The rects' vertical extents within the strip [left, right) between top and bottom, merged, in dp,
 * cut to the limit from the bottom up. */
static int edge_spans(int left, int right, int top, int bottom, struct atl_span *out)
{
	if (left >= right || top >= bottom) // no strip on this edge
		return 0;
	struct atl_span *spans = malloc((n_exclusion_rects + 1) * sizeof(*spans));
	int n = 0;
	for (int i = 0; i < n_exclusion_rects; i++) {
		const int *rect = &exclusion_rects[i * 4];
		int span_top = rect[1] > top ? rect[1] : top;
		int span_bottom = rect[3] < bottom ? rect[3] : bottom;
		if (rect[0] < right && rect[2] > left && span_top < span_bottom)
			spans[n++] = (struct atl_span){span_top, span_bottom};
	}
	qsort(spans, n, sizeof(*spans), span_compare);
	int merged = 0;
	for (int i = 0; i < n; i++) {
		if (merged && spans[i].top <= spans[merged - 1].bottom) {
			if (spans[i].bottom > spans[merged - 1].bottom)
				spans[merged - 1].bottom = spans[i].bottom;
		} else {
			spans[merged++] = spans[i];
		}
	}
	float scale = px_per_dp();
	float remaining = EXCLUSION_LIMIT_DP * scale;
	int first = merged;
	while (first > 0 && remaining > 0 && merged - first < ATL_EXCLUSION_SPANS_MAX) {
		struct atl_span *span = &spans[--first];
		float height = span->bottom - span->top;
		if (height > remaining)
			span->top = span->bottom - remaining;
		remaining -= height;
	}
	int count = merged - first;
	for (int i = 0; i < count; i++)
		out[i] = (struct atl_span){roundf(spans[first + i].top / scale), roundf(spans[first + i].bottom / scale)};
	free(spans);
	return count;
}

static void exclusion_update(void)
{
	struct atl_window_insets current = insets_get();
	const struct atl_edges *strip = &current.system_gestures;
	float scale = px_per_dp();
	int width = atl_window_get()->width, height = atl_window_get()->height;
	int top = roundf(strip->top * scale), bottom = height - roundf(strip->bottom * scale);
	struct atl_gesture_exclusion exclusion = {0};
	exclusion.left_count = edge_spans(0, roundf(strip->left * scale), top, bottom, exclusion.left);
	exclusion.right_count = edge_spans(width - roundf(strip->right * scale), width, top, bottom, exclusion.right);
	if (!memcmp(&exclusion, &exclusion_sent, sizeof(exclusion)))
		return;
	exclusion_sent = exclusion;
	host_gesture_exclusion_changed(&exclusion);
}

static void insets_changed(void *data)
{
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 8); // posted callbacks run in the base JNI frame, which never pops
	jobject window = activity_shown_window(env);
	if (window) {
		(*env)->CallVoidMethod(env, window, handle_cache.window.dispatchInsets);
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
	}
	(*env)->PopLocalFrame(env, NULL);
	exclusion_update();
}

void atl_nav_set_insets(const struct atl_window_insets *new_insets)
{
	pthread_mutex_lock(&insets_lock);
	insets = *new_insets;
	pthread_mutex_unlock(&insets_lock);
	loop_main_post(insets_changed, NULL); // before the start: the first activity shown reads them
}

JNIEXPORT jintArray JNICALL Java_android_view_Window_get_1insets(JNIEnv *env, jclass class)
{
	struct atl_window_insets current = insets_get();
	const struct atl_edges *edges[] = {&current.system_gestures, &current.mandatory_gestures, &current.navigation_bars};
	float scale = px_per_dp();
	jint px[12];
	for (int i = 0; i < 3; i++) {
		px[i * 4 + 0] = roundf(edges[i]->left * scale);
		px[i * 4 + 1] = roundf(edges[i]->top * scale);
		px[i * 4 + 2] = roundf(edges[i]->right * scale);
		px[i * 4 + 3] = roundf(edges[i]->bottom * scale);
	}
	jintArray array = (*env)->NewIntArray(env, 12);
	(*env)->SetIntArrayRegion(env, array, 0, 12, px);
	return array;
}

// the shown activity's window decides, as for the frame
JNIEXPORT void JNICALL Java_android_view_Window_set_1exclusion_1rects(JNIEnv *env, jobject this, jintArray rects)
{
	if (!activity_window_shown(env, this))
		return;
	n_exclusion_rects = (*env)->GetArrayLength(env, rects) / 4;
	exclusion_rects = realloc(exclusion_rects, (n_exclusion_rects * 4 + 1) * sizeof(*exclusion_rects));
	(*env)->GetIntArrayRegion(env, rects, 0, n_exclusion_rects * 4, exclusion_rects);
	exclusion_update();
}
