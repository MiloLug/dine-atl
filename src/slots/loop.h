#ifndef SLOTS_LOOP_H
#define SLOTS_LOOP_H

#include <stdbool.h>

/* Loop slot: each app's Android main thread inside the platform's loop, and worker-thread loops.
 * A main loop dispatches through atl_upcall_looper_loop(). Main loops live as long as the
 * process. */

struct loop_main;

/* Binds the calling thread as an Android main thread and returns its loop; the first dispatch
 * comes as soon as the loop runs. The glib backend has one main loop per process. */
struct loop_main *loop_main_prepare(void);
/* The calling thread's main loop, NULL on any other thread. */
struct loop_main *loop_main_current(void);
/* Next dispatch after timeout_ms (> 0). The loop's own thread only. */
void loop_main_schedule(struct loop_main *loop, int timeout_ms);
/* Dispatch as soon as possible. Any thread. */
void loop_main_wake(struct loop_main *loop);
/* Runs fn(data) once on the loop's thread. Any thread. False, and fn never runs, for a NULL loop
 * and after loop_main_quit. */
bool loop_main_post(struct loop_main *loop, void (*fn)(void *data), void *data);
/* For hosts that own the main loop (not glib: the gtk host runs GApplication); returns after
 * loop_main_quit. */
void loop_main_run(struct loop_main *loop);
void loop_main_quit(struct loop_main *loop);

/* The calling thread's loop. */
struct loop_worker *loop_worker_new(void);
void loop_worker_free(struct loop_worker *worker);
/* Blocks until woken or timeout_ms passes (-1: no timeout). */
void loop_worker_poll(struct loop_worker *worker, int timeout_ms);
void loop_worker_wake(struct loop_worker *worker);
bool loop_worker_is_idling(struct loop_worker *worker);

#endif
