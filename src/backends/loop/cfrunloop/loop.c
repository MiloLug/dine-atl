#include <CoreFoundation/CoreFoundation.h>
#include <pthread.h>
#include <stdlib.h>

#include "src/shared/upcall.h"
#include "src/slots/loop.h"

/* Loops on CoreFoundation run loops: the main loop is the run loop of the thread that calls
 * loop_main_prepare, so it also works on the UIKit main thread. */

// a timer that is not due; repeating, because a one-shot timer is invalidated after it fires
#define IDLE_INTERVAL 1.0e10

struct post {
	void (*fn)(void *data);
	void *data;
	struct post *next;
};

static struct {
	pthread_mutex_t mutex;
	pthread_t thread;
	bool bound;
	CFRunLoopRef loop;
	CFRunLoopSourceRef source;
	CFRunLoopTimerRef timer;
	bool dispatch_pending;
	struct post *posts, *posts_tail;
	bool quit;
} main_loop = {.mutex = PTHREAD_MUTEX_INITIALIZER};

static void dispatch(void)
{
	CFRunLoopTimerSetNextFireDate(main_loop.timer, CFAbsoluteTimeGetCurrent() + IDLE_INTERVAL);
	atl_upcall_looper_loop();
}

static void main_perform(void *info)
{
	pthread_mutex_lock(&main_loop.mutex);
	struct post *posts = main_loop.posts;
	main_loop.posts = main_loop.posts_tail = NULL;
	bool pending = main_loop.dispatch_pending;
	main_loop.dispatch_pending = false;
	pthread_mutex_unlock(&main_loop.mutex);

	while (posts) {
		struct post *next = posts->next;
		posts->fn(posts->data);
		free(posts);
		posts = next;
	}
	if (pending)
		dispatch();
}

static void main_timer_fired(CFRunLoopTimerRef timer, void *info)
{
	dispatch();
}

static void signal_main(void)
{
	CFRunLoopSourceSignal(main_loop.source);
	CFRunLoopWakeUp(main_loop.loop);
}

void loop_main_prepare(void)
{
	main_loop.thread = pthread_self();
	main_loop.loop = (CFRunLoopRef)CFRetain(CFRunLoopGetCurrent());
	CFRunLoopSourceContext context = {.perform = main_perform};
	main_loop.source = CFRunLoopSourceCreate(NULL, 0, &context);
	main_loop.timer = CFRunLoopTimerCreate(NULL, CFAbsoluteTimeGetCurrent() + IDLE_INTERVAL, IDLE_INTERVAL, 0, 0, main_timer_fired, NULL);
	CFRunLoopAddSource(main_loop.loop, main_loop.source, kCFRunLoopCommonModes);
	CFRunLoopAddTimer(main_loop.loop, main_loop.timer, kCFRunLoopCommonModes);
	pthread_mutex_lock(&main_loop.mutex);
	main_loop.bound = true;
	pthread_mutex_unlock(&main_loop.mutex);
	loop_main_wake();
}

bool loop_is_main_thread(void)
{
	return main_loop.bound && pthread_equal(pthread_self(), main_loop.thread);
}

void loop_main_schedule(int timeout_ms)
{
	CFRunLoopTimerSetNextFireDate(main_loop.timer, CFAbsoluteTimeGetCurrent() + timeout_ms / 1000.0);
}

void loop_main_wake(void)
{
	pthread_mutex_lock(&main_loop.mutex);
	main_loop.dispatch_pending = true;
	pthread_mutex_unlock(&main_loop.mutex);
	signal_main();
}

bool loop_main_post(void (*fn)(void *data), void *data)
{
	pthread_mutex_lock(&main_loop.mutex);
	if (!main_loop.bound || main_loop.quit) {
		pthread_mutex_unlock(&main_loop.mutex);
		return false;
	}
	struct post *post = malloc(sizeof(*post));
	*post = (struct post){fn, data, NULL};
	if (main_loop.posts_tail)
		main_loop.posts_tail->next = post;
	else
		main_loop.posts = post;
	main_loop.posts_tail = post;
	pthread_mutex_unlock(&main_loop.mutex);
	signal_main();
	return true;
}

void loop_main_run(void)
{
	CFRunLoopRun();
}

void loop_main_quit(void)
{
	pthread_mutex_lock(&main_loop.mutex);
	main_loop.quit = true;
	pthread_mutex_unlock(&main_loop.mutex);
	CFRunLoopStop(main_loop.loop);
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
