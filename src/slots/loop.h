#ifndef SLOTS_LOOP_H
#define SLOTS_LOOP_H

#include <stdbool.h>

/* Loop slot: the Android main thread inside the platform's loop, and worker-thread loops.
 * The main loop dispatches through atl_upcall_looper_loop(). */

/* Binds the calling thread as the Android main thread; the first dispatch comes as soon as the
 * loop runs. */
void loop_main_prepare(void);
bool loop_is_main_thread(void);
/* Next dispatch after timeout_ms (> 0). Main thread only. */
void loop_main_schedule(int timeout_ms);
/* Dispatch as soon as possible. Any thread. */
void loop_main_wake(void);
/* Runs fn(data) once on the main thread. Any thread. */
void loop_main_post(void (*fn)(void *data), void *data);
/* For hosts that own the main loop; the gtk host runs GApplication instead. */
void loop_main_run(void);
void loop_main_quit(void);

/* The calling thread's loop. */
struct loop_worker *loop_worker_new(void);
void loop_worker_free(struct loop_worker *worker);
/* Blocks until woken or timeout_ms passes (-1: no timeout). */
void loop_worker_poll(struct loop_worker *worker, int timeout_ms);
void loop_worker_wake(struct loop_worker *worker);
bool loop_worker_is_idling(struct loop_worker *worker);

#endif
