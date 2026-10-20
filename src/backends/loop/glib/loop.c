#include <glib.h>
#include <stdbool.h>

#include "src/libandroid/looper.h"
#include "src/shared/upcall.h"
#include "src/slots/loop.h"

// also read by legacy code (Bitmap, libandroid's native_window)
GThread *main_thread_id;

static gboolean dispatch_func(GSource *source, GSourceFunc callback, gpointer user_data)
{
	g_source_set_ready_time(source, -1); // clear previous timeout
	atl_upcall_looper_loop();
	return G_SOURCE_CONTINUE;
}
static GSourceFuncs source_funcs = {
	.dispatch = dispatch_func,
};
static GSource *source = NULL;

void loop_main_prepare(void)
{
	main_thread_id = g_thread_self();
	// app native code may use the main thread's ALooper
	ALooper_prepare(0);

	source = g_source_new(&source_funcs, sizeof(GSource));
	g_source_set_ready_time(source, 0);
	g_source_attach(source, NULL);
}

bool loop_is_main_thread(void)
{
	return g_thread_self() == main_thread_id;
}

void loop_main_schedule(int timeout_ms)
{
	g_source_set_ready_time(source, g_source_get_time(source) + timeout_ms * 1000L);
}

void loop_main_wake(void)
{
	g_source_set_ready_time(source, 0);
	g_main_context_wakeup(NULL); // needed when called from another thread
}

struct post {
	void (*fn)(void *data);
	void *data;
};

static gboolean run_post(gpointer user_data)
{
	struct post *post = user_data;
	post->fn(post->data);
	g_free(post);
	return G_SOURCE_REMOVE;
}

// no quit: the gtk host's GApplication owns the loop
bool loop_main_post(void (*fn)(void *data), void *data)
{
	if (!source)
		return false;
	struct post *post = g_new(struct post, 1);
	*post = (struct post){fn, data};
	g_idle_add(run_post, post);
	return true;
}

/* libandroid's ALooper (libutils' Looper): app native code under native=bionic attaches fds to a
 * Java thread's looper. */

struct loop_worker *loop_worker_new(void)
{
	return (struct loop_worker *)ALooper_prepare(0);
}

void loop_worker_free(struct loop_worker *worker) {}

void loop_worker_poll(struct loop_worker *worker, int timeout_ms)
{
	ALooper_pollOnce(timeout_ms, NULL, NULL, NULL);
}

void loop_worker_wake(struct loop_worker *worker)
{
	ALooper_wake((ALooper *)worker);
}

bool loop_worker_is_idling(struct loop_worker *worker)
{
	return ALooper_isPolling((ALooper *)worker);
}
