#ifndef UIKIT_PANEL_H
#define UIKIT_PANEL_H

#import <UIKit/UIKit.h>

#include "src/slots/ui.h"

/* A panel alert as iOS draws its own alerts: glass, the title and message, check rows, a progress
 * view and capsule buttons above and below a gap that the window's root fills. UIKit's thread. */
@interface ATLPanelView : UIView
// which: a button (-1 - DialogInterface.BUTTON_*) or a row; rows move their checks first
@property(nonatomic, copy) void (^action)(int which);
- (void)atlSetAlert:(const struct ui_alert *)alert;
- (void)atlSetProgress:(int)value max:(int)max indeterminate:(BOOL)indeterminate;
- (void)atlSetButton:(int)index label:(NSString *)label enabled:(BOOL)enabled;
// pt above and below the root at the panel's width; rows past cap above scroll
- (CGFloat)atlHeaderHeightForWidth:(CGFloat)width cap:(CGFloat)cap;
- (CGFloat)atlFooterHeightForWidth:(CGFloat)width;
// the header's cap when it lays out
@property(nonatomic) CGFloat atlHeaderCap;
// the root has Android views (ui_alert.content)
@property(nonatomic, readonly) BOOL atlContent;
@end

// the panel's width within a surface surface_width wide, pt
CGFloat panel_width(CGFloat surface_width);
// pt: the space a panel keeps from the surface's edges, and the root's from the panel's sides
extern const CGFloat panel_margin;
extern const CGFloat panel_side;
/* The header's most within max_height and above a footer this tall, pt: the rows past it scroll,
 * and Android content keeps half. */
CGFloat panel_header_cap(CGFloat max_height, CGFloat footer, bool content);
// the system alert's dim behind a panel, for the light or dark appearance
UIColor *panel_dim_color(void);

#endif
