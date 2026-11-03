#include <CoreFoundation/CoreFoundation.h>
#include <pthread.h>
#include <stdlib.h>

#include "src/shared/upcall.h"
#include "src/slots/loop.h"

/* Loops on CoreFoundation run loops: a main loop is the run loop of the thread that calls
 * loop_main_prepare, so it also works on the UIKit main thread. */

// a timer that is not due; repeating, because a one-shot timer is invalidated after it fires
#define IDLE_INTERVAL 1.0e10

struct post {
	void (*fn)(void *data);
	void *data;
	struct post *next;
};

struct loop_main {
	pthread_mutex_t mutex;
	CFRunLoopRef loop;
	CFRunLoopSourceRef source;
	CFRunLoopTimerRef timer;
	bool dispatch_pending;
	struct post *posts, *posts_tail;
	bool quit;
};

static __thread struct loop_main *current;

static void dispatch(struct loop_main *loop)
{
	CFRunLoopTimerSetNextFireDate(loop->timer, CFAbsoluteTimeGetCurrent() + IDLE_INTERVAL);
	atl_upcall_looper_loop();
}

static void main_perform(void *info)
{
	struct loop_main *loop = info;
	pthread_mutex_lock(&loop->mutex);
	struct post *posts = loop->posts;
	loop->posts = loop->posts_tail = NULL;
	bool pending = loop->dispatch_pending;
	loop->dispatch_pending = false;
	pthread_mutex_unlock(&loop->mutex);

	while (posts) {
		struct post *next = posts->next;
		posts->fn(posts->data);
		free(posts);
		posts = next;
	}
	if (pending)
		dispatch(loop);
}

static void main_timer_fired(CFRunLoopTimerRef timer, void *info)
{
	dispatch(info);
}

static void signal_main(struct loop_main *loop)
{
	CFRunLoopSourceSignal(loop->source);
	CFRunLoopWakeUp(loop->loop);
}

struct loop_main *loop_main_prepare(void)
{
	struct loop_main *loop = calloc(1, sizeof(*loop));
	pthread_mutex_init(&loop->mutex, NULL);
	loop->loop = (CFRunLoopRef)CFRetain(CFRunLoopGetCurrent());
	CFRunLoopSourceContext context = {.info = loop, .perform = main_perform};
	loop->source = CFRunLoopSourceCreate(NULL, 0, &context);
	CFRunLoopTimerContext timer_context = {.info = loop};
	loop->timer = CFRunLoopTimerCreate(NULL, CFAbsoluteTimeGetCurrent() + IDLE_INTERVAL, IDLE_INTERVAL, 0, 0, main_timer_fired, &timer_context);
	CFRunLoopAddSource(loop->loop, loop->source, kCFRunLoopCommonModes);
	CFRunLoopAddTimer(loop->loop, loop->timer, kCFRunLoopCommonModes);
	current = loop;
	loop_main_wake(loop);
	return loop;
}

struct loop_main *loop_main_current(void)
{
	return current;
}

void loop_main_schedule(struct loop_main *loop, int timeout_ms)
{
	CFRunLoopTimerSetNextFireDate(loop->timer, CFAbsoluteTimeGetCurrent() + timeout_ms / 1000.0);
}

void loop_main_wake(struct loop_main *loop)
{
	pthread_mutex_lock(&loop->mutex);
	loop->dispatch_pending = true;
	pthread_mutex_unlock(&loop->mutex);
	signal_main(loop);
}

bool loop_main_post(struct loop_main *loop, void (*fn)(void *data), void *data)
{
	if (!loop)
		return false;
	pthread_mutex_lock(&loop->mutex);
	if (loop->quit) {
		pthread_mutex_unlock(&loop->mutex);
		return false;
	}
	struct post *post = malloc(sizeof(*post));
	*post = (struct post){fn, data, NULL};
	if (loop->posts_tail)
		loop->posts_tail->next = post;
	else
		loop->posts = post;
	loop->posts_tail = post;
	pthread_mutex_unlock(&loop->mutex);
	signal_main(loop);
	return true;
}

// CFRunLoopStop is lost on a run loop that isn't running yet: the quit also signals the source,
// and each handled source returns here to read the flag
void loop_main_run(struct loop_main *loop)
{
	for (;;) {
		pthread_mutex_lock(&loop->mutex);
		bool quit = loop->quit;
		pthread_mutex_unlock(&loop->mutex);
		if (quit)
			return;
		CFRunLoopRunInMode(kCFRunLoopDefaultMode, IDLE_INTERVAL, true);
	}
}

void loop_main_quit(struct loop_main *loop)
{
	pthread_mutex_lock(&loop->mutex);
	loop->quit = true;
	pthread_mutex_unlock(&loop->mutex);
	CFRunLoopStop(loop->loop);
	signal_main(loop);
}

struct loop_worker {
	CFRunLoopRef loop;
	CFRunLoopSourceRef source;
	pthread_mutex_t mutex;
	bool woken;
	bool polling;
};

static void worker_perform(void *info) {}

struct loop_worker *loop_worker_new(void)
{
	struct loop_worker *worker = calloc(1, sizeof(*worker));
	pthread_mutex_init(&worker->mutex, NULL);
	worker->loop = (CFRunLoopRef)CFRetain(CFRunLoopGetCurrent());
	CFRunLoopSourceContext context = {.perform = worker_perform};
	worker->source = CFRunLoopSourceCreate(NULL, 0, &context);
	CFRunLoopAddSource(worker->loop, worker->source, kCFRunLoopDefaultMode);
	return worker;
}

// may run on another thread (the finalizer), so it doesn't touch the worker's run loop beyond these
void loop_worker_free(struct loop_worker *worker)
{
	CFRunLoopSourceInvalidate(worker->source);
	CFRelease(worker->source);
	CFRelease(worker->loop);
	pthread_mutex_destroy(&worker->mutex);
	free(worker);
}

void loop_worker_poll(struct loop_worker *worker, int timeout_ms)
{
	pthread_mutex_lock(&worker->mutex);
	bool woken = worker->woken;
	worker->polling = !woken;
	pthread_mutex_unlock(&worker->mutex);

	// a wake between the check and the run leaves the source signalled, so the run returns at once
	if (!woken)
		CFRunLoopRunInMode(kCFRunLoopDefaultMode, timeout_ms < 0 ? IDLE_INTERVAL : timeout_ms / 1000.0, true);

	pthread_mutex_lock(&worker->mutex);
	worker->woken = false;
	worker->polling = false;
	pthread_mutex_unlock(&worker->mutex);
}

void loop_worker_wake(struct loop_worker *worker)
{
	pthread_mutex_lock(&worker->mutex);
	worker->woken = true;
	pthread_mutex_unlock(&worker->mutex);
	CFRunLoopSourceSignal(worker->source);
	CFRunLoopWakeUp(worker->loop);
}

bool loop_worker_is_idling(struct loop_worker *worker)
{
	pthread_mutex_lock(&worker->mutex);
	bool polling = worker->polling;
	pthread_mutex_unlock(&worker->mutex);
	return polling;
}
