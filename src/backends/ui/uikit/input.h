#ifndef UIKIT_INPUT_H
#define UIKIT_INPUT_H

#import <UIKit/UIKit.h>

/* UIKit's thread: the views' touches and the activity root's presses, posted to the Android main
 * thread for the shown activity (records_touch, records_key). */

void input_touches(NSSet<UITouch *> *touches, UIEvent *event, UITouchPhase phase);
void input_presses(UIView *view, NSSet<UIPress *> *presses, bool down);
/* A tap at point in view's coordinates, posted as a touch's DOWN and UP (accessibility's activate);
 * none while a finger is down. */
void input_tap(UIView *view, CGPoint point);

struct atl_app;
struct ui_window;
// view.m, any thread: the app whose window has this surface (the host's view), or NULL
struct atl_app *uikit_app_of(void *surface);
// view.m, on app's main thread: its window whose surface it is, or NULL once closed
struct ui_window *uikit_window_of(struct atl_app *app, void *surface);

// keys.m: a hardware keyboard's key as Android's
int keys_android_code(UIKeyboardHIDUsage usage);
int keys_android_meta(UIKeyModifierFlags flags);

#endif
