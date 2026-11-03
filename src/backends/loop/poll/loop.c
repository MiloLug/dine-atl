#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <time.h>

#include "src/shared/upcall.h"
#include "src/slots/loop.h"

/* Mutex + condition variable loops for headless builds. Deadlines are CLOCK_MONOTONIC ms. */

static int64_t now_ms(void)
{
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static void init_cond(pthread_cond_t *cond)
{
	pthread_condattr_t attr;
	pthread_condattr_init(&attr);
	pthread_condattr_setclock(&attr, CLOCK_MONOTONIC);
	pthread_cond_init(cond, &attr);
	pthread_condattr_destroy(&attr);
}

// returns false once the deadline has passed; deadline -1 waits without one
static bool wait_until(pthread_cond_t *cond, pthread_mutex_t *mutex, int64_t deadline)
{
	if (deadline < 0) {
		pthread_cond_wait(cond, mutex);
		return true;
	}
	if (now_ms() >= deadline)
		return false;
	struct timespec ts = {.tv_sec = deadline / 1000, .tv_nsec = (deadline % 1000) * 1000000};
	pthread_cond_timedwait(cond, mutex, &ts);
	return true;
}

struct post {
	void (*fn)(void *data);
	void *data;
	struct post *next;
};

struct loop_main {
	pthread_mutex_t mutex;
	pthread_cond_t cond;
	int64_t ready_at; // -1: no dispatch pending
	struct post *posts, *posts_tail;
	bool quit;
};

static __thread struct loop_main *current;

struct loop_main *loop_main_prepare(void)
{
	struct loop_main *loop = calloc(1, sizeof(*loop));
	pthread_mutex_init(&loop->mutex, NULL);
	init_cond(&loop->cond);
	current = loop;
	return loop;
}

struct loop_main *loop_main_current(void)
{
	return current;
}

void loop_main_schedule(struct loop_main *loop, int timeout_ms)
{
	pthread_mutex_lock(&loop->mutex);
	loop->ready_at = now_ms() + timeout_ms;
	pthread_mutex_unlock(&loop->mutex);
}

void loop_main_wake(struct loop_main *loop)
{
	pthread_mutex_lock(&loop->mutex);
	loop->ready_at = 0;
	pthread_cond_signal(&loop->cond);
	pthread_mutex_unlock(&loop->mutex);
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
	pthread_cond_signal(&loop->cond);
	pthread_mutex_unlock(&loop->mutex);
	return true;
}

void loop_main_run(struct loop_main *loop)
{
	pthread_mutex_lock(&loop->mutex);
	while (!loop->quit) {
		if (!loop->posts && (loop->ready_at < 0 || now_ms() < loop->ready_at)) {
			wait_until(&loop->cond, &loop->mutex, loop->ready_at);
			continue;
		}
		struct post *posts = loop->posts;
		loop->posts = loop->posts_tail = NULL;
		bool dispatch = loop->ready_at >= 0 && now_ms() >= loop->ready_at;
		if (dispatch)
			loop->ready_at = -1;
		pthread_mutex_unlock(&loop->mutex);

		while (posts) {
			struct post *next = posts->next;
			posts->fn(posts->data);
			free(posts);
			posts = next;
		}
		if (dispatch)
			atl_upcall_looper_loop();

		pthread_mutex_lock(&loop->mutex);
	}
	pthread_mutex_unlock(&loop->mutex);
}

void loop_main_quit(struct loop_main *loop)
{
	pthread_mutex_lock(&loop->mutex);
	loop->quit = true;
	pthread_cond_signal(&loop->cond);
	pthread_mutex_unlock(&loop->mutex);
}

struct loop_worker {
	pthread_mutex_t mutex;
	pthread_cond_t cond;
	bool woken;
	bool polling;
};

struct loop_worker *loop_worker_new(void)
{
	struct loop_worker *worker = calloc(1, sizeof(*worker));
	pthread_mutex_init(&worker->mutex, NULL);
	init_cond(&worker->cond);
	return worker;
}

void loop_worker_free(struct loop_worker *worker)
{
	pthread_cond_destroy(&worker->cond);
	pthread_mutex_destroy(&worker->mutex);
	free(worker);
}

void loop_worker_poll(struct loop_worker *worker, int timeout_ms)
{
	int64_t deadline = timeout_ms < 0 ? -1 : now_ms() + timeout_ms;
	pthread_mutex_lock(&worker->mutex);
	worker->polling = true;
	while (!worker->woken && wait_until(&worker->cond, &worker->mutex, deadline))
		;
	worker->woken = false;
	worker->polling = false;
	pthread_mutex_unlock(&worker->mutex);
}

void loop_worker_wake(struct loop_worker *worker)
{
	pthread_mutex_lock(&worker->mutex);
	worker->woken = true;
	pthread_cond_signal(&worker->cond);
	pthread_mutex_unlock(&worker->mutex);
}

bool loop_worker_is_idling(struct loop_worker *worker)
{
	pthread_mutex_lock(&worker->mutex);
	bool polling = worker->polling;
	pthread_mutex_unlock(&worker->mutex);
	return polling;
}
