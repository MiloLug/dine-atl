#import <UIKit/UIKit.h>

#include <stdio.h>
#include <stdlib.h>

#include "src/shared/menu.h"
#include "src/slots/ui.h"

#include "../records/records.h"
#import "queue.h"
#import "surface.h"

/* A PopupMenu as iOS's own menu: a clear button over the anchor whose primary action is the menu,
 * pressed from code (performPrimaryAction). A picked item's action runs at the tap, before the end
 * animation's completion, which answers the dismissal. */

@interface ATLMenuButton : UIButton
@property(nonatomic) struct atl_menu *atlMenu;
@property(nonatomic) BOOL atlPresented;
@property(nonatomic) BOOL atlAnswered; // its one answer went
@end

@implementation ATLMenuButton
- (void)atlAnswer:(int)index
{
	if (self.atlAnswered)
		return;
	self.atlAnswered = YES;
	atl_menu_answer(self.atlMenu, index);
}

- (void)atlEnd
{
	[self atlAnswer:UI_MENU_DISMISSED];
	[self removeFromSuperview];
}

- (void)contextMenuInteraction:(UIContextMenuInteraction *)interaction
    willDisplayMenuForConfiguration:(UIContextMenuConfiguration *)configuration
                           animator:(id<UIContextMenuInteractionAnimating>)animator
{
	if ([UIButton instancesRespondToSelector:_cmd])
		[super contextMenuInteraction:interaction willDisplayMenuForConfiguration:configuration animator:animator];
	self.atlPresented = YES;
#ifdef ATL_TEST_HOOKS
	printf("menu: presented\n");
	fflush(stdout);
#endif
}

- (void)contextMenuInteraction:(UIContextMenuInteraction *)interaction
       willEndForConfiguration:(UIContextMenuConfiguration *)configuration
                      animator:(id<UIContextMenuInteractionAnimating>)animator
{
	if ([UIButton instancesRespondToSelector:_cmd])
		[super contextMenuInteraction:interaction willEndForConfiguration:configuration animator:animator];
	void (^ended)(void) = ^{
#ifdef ATL_TEST_HOOKS
		printf("menu: ended\n");
		fflush(stdout);
#endif
		[self atlEnd];
	};
	if (animator)
		[animator addCompletion:ended];
	else
		ended();
}
@end

struct uikit_menu {
	struct atl_app *app;
	CFTypeRef button; // ATLMenuButton, UIKit's thread
};

static UIView *surface_of(UIView *view)
{
	for (; view; view = view.superview) {
		if ([view conformsToProtocol:@protocol(ATLSurface)])
			return view;
	}
	return nil;
}

// items depth first: each element goes in front of its siblings' list, walking back from the last
static UIMenu *build(ATLMenuButton *button, NSArray<NSString *> *titles, const struct ui_menu_item *items, int n)
{
	NSMutableArray<NSMutableArray<UIMenuElement *> *> *children = [NSMutableArray arrayWithCapacity:n + 1];
	for (int i = 0; i <= n; i++)
		[children addObject:[NSMutableArray array]];
	for (int i = n; i-- > 0;) {
		const struct ui_menu_item *item = &items[i];
		UIMenuElement *element;
		if (item->has_submenu) {
			element = [UIMenu menuWithTitle:titles[i] children:children[i]];
		} else {
			__weak ATLMenuButton *weak = button;
			int index = i;
			UIAction *action = [UIAction actionWithTitle:titles[i] image:nil identifier:nil handler:^(UIAction *a) {
#ifdef ATL_TEST_HOOKS
				printf("menu: picked %d\n", index);
				fflush(stdout);
#endif
				[weak atlAnswer:index];
			}];
			if (!item->enabled)
				action.attributes = UIMenuElementAttributesDisabled;
			if (item->checkable)
				action.state = item->checked ? UIMenuElementStateOn : UIMenuElementStateOff;
			element = action;
		}
		[children[item->parent >= 0 ? item->parent : n] insertObject:element atIndex:0];
	}
	return [UIMenu menuWithTitle:@"" children:children[n]];
}

void *ui_menu_show(struct ui_window *window, struct ui_view *anchor, const struct ui_menu_item *items, int n, struct atl_menu *menu)
{
	struct uikit_menu *shown = calloc(1, sizeof(*shown));
	shown->app = window->app;
	NSMutableArray<NSString *> *titles = [NSMutableArray arrayWithCapacity:n];
	struct ui_menu_item *copy = malloc((n ? n : 1) * sizeof(*copy));
	for (int i = 0; i < n; i++) {
		[titles addObject:@(items[i].title)];
		copy[i] = items[i];
		copy[i].title = NULL;
	}
	@autoreleasepool {
		uikit_enqueue(window->app, ^{
			ATLMenuButton *button = [ATLMenuButton buttonWithType:UIButtonTypeCustom];
			button.atlMenu = menu;
			shown->button = CFBridgingRetain(button);
			UIView *at = (__bridge UIView *)anchor->native;
			UIView *surface = surface_of(at);
			if (!surface || !at.window) {
				free(copy);
				[button atlEnd];
				return;
			}
			button.frame = [at convertRect:at.bounds toView:surface];
			button.alpha = 0.02; // shown: a hidden control presents nothing
			button.menu = build(button, titles, copy, n);
			free(copy);
			button.showsMenuAsPrimaryAction = YES;
			[surface addSubview:button];
			[button performPrimaryAction];
		});
	}
	return shown;
}

void ui_menu_close(void *data)
{
	struct uikit_menu *shown = data;
	uikit_enqueue(shown->app, ^{
		ATLMenuButton *button = (__bridge ATLMenuButton *)shown->button;
		[button.contextMenuInteraction dismissMenu]; // presented: willEnd answers
		if (!button.atlPresented)
			[button atlEnd];
	});
}

void ui_menu_free(void *data)
{
	struct uikit_menu *shown = data;
	uikit_enqueue(shown->app, ^{
		if (shown->button)
			CFRelease(shown->button);
		free(shown);
	});
}
