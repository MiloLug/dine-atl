#ifndef UIKIT_TOAST_H
#define UIKIT_TOAST_H

#import <UIKit/UIKit.h>

/* UIKit's thread: an app surface closed; its toast, which lives in the toast window, goes too. */
void uikit_toast_surface_closed(UIView *surface);

#endif
