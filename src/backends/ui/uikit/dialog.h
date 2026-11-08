#ifndef UIKIT_DIALOG_H
#define UIKIT_DIALOG_H

#import <UIKit/UIKit.h>

/* dialog.m's alerts for view.m, which shows the windows over the activities. */

@class ATLDialog;
struct ui_window;

// Android main thread: the alert of a ui_dialog_new window, for a block to hold
ATLDialog *uikit_dialog_of(struct ui_window *dialog);
// UIKit's thread: a panel alert's view, nil for a plain alert
UIView *uikit_dialog_panel(ATLDialog *dialog);
/* UIKit's thread: a newer window over the activities covers a plain alert, which UIKit would draw
 * over it: it waits, dismissed, until the windows above it go. */
void uikit_dialog_set_covered(ATLDialog *dialog, bool covered);

#endif
