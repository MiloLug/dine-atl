/* The pointer tracker (src/shared/pointers.c) the gtk and uikit input backends share: Android's
 * pointer ids and action codes, a pointer table that stays right when the app ends the gesture
 * during a delivery. */

#include <stdio.h>

#include "src/shared/pointers.h"

#define POINTER_DOWN_AT(index) (ATL_ACTION_POINTER_DOWN | (index) << ATL_ACTION_INDEX_SHIFT)
#define POINTER_UP_AT(index)   (ATL_ACTION_POINTER_UP | (index) << ATL_ACTION_INDEX_SHIFT)

static int failures;

static void check(bool ok, const char *what)
{
	if (!ok) {
		failures++;
		printf("FAIL: %s\n", what);
	}
}

static int a, b, c; // handles: addresses only

static void first_finger_has_id_0(void)
{
	struct atl_pointers p = {0};
	struct atl_touch event;
	check(atl_pointers_down(&p, &a, 10, 20, 100, &event), "the first down is taken");
	check(event.action == ATL_ACTION_DOWN && event.count == 1 && event.down_time == 100 && event.time == 100, "the first down: DOWN, one pointer, the gesture's time");
	check(event.pointers[0].id == 0, "the first finger gets id 0");
	check(event.pointers[0].x == 10 && event.pointers[0].y == 20, "the first finger's position");
	check(atl_pointers_up(&p, &a, 11, 21, 110, &event), "the up is taken");
	check(event.action == ATL_ACTION_UP && event.count == 1 && event.pointers[0].x == 11, "the last up: UP with the pointer at its position");
	check(p.touch.count == 0, "no pointer is down after the last up");
	atl_pointers_down(&p, &a, 0, 0, 200, &event);
	check(event.pointers[0].id == 0 && event.down_time == 200, "the next gesture starts at id 0 and its own time");
}

static void second_finger_has_id_1(void)
{
	struct atl_pointers p = {0};
	struct atl_touch event;
	atl_pointers_down(&p, &a, 1, 1, 10, &event);
	check(atl_pointers_down(&p, &b, 2, 2, 20, &event), "the second down is taken");
	check(event.action == POINTER_DOWN_AT(1) && event.count == 2, "the second down: POINTER_DOWN at index 1, both pointers");
	check(event.pointers[0].id == 0 && event.pointers[1].id == 1, "two fingers: ids 0 and 1");
	check(event.down_time == 10, "a later pointer keeps the gesture's down time");
}

static void lowest_free_id(void)
{
	struct atl_pointers p = {0};
	struct atl_touch event;
	atl_pointers_down(&p, &a, 1, 1, 10, &event);
	atl_pointers_down(&p, &b, 2, 2, 20, &event);
	atl_pointers_up(&p, &a, 1, 1, 30, &event);
	check(event.action == POINTER_UP_AT(0) && event.count == 2, "the first of two lifts: POINTER_UP at index 0, both pointers in the event");
	check(p.touch.count == 1 && p.touch.pointers[0].id == 1 && atl_pointers_find(&p, &b) == 0, "the other pointer moves down a slot and keeps id 1");
	atl_pointers_down(&p, &c, 3, 3, 40, &event);
	check(event.action == POINTER_DOWN_AT(1), "a pointer after a lift: POINTER_DOWN at index 1");
	check(event.pointers[1].id == 0 && event.pointers[0].id == 1, "the lowest id no pointer down has: 0 again");
}

static void null_handle_is_a_key(void)
{
	struct atl_pointers p = {0};
	struct atl_touch event;
	check(atl_pointers_find(&p, NULL) == -1, "no pointer down: a NULL handle is not found");
	check(atl_pointers_down(&p, NULL, 5, 5, 10, &event) && event.action == ATL_ACTION_DOWN, "a NULL handle goes down");
	check(atl_pointers_find(&p, NULL) == 0 && atl_pointers_find(&p, &a) == -1, "the NULL handle is found, another is not");
	check(!atl_pointers_down(&p, NULL, 6, 6, 20, &event) && p.touch.count == 1, "a pointer that is down does not go down again");
	check(atl_pointers_move(&p, NULL, 7, 8, 30, &event) && event.action == ATL_ACTION_MOVE && event.pointers[0].x == 7 && event.pointers[0].y == 8, "the NULL handle moves");
	check(atl_pointers_up(&p, NULL, 7, 8, 40, &event) && p.touch.count == 0, "the NULL handle lifts");
}

static void unknown_pointer_changes_nothing(void)
{
	struct atl_pointers p = {0};
	struct atl_touch event;
	atl_pointers_down(&p, &a, 1, 1, 10, &event);
	check(!atl_pointers_move(&p, &b, 2, 2, 20, &event), "an unknown pointer does not move");
	check(!atl_pointers_up(&p, &b, 2, 2, 30, &event), "an unknown pointer does not lift");
	check(p.touch.count == 1 && p.touch.pointers[0].x == 1, "the table is as it was");
}

static void table_full(void)
{
	struct atl_pointers p = {0};
	struct atl_touch event;
	int handles[ATL_POINTERS_MAX + 1];
	for (int i = 0; i < ATL_POINTERS_MAX; i++)
		check(atl_pointers_down(&p, &handles[i], i, i, i, &event) && event.pointers[i].id == i, "each of the table's pointers goes down with the next id");
	check(!atl_pointers_down(&p, &handles[ATL_POINTERS_MAX], 0, 0, 99, &event), "one more than the table holds is refused");
	check(p.touch.count == ATL_POINTERS_MAX, "the table still holds its pointers");
}

static void cancel_ends_the_gesture(void)
{
	struct atl_pointers p = {0};
	struct atl_touch event;
	check(!atl_pointers_cancel(&p, 5, &event), "no gesture: nothing to cancel");
	atl_pointers_down(&p, &a, 1, 1, 10, &event);
	atl_pointers_down(&p, &b, 2, 2, 20, &event);
	check(atl_pointers_cancel(&p, 30, &event), "a cancel is taken");
	check(event.action == ATL_ACTION_CANCEL && event.count == 2 && event.pointers[1].id == 1, "the cancel carries every pointer");
	check(p.touch.count == 0, "no pointer is down after the cancel");
	check(!atl_pointers_up(&p, &a, 1, 1, 40, &event) && !atl_pointers_move(&p, &b, 1, 1, 40, &event), "the pointers of a canceled gesture are gone");
	check(atl_pointers_down(&p, &a, 1, 1, 50, &event) && event.pointers[0].id == 0 && event.down_time == 50, "the next gesture starts fresh");
}

// the app ends the gesture while it gets an UP: the caller's table is already right
static void gesture_ended_during_a_delivery(void)
{
	struct atl_pointers p = {0};
	struct atl_touch event, cancel;
	atl_pointers_down(&p, &a, 1, 1, 10, &event);
	atl_pointers_down(&p, &b, 2, 2, 20, &event);
	atl_pointers_up(&p, &a, 1, 1, 30, &event); // delivery starts here
	atl_pointers_cancel(&p, 31, &cancel);      // the app's window closed meanwhile
	check(p.touch.count == 0, "nothing is down after the cancel");
	check(!atl_pointers_up(&p, &b, 2, 2, 32, &event), "the caller's later up finds no pointer, so it removes nothing");
	check(p.touch.count == 0, "the count stays 0");
}

static void lift_keeps_the_pointers_still_down(void)
{
	struct atl_touch touch = {.count = 3};
	for (int i = 0; i < 3; i++)
		touch.pointers[i] = (struct atl_pointer){.id = i, .x = i * 10, .y = i * 10};
	atl_touch_lift(&touch, 1);
	check(touch.count == 2 && touch.pointers[0].id == 0 && touch.pointers[1].id == 2 && touch.pointers[1].x == 20, "a lifted pointer leaves the event and the later ones move down");
	atl_touch_lift(&touch, 1);
	atl_touch_lift(&touch, 0);
	check(touch.count == 0, "the last lift empties the event");
}

int main(void)
{
	first_finger_has_id_0();
	second_finger_has_id_1();
	lowest_free_id();
	null_handle_is_a_key();
	unknown_pointer_changes_nothing();
	table_full();
	cancel_ends_the_gesture();
	gesture_ended_during_a_delivery();
	lift_keeps_the_pointers_still_down();
	if (failures)
		printf("%d failures\n", failures);
	else
		printf("PASS\n");
	return failures != 0;
}
