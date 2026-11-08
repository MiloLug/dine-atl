#ifndef ATL_SURFACE_H
#define ATL_SURFACE_H

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/* What the surface a uikit host gives ATL (atl_embedded_config.surface) may adopt. ATL calls it on
 * UIKit's thread, in the same batch as the window's view changes. */
NS_SWIFT_UI_ACTOR
@protocol ATLSurface <NSObject>
/* The frame the shown activity asks of the host around the window (the status bar). color: nil
 * when the app leaves it to the host; light: dark text and icons on it; fullscreen: the host hides
 * its bars, the window keeps its size. */
- (void)atlFrameChanged:(nullable UIColor *)color light:(BOOL)light fullscreen:(BOOL)fullscreen;
@optional
/* How dark the windows over the activities' dims make the screen, 0 to 1: the host dims its own
 * frame (the status bar) as much. */
- (void)atlDimChanged:(CGFloat)amount;
@end

NS_ASSUME_NONNULL_END

#endif
