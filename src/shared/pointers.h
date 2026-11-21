#ifndef SHARED_POINTERS_H
#define SHARED_POINTERS_H

/* The pointers of a touch gesture, for the backends whose toolkit gives touches one at a time (gtk,
 * uikit): ids and action codes as Android's input reader gives them. No JNI: tests/loop/pointers_test.c
 * links it alone. The backend owns the state, one per gesture source. */

#include "input.h"

struct atl_pointers {
	struct atl_touch touch; // every pointer that is down; touch.count 0: no gesture
	// the backend's key per pointer, compared only; NULL is a key too (gtk's mouse)
	const void *handles[ATL_POINTERS_MAX];
};

/* The pointer at index leaves the event: the others move down a slot. */
void atl_touch_lift(struct atl_touch *touch, int index);

/* The pointer's index, or -1 when it is not down. */
int atl_pointers_find(const struct atl_pointers *pointers, const void *handle);

/* Each call below returns the touch to deliver in *event, a copy: the table is current when the
 * delivery starts, so what the app does meanwhile (a window closing ends the gesture) leaves
 * nothing for the caller to redo. False: nothing to deliver. */

/* A pointer goes down with the lowest id no pointer down has; the first one starts the gesture at
 * now. False when it is down already or the table is full. */
bool atl_pointers_down(struct atl_pointers *pointers, const void *handle, float x, float y, int64_t now, struct atl_touch *event);
/* False when the pointer is not down. */
bool atl_pointers_move(struct atl_pointers *pointers, const void *handle, float x, float y, int64_t now, struct atl_touch *event);
/* The event has the pointer at its position and the action UP, or POINTER_UP with its index; the
 * table has it gone. False when it is not down. */
bool atl_pointers_up(struct atl_pointers *pointers, const void *handle, float x, float y, int64_t now, struct atl_touch *event);
/* Android cancels the whole gesture: the event has every pointer, the table none. False when no
 * pointer is down. */
bool atl_pointers_cancel(struct atl_pointers *pointers, int64_t now, struct atl_touch *event);

#endif
