#import <UIKit/UIKit.h>

#include <stdio.h>
#include <stdlib.h>

#include "src/shared/upcall.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "../records/records.h"

/* Alerts as UIAlertControllers over the topmost presented view controller; items make an action
 * sheet. The ui calls come on the Android main thread and hop to UIKit's, taps hop back through
 * loop_main_post. Views have no UIKit counterpart yet: a Dialog with a content view shows nothing. */

struct ui_dialog_state {
	struct atl_ref *ref;
	void *controller; // the UIAlertController while shown, retained; UIKit's thread only
};

struct ui_window *ui_dialog_new(struct atl_ref *dialog_ref, struct ui_view *decor)
{
	struct ui_window *window = calloc(1, sizeof(*window));
	window->dialog = calloc(1, sizeof(*window->dialog));
	window->dialog->ref = dialog_ref;
	return window;
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

static UIAlertAction *action(struct ui_dialog_state *state, NSString *title, UIAlertActionStyle style, int which)
{
	return [UIAlertAction actionWithTitle:title style:style handler:^(UIAlertAction *tapped) {
		// UIKit dismissed the alert before calling this
		if (state->controller) {
			CFBridgingRelease(state->controller);
			state->controller = NULL;
		}
		log_line("closed");
		struct answer *answer = malloc(sizeof(*answer));
		*answer = (struct answer){state->ref, which};
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

static void present(struct ui_dialog_state *state, UIAlertController *controller)
{
	if (state->controller != (__bridge void *)controller) // closed before it could show
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
			present(state, controller);
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

void ui_dialog_show(struct ui_window *dialog, const struct ui_alert *alert)
{
	if (!alert) {
		log_line("show content view (not drawn yet)");
		return;
	}
	struct ui_dialog_state *state = dialog->dialog;
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
			[controller addAction:action(state, items[i], UIAlertActionStyleDefault, (int)i)];
		for (int i = 2; i >= 0; i--) { // Android's order: neutral, negative, positive
			if (buttons[i] != NSNull.null)
				[controller addAction:action(state, buttons[i], UIAlertActionStyleDefault, -1 - i)];
		}
		if (add_cancel)
			[controller addAction:action(state, @"Cancel", UIAlertActionStyleCancel, UI_DIALOG_CANCEL)];

		if (state->controller)
			CFBridgingRelease(state->controller);
		state->controller = (void *)CFBridgingRetain(controller);
		present(state, controller);
	});
}

void ui_dialog_close(struct ui_window *dialog)
{
	struct ui_dialog_state *state = dialog->dialog;
	dispatch_async(dispatch_get_main_queue(), ^{
		if (!state->controller) // a tap closed it already
			return;
		UIAlertController *controller = CFBridgingRelease(state->controller);
		state->controller = NULL;
		// dialog 2 may be up already: dismiss only this one, and only while it is presented
		if (controller.presentingViewController && !controller.isBeingDismissed) {
			[controller dismissViewControllerAnimated:YES completion:^{
				log_line("closed");
			}];
		}
	});
}

void ui_dialog_set_cancel_on_touch_outside(struct ui_window *dialog, bool cancel) {}
