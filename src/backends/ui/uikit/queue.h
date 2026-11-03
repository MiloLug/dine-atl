#ifndef UIKIT_QUEUE_H
#define UIKIT_QUEUE_H

#import <Foundation/Foundation.h>

struct atl_app;

/* Any thread (apps build views off the main one), in an autorelease pool: op runs on UIKit's thread
 * after every op of app queued before it, the views' and the dialogs' alike (view.m). app NULL (a
 * view made on a thread of no app): op goes to UIKit's thread at once. */
void uikit_enqueue(struct atl_app *app, dispatch_block_t op);

#endif
