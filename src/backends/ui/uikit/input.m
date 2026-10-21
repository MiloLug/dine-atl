#import <UIKit/UIKit.h>

#include <pthread.h>
#include <stdlib.h>

#include "src/shared/input.h"
#include "src/shared/upcall.h"
#include "src/slots/loop.h"

#include "../records/records.h"
#import "input.h"
#import "surface.h"

/* UIKit gives a touch to the view it hits, and each node view hands its touches here. Scroll views
 * and Dine's edge pans cancel the touches they take, which Java gets as CANCEL. A MOVE still
 * waiting for the Android thread takes the newer one's pointers instead of queueing behind it. */

// UIKit's thread
static UITouch *touches[ATL_POINTERS_MAX]; // per pointer index
static struct atl_touch gesture;
static UIView *gesture_surface; // the gesture's coordinates
static int64_t key_down_times[512];

struct posted_touch {
	struct atl_touch touch;
};

static pthread_mutex_t post_lock = PTHREAD_MUTEX_INITIALIZER;
static struct posted_touch *pending_move; // posted and not run yet

static void deliver_touch(void *data)
{
	struct posted_touch *posted = data;
	pthread_mutex_lock(&post_lock);
	if (pending_move == posted)
		pending_move = NULL;
	struct atl_touch touch = posted->touch;
	pthread_mutex_unlock(&post_lock);
	free(posted);
	records_touch(&touch);
}

static void post(int action)
{
	gesture.action = action;
	gesture.time = atl_uptime_ms();
	pthread_mutex_lock(&post_lock);
	if (action == ATL_ACTION_MOVE && pending_move) {
		pending_move->touch = gesture;
		pthread_mutex_unlock(&post_lock);
		return;
	}
	struct posted_touch *posted = malloc(sizeof(*posted));
	posted->touch = gesture;
	pending_move = action == ATL_ACTION_MOVE ? posted : NULL;
	pthread_mutex_unlock(&post_lock);
	if (!loop_main_post(deliver_touch, posted)) {
		pthread_mutex_lock(&post_lock);
		if (pending_move == posted)
			pending_move = NULL;
		pthread_mutex_unlock(&post_lock);
		free(posted);
	}
}

// the host's surface holding the touched view: the app's window coordinates
static UIView *surface_of(UIView *view)
{
	for (; view; view = view.superview) {
		if ([view conformsToProtocol:@protocol(ATLSurface)])
			return view;
	}
	return nil;
}

static void locate(int index)
{
	CGFloat scale = atl_window_get()->density_dpi / 160.;
	CGPoint point = [touches[index] locationInView:gesture_surface];
	gesture.pointers[index].x = point.x * scale;
	gesture.pointers[index].y = point.y * scale;
}

static int pointer_index(UITouch *touch)
{
	for (int i = 0; i < gesture.count; i++) {
		if (touches[i] == touch)
			return i;
	}
	return -1;
}

// Android's: the lowest id no pointer has
static int free_pointer_id(void)
{
	for (int id = 0;; id++) {
		bool taken = false;
		for (int i = 0; i < gesture.count; i++)
			taken |= gesture.pointers[i].id == id;
		if (!taken)
			return id;
	}
}

static void remove_pointer(int index)
{
	for (int i = index; i < gesture.count - 1; i++) {
		touches[i] = touches[i + 1];
		gesture.pointers[i] = gesture.pointers[i + 1];
	}
	touches[--gesture.count] = nil;
}

static void end_gesture(void)
{
	while (gesture.count)
		touches[--gesture.count] = nil;
	gesture_surface = nil;
}

void input_touches(NSSet<UITouch *> *changed, UIEvent *event, UITouchPhase phase)
{
	switch (phase) {
		case UITouchPhaseBegan:
			for (UITouch *touch in changed) {
				if (pointer_index(touch) >= 0 || gesture.count == ATL_POINTERS_MAX)
					continue;
				if (!gesture.count) {
					gesture_surface = surface_of(touch.view);
					if (!gesture_surface)
						return;
					gesture.down_time = atl_uptime_ms();
				}
				int index = gesture.count++;
				touches[index] = touch;
				gesture.pointers[index].id = free_pointer_id();
				locate(index);
				post(index ? ATL_ACTION_POINTER_DOWN | index << ATL_ACTION_INDEX_SHIFT : ATL_ACTION_DOWN);
			}
			break;
		case UITouchPhaseMoved: {
			bool moved = false;
			for (UITouch *touch in changed) {
				int index = pointer_index(touch);
				if (index >= 0) {
					locate(index);
					moved = true;
				}
			}
			if (moved)
				post(ATL_ACTION_MOVE);
			break;
		}
		case UITouchPhaseEnded:
			for (UITouch *touch in changed) {
				int index = pointer_index(touch);
				if (index < 0)
					continue;
				locate(index);
				post(gesture.count == 1 ? ATL_ACTION_UP : ATL_ACTION_POINTER_UP | index << ATL_ACTION_INDEX_SHIFT);
				remove_pointer(index);
			}
			if (!gesture.count)
				end_gesture();
			break;
		case UITouchPhaseCancelled:
			for (UITouch *touch in changed) {
				if (pointer_index(touch) >= 0) { // Android cancels the whole gesture
					post(ATL_ACTION_CANCEL);
					end_gesture();
					break;
				}
			}
			break;
		default:
			break;
	}
}

static void deliver_key(void *data)
{
	records_key(data);
	free(data);
}

void input_presses(NSSet<UIPress *> *presses, bool down)
{
	for (UIPress *press in presses) {
		UIKey *key = press.key;
		if (!key)
			continue;
		struct atl_key *posted = calloc(1, sizeof(*posted));
		posted->action = down ? ATL_KEY_DOWN : ATL_KEY_UP;
		posted->code = keys_android_code(key.keyCode);
		posted->meta = keys_android_meta(key.modifierFlags);
		posted->unicode = key.characters.length ? [key.characters characterAtIndex:0] : 0;
		posted->time = atl_uptime_ms();
		int64_t *down_time = &key_down_times[posted->code % 512];
		if (down)
			*down_time = posted->time;
		posted->down_time = *down_time;
		if (!loop_main_post(deliver_key, posted))
			free(posted);
	}
}
