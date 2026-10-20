#ifndef ATL_SURFACE_H
#define ATL_SURFACE_H

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/* What the surface a uikit host gives ATL (atl_embedded_config.surface) may adopt. ATL calls it on
 * UIKit's thread, in the same batch as the window's view changes. */
NS_SWIFT_UI_ACTOR
@protocol ATLSurface <NSObject>
/* The frame the shown activity asks of the host around the window (the status bar). color: nil
 * when the app leaves it to the host; light: dark text and icons on it. */
- (void)atlFrameChanged:(nullable UIColor *)color light:(BOOL)light;
@end

NS_ASSUME_NONNULL_END

#endif
