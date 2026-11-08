#import <UIKit/UIKit.h>
#import <objc/runtime.h>

#include <stdio.h>

#include "src/slots/ui.h"
#include "src/shared/upcall.h"

#include "../records/records.h"
#import "queue.h"
#import "toast.h"

/* A Toast's text as a glass capsule in a window of its own above the alerts, as Android shows
 * toasts over dialogs: it takes no touches and never becomes key, so the alerts still present from
 * the key window. One capsule per app surface. */

@interface ATLToastWindow : UIWindow
@end

@implementation ATLToastWindow
- (BOOL)canBecomeKeyWindow
{
	return NO;
}
@end

static const void *capsule_key = &capsule_key;

static ATLToastWindow *toast_window(UIWindowScene *scene)
{
	static NSMapTable<UIWindowScene *, ATLToastWindow *> *windows;
	if (!windows)
		windows = [NSMapTable weakToStrongObjectsMapTable];
	ATLToastWindow *window = [windows objectForKey:scene];
	if (!window) {
		window = [[ATLToastWindow alloc] initWithWindowScene:scene];
		window.windowLevel = UIWindowLevelAlert + 1;
		window.userInteractionEnabled = NO;
		window.backgroundColor = UIColor.clearColor;
		window.hidden = NO;
		[windows setObject:window forKey:scene];
	}
	return window;
}

static UIVisualEffectView *capsule_new(void)
{
	UIVisualEffect *effect;
	if (@available(iOS 26, *))
		effect = [UIGlassEffect effectWithStyle:UIGlassEffectStyleRegular];
	else
		effect = [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemMaterial];
	UIVisualEffectView *capsule = [[UIVisualEffectView alloc] initWithEffect:effect];
	capsule.clipsToBounds = YES;
	UILabel *label = [[UILabel alloc] init];
	label.font = [UIFont preferredFontForTextStyle:UIFontTextStyleSubheadline];
	label.adjustsFontForContentSizeCategory = YES;
	label.numberOfLines = 0;
	label.textAlignment = NSTextAlignmentCenter;
	label.tag = 1;
	[capsule.contentView addSubview:label];
	capsule.isAccessibilityElement = NO;
	return capsule;
}

// Android's Gravity on one axis: before, after, both (fill) or neither (center), offset by adjust
static CGFloat place(int axis, CGFloat size, CGFloat container, CGFloat adjust)
{
	switch (axis & 0x6) {
	case 0x2:
		return adjust;
	case 0x4:
		return container - adjust - size;
	default:
		return (container - size) / 2 + adjust;
	}
}

void ui_toast_show(struct ui_window *window, const char *text, int gravity, int x, int y)
{
	NSString *string = @(text);
	void *target = window->surface;
	CGFloat scale = atl_screen_get()->density_dpi / 160.;
	@autoreleasepool {
		uikit_enqueue(window->app, ^{
			UIView *surface = (__bridge UIView *)target;
			UIWindowScene *scene = surface.window.windowScene;
			if (!scene)
				return;
			ATLToastWindow *toasts = toast_window(scene);
			UIVisualEffectView *capsule = objc_getAssociatedObject(surface, capsule_key);
			if (!capsule) {
				capsule = capsule_new();
				objc_setAssociatedObject(surface, capsule_key, capsule, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
			}
			UILabel *label = [capsule.contentView viewWithTag:1];
			label.text = string;
			capsule.accessibilityLabel = string;
			// the surface's frame in the toast window, within its safe area
			CGRect area = [surface convertRect:UIEdgeInsetsInsetRect(surface.bounds, surface.safeAreaInsets) toView:toasts];
			CGFloat margin = 16;
			CGSize text_size = [label sizeThatFits:CGSizeMake(area.size.width - 4 * margin, CGFLOAT_MAX)];
			CGSize size = CGSizeMake(text_size.width + 2 * margin, text_size.height + margin);
			capsule.layer.cornerRadius = MIN(size.height / 2, 22);
			label.frame = CGRectMake(margin, margin / 2, text_size.width, text_size.height);
			capsule.frame = CGRectMake(CGRectGetMinX(area) + place(gravity, size.width, area.size.width, x / scale),
			                           CGRectGetMinY(area) + place(gravity >> 4, size.height, area.size.height, y / scale), size.width, size.height);
			if (capsule.superview != toasts) {
				capsule.alpha = 0;
				[toasts addSubview:capsule];
			}
			[UIView animateWithDuration:0.2 animations:^{
				capsule.alpha = 1;
			} completion:^(BOOL finished) {
#ifdef ATL_TEST_HOOKS
				if (finished) {
					printf("toast: shown \"%s\"\n", string.UTF8String);
					fflush(stdout);
				}
#endif
			}];
			UIAccessibilityPostNotification(UIAccessibilityAnnouncementNotification, string);
		});
	}
}

void uikit_toast_surface_closed(UIView *surface)
{
	UIVisualEffectView *capsule = objc_getAssociatedObject(surface, capsule_key);
#ifdef ATL_TEST_HOOKS
	if (capsule.superview) {
		printf("toast: gone with its window\n");
		fflush(stdout);
	}
#endif
	[capsule removeFromSuperview];
	objc_setAssociatedObject(surface, capsule_key, nil, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

void ui_toast_hide(struct ui_window *window)
{
	void *target = window->surface;
	uikit_enqueue(window->app, ^{
		UIView *surface = (__bridge UIView *)target;
		UIVisualEffectView *capsule = objc_getAssociatedObject(surface, capsule_key);
		if (!capsule || !capsule.superview)
			return;
		[UIView animateWithDuration:0.2 animations:^{
			capsule.alpha = 0;
		} completion:^(BOOL finished) {
			if (capsule.alpha == 0)
				[capsule removeFromSuperview];
		}];
	});
}
