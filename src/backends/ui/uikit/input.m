#import <UIKit/UIKit.h>
#import <objc/runtime.h>

#include <pthread.h>
#include <stdlib.h>

#include "src/shared/input.h"
#include "src/shared/pointers.h"
#include "src/shared/upcall.h"
#include "src/slots/loop.h"

#include "../records/records.h"
#import "input.h"
#import "surface.h"

/* UIKit gives a touch to the view it hits, and each node view hands its touches here. Scroll views
 * and Dine's edge pans cancel the touches they take, which Java gets as CANCEL. A MOVE still
 * waiting for the Android thread takes the newer one's pointers instead of queueing behind it.
 * Each app has its own state (ATLInput): a finger on one app's surface and one on another's are two
 * gestures. */

struct posted_touch {
	struct atl_app *app;
	void *surface; // compared only: its window may have closed
	struct atl_touch touch;
};

/* One app's input. UIKit's thread owns it, but the lock and pendingMove: the Android thread clears
 * pendingMove when its MOVE runs. */
@interface ATLInput : NSObject {
@public
	struct atl_pointers pointers;     // keyed by the UITouch's address
	int64_t keyDownTimes[512];        // by Android key code
	pthread_mutex_t lock;
	struct posted_touch *pendingMove; // posted and not run yet
}
// The touches in pointers: their addresses are the handles, so they live as long as the gesture
// keeps them, and an ended one's address can't come again as another touch.
@property(nonatomic, strong) NSMutableArray<UITouch *> *held;
@property(nonatomic, strong) UIView *surface; // the gesture's coordinates
@end

@implementation ATLInput
- (instancetype)init
{
	if ((self = [super init])) {
		pthread_mutex_init(&lock, NULL);
		_held = [NSMutableArray array];
	}
	return self;
}
@end

void *input_new(void)
{
	return (void *)CFBridgingRetain([[ATLInput alloc] init]);
}

static ATLInput *input_of_app(struct atl_app *app)
{
	return app ? (__bridge ATLInput *)uikit_input_of(app) : nil;
}

static void deliver_touch(void *data)
{
	struct posted_touch *posted = data;
	ATLInput *input = input_of_app(posted->app);
	pthread_mutex_lock(&input->lock);
	if (input->pendingMove == posted)
		input->pendingMove = NULL;
	struct atl_touch touch = posted->touch;
	struct ui_window *window = uikit_window_of(posted->app, posted->surface);
	pthread_mutex_unlock(&input->lock);
	free(posted);
	if (window)
		records_touch(window, &touch);
}

static void post(ATLInput *input, const struct atl_touch *event)
{
	void *surface = (__bridge void *)input.surface;
	pthread_mutex_lock(&input->lock);
	if (event->action == ATL_ACTION_MOVE && input->pendingMove && input->pendingMove->surface == surface) {
		input->pendingMove->touch = *event;
		pthread_mutex_unlock(&input->lock);
		return;
	}
	struct atl_app *app = uikit_app_of(surface);
	if (!app) {
		input->pendingMove = NULL;
		pthread_mutex_unlock(&input->lock);
		return;
	}
	struct posted_touch *posted = malloc(sizeof(*posted));
	posted->app = app;
	posted->surface = surface;
	posted->touch = *event;
	input->pendingMove = event->action == ATL_ACTION_MOVE ? posted : NULL;
	pthread_mutex_unlock(&input->lock);
	if (!loop_main_post(atl_app_loop(app), deliver_touch, posted)) {
		pthread_mutex_lock(&input->lock);
		if (input->pendingMove == posted)
			input->pendingMove = NULL;
		pthread_mutex_unlock(&input->lock);
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

static ATLInput *input_of(UIView *surface)
{
	return surface ? input_of_app(uikit_app_of((__bridge void *)surface)) : nil;
}

/* The app whose gesture a touch is in, from its Began to its end: its view may leave the window
 * meanwhile, and then no surface names it. Not retained: an app's ATLInput lives as long as the app. */
static const void *const owner_key = &owner_key;

static ATLInput *owner_of(UITouch *touch)
{
	return objc_getAssociatedObject(touch, owner_key);
}

// px in the surface
static CGPoint located(UITouch *touch, UIView *surface)
{
	CGFloat scale = atl_screen_get()->density_dpi / 160.;
	CGPoint point = [touch locationInView:surface];
	return CGPointMake(point.x * scale, point.y * scale);
}

static void end_gesture(ATLInput *input)
{
	for (UITouch *touch in input.held)
		objc_setAssociatedObject(touch, owner_key, nil, OBJC_ASSOCIATION_ASSIGN);
	[input.held removeAllObjects];
	input.surface = nil;
}

void input_touches(NSSet<UITouch *> *changed, UIEvent *event, UITouchPhase phase)
{
	switch (phase) {
		case UITouchPhaseBegan:
			for (UITouch *touch in changed) {
				UIView *surface = surface_of(touch.view);
				ATLInput *input = input_of(surface);
				if (!input)
					continue;
				if (!input->pointers.touch.count)
					input.surface = surface;
				CGPoint at = located(touch, input.surface);
				struct atl_touch down;
				if (!atl_pointers_down(&input->pointers, (__bridge void *)touch, at.x, at.y, atl_uptime_ms(), &down))
					continue;
				[input.held addObject:touch];
				objc_setAssociatedObject(touch, owner_key, input, OBJC_ASSOCIATION_ASSIGN);
				post(input, &down);
			}
			break;
		case UITouchPhaseMoved: {
			ATLInput *moved_input = nil;
			struct atl_touch moved;
			for (UITouch *touch in changed) {
				ATLInput *input = owner_of(touch);
				if (!input || atl_pointers_find(&input->pointers, (__bridge void *)touch) < 0)
					continue;
				if (moved_input && moved_input != input)
					post(moved_input, &moved);
				CGPoint at = located(touch, input.surface);
				atl_pointers_move(&input->pointers, (__bridge void *)touch, at.x, at.y, atl_uptime_ms(), &moved);
				moved_input = input;
			}
			if (moved_input)
				post(moved_input, &moved);
			break;
		}
		case UITouchPhaseEnded:
			for (UITouch *touch in changed) {
				ATLInput *input = owner_of(touch);
				if (!input || atl_pointers_find(&input->pointers, (__bridge void *)touch) < 0)
					continue;
				CGPoint at = located(touch, input.surface);
				struct atl_touch up;
				atl_pointers_up(&input->pointers, (__bridge void *)touch, at.x, at.y, atl_uptime_ms(), &up);
				post(input, &up);
				[input.held removeObject:touch];
				objc_setAssociatedObject(touch, owner_key, nil, OBJC_ASSOCIATION_ASSIGN);
				if (!input->pointers.touch.count)
					end_gesture(input);
			}
			break;
		case UITouchPhaseCancelled:
			for (UITouch *touch in changed) {
				ATLInput *input = owner_of(touch);
				struct atl_touch cancel;
				if (input && atl_pointers_find(&input->pointers, (__bridge void *)touch) >= 0 && atl_pointers_cancel(&input->pointers, atl_uptime_ms(), &cancel)) {
					post(input, &cancel); // Android cancels the whole gesture
					end_gesture(input);
				}
			}
			break;
		default:
			break;
	}
}

void input_tap(UIView *view, CGPoint point)
{
	UIView *surface = surface_of(view);
	ATLInput *input = input_of(surface);
	if (!input || input->pointers.touch.count)
		return;
	CGFloat scale = atl_screen_get()->density_dpi / 160.;
	CGPoint at = [view convertPoint:point toView:surface];
	input.surface = surface;
	struct atl_touch event;
	// the app's own state's address stands for the pointer: no touch has it
	const void *handle = (__bridge void *)input;
	atl_pointers_down(&input->pointers, handle, at.x * scale, at.y * scale, atl_uptime_ms(), &event);
	post(input, &event);
	atl_pointers_up(&input->pointers, handle, at.x * scale, at.y * scale, atl_uptime_ms(), &event);
	post(input, &event);
	end_gesture(input);
}

static void deliver_native_down(void *data)
{
	struct posted_touch *posted = data;
	struct ui_window *window = uikit_window_of(posted->app, posted->surface);
	if (window)
		records_touch_outside(window, &posted->touch);
	free(posted);
}

void input_native_down(UIView *surface, CGPoint point)
{
	struct atl_app *app = uikit_app_of((__bridge void *)surface);
	if (!app)
		return;
	CGFloat scale = atl_screen_get()->density_dpi / 160.;
	struct posted_touch *posted = malloc(sizeof(*posted));
	posted->app = app;
	posted->surface = (__bridge void *)surface;
	posted->touch = (struct atl_touch){.action = ATL_ACTION_DOWN, .down_time = atl_uptime_ms(), .count = 1};
	posted->touch.time = posted->touch.down_time;
	posted->touch.pointers[0] = (struct atl_pointer){.id = 0, .x = point.x * scale, .y = point.y * scale};
	if (!loop_main_post(atl_app_loop(app), deliver_native_down, posted))
		free(posted);
}

struct posted_key {
	struct atl_key key;
	struct atl_app *app;
	void *surface; // compared only
};

static void deliver_key(void *data)
{
	struct posted_key *posted = data;
	struct ui_window *window = uikit_window_of(posted->app, posted->surface);
	if (window)
		records_key(window, &posted->key);
	free(posted);
}

void input_presses(UIView *view, NSSet<UIPress *> *presses, enum input_press what)
{
	void *surface = (__bridge void *)surface_of(view);
	struct atl_app *app = uikit_app_of(surface);
	ATLInput *input = input_of_app(app);
	if (!input)
		return;
	for (UIPress *press in presses) {
		UIKey *key = press.key;
		if (!key)
			continue;
		struct posted_key *posted_key = calloc(1, sizeof(*posted_key));
		posted_key->app = app;
		posted_key->surface = surface;
		struct atl_key *posted = &posted_key->key;
		posted->action = what == INPUT_PRESS_DOWN ? ATL_KEY_DOWN : ATL_KEY_UP;
		posted->canceled = what == INPUT_PRESS_CANCEL;
		posted->code = keys_android_code(key.keyCode);
		posted->meta = keys_android_meta(key.modifierFlags);
		posted->unicode = key.characters.length ? [key.characters characterAtIndex:0] : 0;
		posted->time = atl_uptime_ms();
		int64_t *down_time = &input->keyDownTimes[posted->code % 512];
		if (what == INPUT_PRESS_DOWN)
			*down_time = posted->time;
		posted->down_time = *down_time;
		if (!loop_main_post(atl_app_loop(app), deliver_key, posted_key))
			free(posted_key);
	}
}
