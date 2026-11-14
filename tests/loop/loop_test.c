/* The loop slot's rules for a main loop, checked on the backend alone: this file stands in for the
 * shared code it calls. Built with the poll or the glib backend's loop.c (LOOP_GLIB). Each case
 * starts inside a dispatch, where nativePollOnce makes its calls. */

#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>

#include "src/slots/loop.h"

#ifdef LOOP_GLIB
#include <glib.h>

#include "src/libandroid/looper.h"

ALooper *ALooper_prepare(int opts) { return NULL; }
void ALooper_wake(ALooper *looper) {}
bool ALooper_isPolling(ALooper *looper) { return false; }
int ALooper_pollOnce(int timeout_ms, int *fd, int *events, void **data) { return 0; }
#endif

#define LATE_MS 1000 // far below the 5 s a lost wake waits

static struct loop_main *loop;
static int dispatches;
static void (*step)(void); // what the next dispatch runs
static bool done;
static int64_t mark;
static int failures;

static int64_t now_ms(void)
{
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static void check(bool ok, const char *what)
{
	if (!ok) {
		failures++;
		printf("FAIL: %s\n", what);
	}
}

// ends run() from a dispatch or a post
static void finish(void)
{
	done = true;
#ifndef LOOP_GLIB
	loop_main_quit(loop);
#endif
}

void atl_upcall_looper_loop(void)
{
	void (*fn)(void) = step;
	step = NULL;
	dispatches++;
	if (fn)
		fn();
}

/* Runs the loop until finish(); `first` runs in its first dispatch. glib has one main loop per
 * process; poll's can't run again after its quit, so each case gets a new one. */
static void run(void (*first)(void))
{
	dispatches = 0;
	done = false;
	step = first;
#ifdef LOOP_GLIB
	if (loop)
		loop_main_wake(loop);
	else
		loop = loop_main_prepare();
	while (!done)
		g_main_context_iteration(NULL, TRUE);
#else
	loop = loop_main_prepare();
	loop_main_run(loop);
#endif
}

static void stamp_and_finish(void)
{
	mark = now_ms() - mark;
	finish();
}

static void schedule_100(void)
{
	mark = now_ms();
	step = stamp_and_finish;
	loop_main_schedule(loop, 100);
}

static void test_schedule_waits(void)
{
	run(schedule_100);
	check(mark >= 100 && mark < LATE_MS, "a dispatch scheduled in 100 ms comes then");
	check(dispatches == 2, "one dispatch per schedule");
}

// MessageQueue.next() leaves its monitor before nativePollOnce: a wake can come before the schedule
static void wake_then_schedule(void)
{
	mark = now_ms();
	step = stamp_and_finish;
	loop_main_wake(loop);
	loop_main_schedule(loop, 5000);
}

static void test_wake_survives_a_later_schedule(void)
{
	run(wake_then_schedule);
	check(mark < LATE_MS, "a wake before the schedule still dispatches at once");
}

static void post_finish(void *data)
{
	*(pthread_t *)data = pthread_self();
	finish();
}

static void *post_from_thread(void *data)
{
	check(loop_main_post(loop, post_finish, data), "a post from another thread is taken");
	return NULL;
}

static pthread_t poster;
static pthread_t ran_on;

static void start_poster(void)
{
	mark = now_ms();
	pthread_create(&poster, NULL, post_from_thread, &ran_on);
}

static void test_post_from_another_thread(void)
{
	run(start_poster);
	pthread_join(poster, NULL);
	check(now_ms() - mark < LATE_MS, "a post runs at once");
	check(pthread_equal(ran_on, pthread_self()), "a post runs on the loop's thread");
}

static bool post_ran;

static void note_post(void *data)
{
	post_ran = true;
}

static void finish_after_the_post(void)
{
	check(post_ran, "the post runs before the dispatch a yield asked for");
	finish();
}

static void yield_with_a_post(void)
{
	check(!loop_main_yield(loop), "nothing waits: no yield");
	post_ran = false;
	loop_main_post(loop, note_post, NULL);
	step = finish_after_the_post;
	mark = now_ms();
	check(loop_main_yield(loop), "a queued post makes the dispatch yield");
}

static void test_yield(void)
{
	run(yield_with_a_post);
	check(now_ms() - mark < LATE_MS, "the dispatch after a yield comes at once");
	check(dispatches == 2, "one dispatch after the yield");
}

#ifndef LOOP_GLIB
static void never(void *data)
{
	check(false, "a post refused after the quit still ran");
}

static void quit_then_post(void)
{
	loop_main_quit(loop);
	check(!loop_main_post(loop, never, NULL), "a post after the quit is refused");
}

static void test_no_post_after_quit(void)
{
	run(quit_then_post);
	check(!loop_main_post(NULL, never, NULL), "a post to no loop is refused");
}
#endif

int main(void)
{
	alarm(30); // a hang is a failure
	test_schedule_waits();
	test_wake_survives_a_later_schedule();
	test_post_from_another_thread();
	test_yield();
#ifndef LOOP_GLIB
	test_no_post_after_quit();
#endif
	if (!failures)
		printf("PASS\n");
	return failures != 0;
}
