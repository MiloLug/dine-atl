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

static struct {
	pthread_mutex_t mutex;
	pthread_cond_t cond;
	pthread_t thread;
	bool bound;
	int64_t ready_at; // -1: no dispatch pending
	struct post *posts, *posts_tail;
	bool quit;
} main_loop = {.mutex = PTHREAD_MUTEX_INITIALIZER, .ready_at = -1};

void loop_main_prepare(void)
{
	init_cond(&main_loop.cond);
	main_loop.thread = pthread_self();
	main_loop.bound = true;
	main_loop.ready_at = 0;
}

bool loop_is_main_thread(void)
{
	return main_loop.bound && pthread_equal(pthread_self(), main_loop.thread);
}

void loop_main_schedule(int timeout_ms)
{
	pthread_mutex_lock(&main_loop.mutex);
	main_loop.ready_at = now_ms() + timeout_ms;
	pthread_mutex_unlock(&main_loop.mutex);
}

void loop_main_wake(void)
{
	pthread_mutex_lock(&main_loop.mutex);
	main_loop.ready_at = 0;
	pthread_cond_signal(&main_loop.cond);
	pthread_mutex_unlock(&main_loop.mutex);
}

void loop_main_post(void (*fn)(void *data), void *data)
{
	struct post *post = malloc(sizeof(*post));
	*post = (struct post){fn, data, NULL};
	pthread_mutex_lock(&main_loop.mutex);
	if (main_loop.posts_tail)
		main_loop.posts_tail->next = post;
	else
		main_loop.posts = post;
	main_loop.posts_tail = post;
	pthread_cond_signal(&main_loop.cond);
	pthread_mutex_unlock(&main_loop.mutex);
}

void loop_main_run(void)
{
	pthread_mutex_lock(&main_loop.mutex);
	while (!main_loop.quit) {
		if (!main_loop.posts && (main_loop.ready_at < 0 || now_ms() < main_loop.ready_at)) {
			wait_until(&main_loop.cond, &main_loop.mutex, main_loop.ready_at);
			continue;
		}
		struct post *posts = main_loop.posts;
		main_loop.posts = main_loop.posts_tail = NULL;
		bool dispatch = main_loop.ready_at >= 0 && now_ms() >= main_loop.ready_at;
		if (dispatch)
			main_loop.ready_at = -1;
		pthread_mutex_unlock(&main_loop.mutex);

		while (posts) {
			struct post *next = posts->next;
			posts->fn(posts->data);
			free(posts);
			posts = next;
		}
		if (dispatch)
			atl_upcall_looper_loop();

		pthread_mutex_lock(&main_loop.mutex);
	}
	pthread_mutex_unlock(&main_loop.mutex);
}

void loop_main_quit(void)
{
	pthread_mutex_lock(&main_loop.mutex);
	main_loop.quit = true;
	pthread_cond_signal(&main_loop.cond);
	pthread_mutex_unlock(&main_loop.mutex);
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
