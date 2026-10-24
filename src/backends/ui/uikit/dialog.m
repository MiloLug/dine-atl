#import <UIKit/UIKit.h>

#include <stdio.h>
#include <stdlib.h>

#include "src/shared/upcall.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "../records/records.h"
#import "queue.h"

/* Alerts as UIAlertControllers over the topmost presented view controller; items make an action
 * sheet. The ui calls queue on the Android main thread with the views' changes, taps hop back through
 * loop_main_post. A Dialog with a content view shows nothing yet. */

/* A dialog's UIKit side; UIKit's thread only once shown. The blocks hold it, so a tap on an alert
 * still dismissing after ui_dialog_free reads a NULL ref, not freed memory. The alert is built from
 * the model when it presents: changes queued after a show in the same batch (onShow's) are in it. */
@interface ATLDialog : NSObject {
@public
	struct atl_ref *ref;
	NSString *title, *message;
	NSArray<NSString *> *items;
	NSString *buttons[3]; // nil: none or hidden
	bool disabled[3];
	bool cancelable;
	bool wanted;                   // shown and not closed: an alert UIKit dismissed on a tap comes back
	bool presenting;               // a present is on its way: posted or waiting for a transition
	bool stale;                    // the model changed in a way the presented alert can't show
	UIAlertController *controller; // built and presenting, presented or dismissing
	UIAlertAction *actions[3];     // the controller's buttons
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

static void request_present(ATLDialog *dialog, bool animated);

struct answer {
	void *dialog; // an ATLDialog, retained
	struct atl_ref *ref;
	int which;
};

// UIKit dismissed the alert on the tap: while the dialog stays drawn it comes back
static void give_answer(void *data)
{
	@autoreleasepool {
		struct answer *answer = data;
		ATLDialog *dialog = CFBridgingRelease(answer->dialog);
		if (atl_upcall_dialog_result(answer->ref, answer->which)) {
			uikit_enqueue(^{
				if (dialog->wanted && !dialog->controller)
					request_present(dialog, false);
			});
		}
		free(answer);
	}
}

static UIAlertAction *action(ATLDialog *dialog, UIAlertController *controller, NSString *title, UIAlertActionStyle style, int which)
{
	__weak UIAlertController *weak_controller = controller;
	return [UIAlertAction actionWithTitle:title style:style handler:^(UIAlertAction *tapped) {
		// UIKit dismissed the alert before calling this; a newer one may be up already
		if (dialog->controller == weak_controller)
			dialog->controller = nil;
		log_line("closed");
		if (!dialog->ref)
			return;
		struct answer *answer = malloc(sizeof(*answer));
		*answer = (struct answer){(void *)CFBridgingRetain(dialog), dialog->ref, which};
		if (!loop_main_post(give_answer, answer)) {
			CFRelease(answer->dialog);
			free(answer);
		}
	}];
}

static UIAlertController *build(ATLDialog *dialog)
{
	UIAlertControllerStyle style = dialog->items.count ? UIAlertControllerStyleActionSheet : UIAlertControllerStyleAlert;
	UIAlertController *controller = [UIAlertController alertControllerWithTitle:dialog->title message:dialog->message preferredStyle:style];
	for (NSUInteger i = 0; i < dialog->items.count; i++)
		[controller addAction:action(dialog, controller, dialog->items[i], UIAlertActionStyleDefault, (int)i)];
	for (int i = 2; i >= 0; i--) { // Android's order: neutral, negative, positive
		dialog->actions[i] = nil;
		if (!dialog->buttons[i])
			continue;
		UIAlertAction *button = dialog->actions[i] = action(dialog, controller, dialog->buttons[i], UIAlertActionStyleDefault, -1 - i);
		button.enabled = !dialog->disabled[i];
		[controller addAction:button];
	}
	// a list or an alert without buttons is otherwise left only by Android's back
	bool add_cancel = dialog->cancelable && (dialog->items.count || (!dialog->buttons[0] && !dialog->buttons[1] && !dialog->buttons[2]));
	if (add_cancel)
		[controller addAction:action(dialog, controller, @"Cancel", UIAlertActionStyleCancel, UI_DIALOG_CANCEL)];
	return controller;
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

static void rebuild(ATLDialog *dialog);

static void present(ATLDialog *dialog, bool animated)
{
	if (!dialog->wanted || dialog->controller) { // closed before it could show, or shown again meanwhile
		dialog->presenting = false;
		return;
	}
	UIViewController *top = top_controller();
	if (!top) {
		dialog->presenting = false;
		log_line("no view controller to present on");
		return;
	}
	// presenting while another presentation or dismissal animates fails
	id<UIViewControllerTransitionCoordinator> coordinator = top.transitionCoordinator;
	if (coordinator) {
		[coordinator animateAlongsideTransition:nil completion:^(id<UIViewControllerTransitionCoordinatorContext> context) {
			present(dialog, animated);
		}];
		return;
	}
	dialog->presenting = false;
	dialog->stale = false;
	UIAlertController *controller = dialog->controller = build(dialog);
	controller.popoverPresentationController.sourceView = top.view;
	controller.popoverPresentationController.sourceRect = CGRectMake(CGRectGetMidX(top.view.bounds), CGRectGetMidY(top.view.bounds), 0, 0);
	[top presentViewController:controller animated:animated completion:^{
		log_line("presented");
		if (dialog->stale && dialog->controller == controller)
			rebuild(dialog);
	}];
}

// after the queued batch, so changes queued with the show are in the alert
static void request_present(ATLDialog *dialog, bool animated)
{
	if (dialog->presenting)
		return;
	dialog->presenting = true;
	dispatch_async(dispatch_get_main_queue(), ^{
		present(dialog, animated);
	});
}

// a presented alert can't change its actions' titles or drop one: a new one replaces it
static void rebuild(ATLDialog *dialog)
{
	UIAlertController *old = dialog->controller;
	dialog->controller = nil;
	[old dismissViewControllerAnimated:NO completion:^{
		request_present(dialog, false);
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
		struct {
			bool disabled[3];
		} flags; // a block can't capture an array itself
		for (int i = 0; i < 3; i++) {
			[buttons addObject:string(alert->buttons[i]) ?: NSNull.null];
			flags.disabled[i] = alert->disabled[i];
		}
		bool cancelable = alert->cancelable;
		uikit_enqueue(^{
			dialog->title = title;
			dialog->message = message;
			dialog->items = items;
			for (int i = 0; i < 3; i++) {
				dialog->buttons[i] = buttons[i] != NSNull.null ? buttons[i] : nil;
				dialog->disabled[i] = flags.disabled[i];
			}
			dialog->cancelable = cancelable;
			dialog->wanted = true;
			UIAlertController *controller = dialog->controller;
			if (!controller)
				request_present(dialog, true);
			else if (controller.isBeingPresented) // rebuilt once presented
				dialog->stale = true;
			else if (!controller.isBeingDismissed) // still up: the new content replaces it
				rebuild(dialog);
		});
	}
}

void ui_dialog_set_button(struct ui_window *window, int which, const char *label_chars, bool enabled)
{
	@autoreleasepool {
		ATLDialog *dialog = get_dialog(window);
		NSString *label = string(label_chars);
		int i = -1 - which;
		uikit_enqueue(^{
			bool relabeled = (label == nil) != (dialog->buttons[i] == nil) || (label && ![label isEqualToString:dialog->buttons[i]]);
			dialog->buttons[i] = label;
			dialog->disabled[i] = !enabled;
			UIAlertController *controller = dialog->controller;
			if (!controller || controller.isBeingDismissed) // the model is what a present builds from
				return;
			dialog->actions[i].enabled = enabled;
			if (!relabeled)
				return;
			if (controller.isBeingPresented || !controller.presentingViewController)
				dialog->stale = true; // rebuilt once presented
			else
				rebuild(dialog);
		});
	}
}

void ui_dialog_close(struct ui_window *window)
{
	@autoreleasepool {
		ATLDialog *dialog = get_dialog(window);
		uikit_enqueue(^{
			dialog->wanted = false;
			UIAlertController *controller = dialog->controller;
			if (!controller) // a tap closed it already, or it never showed
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
		uikit_enqueue(^{
			struct atl_ref *ref = dialog->ref;
			dialog->ref = NULL;
			dialog->wanted = false;
			dialog->controller = nil;
			// answers posted from this thread before stay ahead of it and still use the ref
			loop_main_post(free_ref, ref);
		});
	}
}

void ui_dialog_set_cancel_on_touch_outside(struct ui_window *dialog, bool cancel) {}
