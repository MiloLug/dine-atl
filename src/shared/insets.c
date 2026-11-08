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
#include "windows.h"

// AOSP's MIN_GESTURE_EXCLUSION_LIMIT_DP: per side edge, granted from the bottom-most rect up
#define EXCLUSION_LIMIT_DP 200

struct atl_window_insets atl_app_insets(struct atl_app *app)
{
	pthread_mutex_lock(&app->insets_lock);
	struct atl_window_insets current = app->insets;
	pthread_mutex_unlock(&app->insets_lock);
	return current;
}

void atl_app_window_size(struct atl_app *app, int *width, int *height)
{
	pthread_mutex_lock(&app->insets_lock);
	*width = app->window_width;
	*height = app->window_height;
	pthread_mutex_unlock(&app->insets_lock);
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
static int edge_spans(const int *rects, int n_rects, int left, int right, int top, int bottom, struct atl_span *out)
{
	if (left >= right || top >= bottom) // no strip on this edge
		return 0;
	struct atl_span *spans = malloc((n_rects + 1) * sizeof(*spans));
	int n = 0;
	for (int i = 0; i < n_rects; i++) {
		const int *rect = &rects[i * 4];
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

void atl_exclusion_update(struct atl_app *app)
{
	struct atl_window_insets current = atl_app_insets(app);
	const struct atl_edges *strip = &current.system_gestures;
	float scale = px_per_dp();
	int width, height;
	atl_app_window_size(app, &width, &height);
	int top = roundf(strip->top * scale), bottom = height - roundf(strip->bottom * scale);
	struct atl_gesture_exclusion exclusion = {0};
	int n_rects;
	int *rects = windows_exclusion_rects(app, &n_rects);
	exclusion.left_count = edge_spans(rects, n_rects, 0, roundf(strip->left * scale), top, bottom, exclusion.left);
	exclusion.right_count = edge_spans(rects, n_rects, width - roundf(strip->right * scale), width, top, bottom, exclusion.right);
	free(rects);
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
	windows_dispatch_insets(app);
	atl_exclusion_update(app);
}

void atl_nav_set_insets(struct atl_app *app, const struct atl_window_insets *new_insets)
{
	pthread_mutex_lock(&app->insets_lock);
	app->insets = *new_insets;
	pthread_mutex_unlock(&app->insets_lock);
	if (app->running) // before the start: the first activity shown reads them
		loop_main_post(app->loop, insets_changed, app);
}

// the insets' dispatch requests the layout that takes the size
void atl_nav_set_window_size(struct atl_app *app, float width, float height)
{
	float scale = px_per_dp();
	pthread_mutex_lock(&app->insets_lock);
	app->window_width = roundf(width * scale);
	app->window_height = roundf(height * scale);
	pthread_mutex_unlock(&app->insets_lock);
	if (app->running)
		loop_main_post(app->loop, insets_changed, app);
}

static int overlap(int inset, int distance)
{
	return inset > distance ? inset - distance : 0;
}

/* A window takes the part of each edge's inset that its frame, px in the app's window, overlaps: a
 * window over the activities its placed frame (none before its first placement, width 0), the
 * activity's the room it lays out in (windows_area). */
JNIEXPORT jintArray JNICALL Java_android_view_Window_get_1insets(JNIEnv *env, jobject this, jlong subwindow, jint left, jint top, jint width, jint height)
{
	struct atl_app *app = atl_app_of_window(env, this);
	if (!subwindow) {
		left = top = 0;
		windows_area(app, NULL, &width, &height);
	}
	struct atl_window_insets current = atl_app_insets(app);
	const struct atl_edges *edges[] = {&current.system_gestures, &current.mandatory_gestures, &current.navigation_bars, &current.status_bars, &current.ime};
	enum { N_EDGES = sizeof(edges) / sizeof(*edges) };
	float scale = px_per_dp();
	int screen_width, screen_height;
	atl_app_window_size(app, &screen_width, &screen_height);
	jint px[N_EDGES * 4];
	for (int i = 0; i < N_EDGES; i++) {
		px[i * 4 + 0] = roundf(edges[i]->left * scale);
		px[i * 4 + 1] = roundf(edges[i]->top * scale);
		px[i * 4 + 2] = roundf(edges[i]->right * scale);
		px[i * 4 + 3] = roundf(edges[i]->bottom * scale);
		px[i * 4 + 0] = width ? overlap(px[i * 4 + 0], left) : 0;
		px[i * 4 + 1] = width ? overlap(px[i * 4 + 1], top) : 0;
		px[i * 4 + 2] = width ? overlap(px[i * 4 + 2], screen_width - left - width) : 0;
		px[i * 4 + 3] = width ? overlap(px[i * 4 + 3], screen_height - top - height) : 0;
	}
	jintArray array = (*env)->NewIntArray(env, N_EDGES * 4);
	(*env)->SetIntArrayRegion(env, array, 0, N_EDGES * 4, px);
	return array;
}

JNIEXPORT void JNICALL Java_android_view_Window_set_1exclusion_1rects(JNIEnv *env, jobject this, jlong subwindow, jintArray rects)
{
	struct atl_app *app = atl_app_of_window(env, this);
	struct atl_window_state *state = atl_window_state_of(env, app, this, subwindow);
	if (!state)
		return;
	state->n_exclusion_rects = (*env)->GetArrayLength(env, rects) / 4;
	state->exclusion_rects = realloc(state->exclusion_rects, (state->n_exclusion_rects * 4 + 1) * sizeof(*state->exclusion_rects));
	(*env)->GetIntArrayRegion(env, rects, 0, state->n_exclusion_rects * 4, state->exclusion_rects);
	atl_exclusion_update(app);
}
