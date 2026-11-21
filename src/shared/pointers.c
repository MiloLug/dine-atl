#include <stddef.h>

#include "pointers.h"

void atl_touch_lift(struct atl_touch *touch, int index)
{
	for (int i = index; i < touch->count - 1; i++)
		touch->pointers[i] = touch->pointers[i + 1];
	touch->count--;
}

int atl_pointers_find(const struct atl_pointers *pointers, const void *handle)
{
	for (int i = 0; i < pointers->touch.count; i++) {
		if (pointers->handles[i] == handle)
			return i;
	}
	return -1;
}

// Android's: the lowest id no pointer has
static int free_pointer_id(const struct atl_pointers *pointers)
{
	for (int id = 0;; id++) {
		bool taken = false;
		for (int i = 0; i < pointers->touch.count; i++)
			taken |= pointers->touch.pointers[i].id == id;
		if (!taken)
			return id;
	}
}

static void deliver(struct atl_pointers *pointers, int action, int64_t now, struct atl_touch *event)
{
	pointers->touch.action = action;
	pointers->touch.time = now;
	*event = pointers->touch;
}

bool atl_pointers_down(struct atl_pointers *pointers, const void *handle, float x, float y, int64_t now, struct atl_touch *event)
{
	if (atl_pointers_find(pointers, handle) >= 0 || pointers->touch.count == ATL_POINTERS_MAX)
		return false;
	if (!pointers->touch.count)
		pointers->touch.down_time = now;
	int id = free_pointer_id(pointers);
	int index = pointers->touch.count++;
	pointers->handles[index] = handle;
	pointers->touch.pointers[index] = (struct atl_pointer){.id = id, .x = x, .y = y};
	deliver(pointers, index ? ATL_ACTION_POINTER_DOWN | index << ATL_ACTION_INDEX_SHIFT : ATL_ACTION_DOWN, now, event);
	return true;
}

bool atl_pointers_move(struct atl_pointers *pointers, const void *handle, float x, float y, int64_t now, struct atl_touch *event)
{
	int index = atl_pointers_find(pointers, handle);
	if (index < 0)
		return false;
	pointers->touch.pointers[index].x = x;
	pointers->touch.pointers[index].y = y;
	deliver(pointers, ATL_ACTION_MOVE, now, event);
	return true;
}

bool atl_pointers_up(struct atl_pointers *pointers, const void *handle, float x, float y, int64_t now, struct atl_touch *event)
{
	int index = atl_pointers_find(pointers, handle);
	if (index < 0)
		return false;
	pointers->touch.pointers[index].x = x;
	pointers->touch.pointers[index].y = y;
	deliver(pointers, pointers->touch.count == 1 ? ATL_ACTION_UP : ATL_ACTION_POINTER_UP | index << ATL_ACTION_INDEX_SHIFT, now, event);
	for (int i = index; i < pointers->touch.count - 1; i++)
		pointers->handles[i] = pointers->handles[i + 1];
	atl_touch_lift(&pointers->touch, index);
	return true;
}

bool atl_pointers_cancel(struct atl_pointers *pointers, int64_t now, struct atl_touch *event)
{
	if (!pointers->touch.count)
		return false;
	deliver(pointers, ATL_ACTION_CANCEL, now, event);
	pointers->touch.count = 0;
	return true;
}
