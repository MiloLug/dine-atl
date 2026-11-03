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

#include "app.h"
#include "nav.h"
#include "upcall.h"

// AOSP's MIN_GESTURE_EXCLUSION_LIMIT_DP: per side edge, granted from the bottom-most rect up
#define EXCLUSION_LIMIT_DP 200

static struct atl_window_insets insets_get(struct atl_app *app)
{
	pthread_mutex_lock(&app->insets_lock);
	struct atl_window_insets current = app->insets;
	pthread_mutex_unlock(&app->insets_lock);
	return current;
}

static float px_per_dp(void)
{
	return atl_screen_get()->density_dpi / 160.f;
}

static int span_compare(const void *a, const void *b)
{
	float d = ((const struct atl_span *)a)->top - ((const struct atl_span *)b)->top;
	return d < 0 ? -1 : d > 0;
}

/* The rects' vertical extents within the strip [left, right) between top and bottom, merged, in dp,
 * cut to the limit from the bottom up. */
static int edge_spans(struct atl_app *app, int left, int right, int top, int bottom, struct atl_span *out)
{
	if (left >= right || top >= bottom) // no strip on this edge
		return 0;
	struct atl_span *spans = malloc((app->n_exclusion_rects + 1) * sizeof(*spans));
	int n = 0;
	for (int i = 0; i < app->n_exclusion_rects; i++) {
		const int *rect = &app->exclusion_rects[i * 4];
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

static void exclusion_update(struct atl_app *app)
{
	struct atl_window_insets current = insets_get(app);
	const struct atl_edges *strip = &current.system_gestures;
	float scale = px_per_dp();
	int width = atl_screen_get()->width, height = atl_screen_get()->height;
	int top = roundf(strip->top * scale), bottom = height - roundf(strip->bottom * scale);
	struct atl_gesture_exclusion exclusion = {0};
	exclusion.left_count = edge_spans(app, 0, roundf(strip->left * scale), top, bottom, exclusion.left);
	exclusion.right_count = edge_spans(app, width - roundf(strip->right * scale), width, top, bottom, exclusion.right);
	if (!memcmp(&exclusion, &app->exclusion_sent, sizeof(exclusion)))
		return;
	app->exclusion_sent = exclusion;
	host_gesture_exclusion_changed(app, &exclusion);
}

static void insets_changed(void *data)
{
	struct atl_app *app = data;
	JNIEnv *env = get_jni_env();
	(*env)->PushLocalFrame(env, 8); // posted callbacks run in the base JNI frame, which never pops
	jobject window = activity_shown_window(env, app);
	if (window) {
		(*env)->CallVoidMethod(env, window, handle_cache.window.dispatchInsets);
		exit_on_exception(env);
	}
	(*env)->PopLocalFrame(env, NULL);
	exclusion_update(app);
}

void atl_nav_set_insets(struct atl_app *app, const struct atl_window_insets *new_insets)
{
	pthread_mutex_lock(&app->insets_lock);
	app->insets = *new_insets;
	pthread_mutex_unlock(&app->insets_lock);
	if (app->running) // before the start: the first activity shown reads them
		loop_main_post(app->loop, insets_changed, app);
}

JNIEXPORT jintArray JNICALL Java_android_view_Window_get_1insets(JNIEnv *env, jobject this)
{
	struct atl_window_insets current = insets_get(atl_app_of_window(env, this));
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
	struct atl_app *app = atl_app_of_window(env, this);
	if (!activity_window_shown(env, app, this))
		return;
	app->n_exclusion_rects = (*env)->GetArrayLength(env, rects) / 4;
	app->exclusion_rects = realloc(app->exclusion_rects, (app->n_exclusion_rects * 4 + 1) * sizeof(*app->exclusion_rects));
	(*env)->GetIntArrayRegion(env, rects, 0, app->n_exclusion_rects * 4, app->exclusion_rects);
	exclusion_update(app);
}
