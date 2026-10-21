#ifndef UIKIT_INPUT_H
#define UIKIT_INPUT_H

#import <UIKit/UIKit.h>

/* UIKit's thread: the views' touches and the activity root's presses, posted to the Android main
 * thread for the shown activity (records_touch, records_key). */

void input_touches(NSSet<UITouch *> *touches, UIEvent *event, UITouchPhase phase);
void input_presses(NSSet<UIPress *> *presses, bool down);

// keys.m: a hardware keyboard's key as Android's
int keys_android_code(UIKeyboardHIDUsage usage);
int keys_android_meta(UIKeyModifierFlags flags);

#endif
