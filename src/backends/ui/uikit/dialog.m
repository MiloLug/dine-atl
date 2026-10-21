#import <UIKit/UIKit.h>

#include <stdio.h>
#include <stdlib.h>

#include "src/shared/upcall.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "../records/records.h"

/* Alerts as UIAlertControllers over the topmost presented view controller; items make an action
 * sheet. The ui calls come on the Android main thread and hop to UIKit's, taps hop back through
 * loop_main_post. A Dialog with a content view shows nothing yet. */

/* A dialog's UIKit side; UIKit's thread only once shown. The blocks hold it, so a tap on an alert
 * still dismissing after ui_dialog_free reads a NULL ref, not freed memory. */
@interface ATLDialog : NSObject {
@public
	struct atl_ref *ref;
	UIAlertController *controller; // while shown
}
@end

@implementation ATLDialog
@end

struct ui_dialog_state {
	void *dialog; // an ATLDialog, retained
};

static ATLDialog *get_dialog(struct ui_window *window)
{
	return (__bridge ATLDialog *)window->dialog->dialog;
}

struct ui_window *ui_dialog_new(struct atl_ref *dialog_ref, struct ui_view *decor)
{
	@autoreleasepool {
		ATLDialog *dialog = [ATLDialog new];
		dialog->ref = dialog_ref;
		struct ui_window *window = calloc(1, sizeof(*window));
		window->dialog = calloc(1, sizeof(*window->dialog));
		window->dialog->dialog = (void *)CFBridgingRetain(dialog);
		return window;
	}
}

static void log_line(const char *line)
{
	printf("dialog: %s\n", line);
	fflush(stdout);
}

struct answer {
	struct atl_ref *ref;
	int which;
};

static void give_answer(void *data)
{
	struct answer *answer = data;
	atl_upcall_dialog_result(answer->ref, answer->which);
	free(answer);
}

static UIAlertAction *action(ATLDialog *dialog, NSString *title, UIAlertActionStyle style, int which)
{
	return [UIAlertAction actionWithTitle:title style:style handler:^(UIAlertAction *tapped) {
		// UIKit dismissed the alert before calling this
		dialog->controller = nil;
		log_line("closed");
		if (!dialog->ref)
			return;
		struct answer *answer = malloc(sizeof(*answer));
		*answer = (struct answer){dialog->ref, which};
		if (!loop_main_post(give_answer, answer))
			free(answer);
	}];
}

static UIViewController *top_controller(void)
{
	UIWindow *key = nil;
	for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
		if (![scene isKindOfClass:UIWindowScene.class])
			continue;
		for (UIWindow *window in ((UIWindowScene *)scene).windows) {
			if (window.isKeyWindow)
				key = window;
		}
	}
	UIViewController *top = key.rootViewController;
	while (top.presentedViewController && !top.presentedViewController.isBeingDismissed)
		top = top.presentedViewController;
	return top;
}

static void present(ATLDialog *dialog, UIAlertController *controller)
{
	if (dialog->controller != controller) // closed before it could show
		return;
	UIViewController *top = top_controller();
	if (!top) {
		log_line("no view controller to present on");
		return;
	}
	// presenting while another presentation or dismissal animates fails
	id<UIViewControllerTransitionCoordinator> coordinator = top.transitionCoordinator;
	if (coordinator) {
		[coordinator animateAlongsideTransition:nil completion:^(id<UIViewControllerTransitionCoordinatorContext> context) {
			present(dialog, controller);
		}];
		return;
	}
	controller.popoverPresentationController.sourceView = top.view;
	controller.popoverPresentationController.sourceRect = CGRectMake(CGRectGetMidX(top.view.bounds), CGRectGetMidY(top.view.bounds), 0, 0);
	[top presentViewController:controller animated:YES completion:^{
		log_line("presented");
	}];
}

static NSString *string(const char *chars)
{
	return chars ? [NSString stringWithUTF8String:chars] : nil;
}

void ui_dialog_show(struct ui_window *window, const struct ui_alert *alert)
{
	if (!alert) {
		log_line("show content view (not drawn yet)");
		return;
	}
	@autoreleasepool {
		ATLDialog *dialog = get_dialog(window);
		NSString *title = string(alert->title);
		NSString *message = string(alert->message);
		NSMutableArray<NSString *> *items = [NSMutableArray array];
		for (int i = 0; i < alert->n_items; i++)
			[items addObject:string(alert->items[i]) ?: @""];
		NSMutableArray *buttons = [NSMutableArray array];
		for (int i = 0; i < 3; i++)
			[buttons addObject:string(alert->buttons[i]) ?: NSNull.null];
		// a list or an alert without buttons is otherwise left only by Android's back
		bool add_cancel = alert->cancelable && (items.count || (!alert->buttons[0] && !alert->buttons[1] && !alert->buttons[2]));

		dispatch_async(dispatch_get_main_queue(), ^{
			UIAlertControllerStyle style = items.count ? UIAlertControllerStyleActionSheet : UIAlertControllerStyleAlert;
			UIAlertController *controller = [UIAlertController alertControllerWithTitle:title message:message preferredStyle:style];
			for (NSUInteger i = 0; i < items.count; i++)
				[controller addAction:action(dialog, items[i], UIAlertActionStyleDefault, (int)i)];
			for (int i = 2; i >= 0; i--) { // Android's order: neutral, negative, positive
				if (buttons[i] != NSNull.null)
					[controller addAction:action(dialog, buttons[i], UIAlertActionStyleDefault, -1 - i)];
			}
			if (add_cancel)
				[controller addAction:action(dialog, @"Cancel", UIAlertActionStyleCancel, UI_DIALOG_CANCEL)];

			dialog->controller = controller;
			present(dialog, controller);
		});
	}
}

void ui_dialog_close(struct ui_window *window)
{
	@autoreleasepool {
		ATLDialog *dialog = get_dialog(window);
		dispatch_async(dispatch_get_main_queue(), ^{
			UIAlertController *controller = dialog->controller;
			if (!controller) // a tap closed it already
				return;
			dialog->controller = nil;
			// dialog 2 may be up already: dismiss only this one, and only while it is presented
			if (controller.presentingViewController && !controller.isBeingDismissed) {
				[controller dismissViewControllerAnimated:YES completion:^{
					log_line("closed");
				}];
			}
		});
	}
}

static void free_ref(void *ref)
{
	atl_ref_free(ref);
}

void ui_dialog_free(struct ui_window *window)
{
	@autoreleasepool {
		ATLDialog *dialog = CFBridgingRelease(window->dialog->dialog);
		records_observations_orphan(window);
		free(window->dialog);
		free(window);
		dispatch_async(dispatch_get_main_queue(), ^{
			struct atl_ref *ref = dialog->ref;
			dialog->ref = NULL;
			dialog->controller = nil;
			// answers posted from this thread before stay ahead of it and still use the ref
			loop_main_post(free_ref, ref);
		});
	}
}

void ui_dialog_set_cancel_on_touch_outside(struct ui_window *dialog, bool cancel) {}
