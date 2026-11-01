#ifndef UIKIT_QUEUE_H
#define UIKIT_QUEUE_H

#import <Foundation/Foundation.h>

/* Any thread (apps build views off the main one), in an autorelease pool: op runs on UIKit's thread
 * after every op queued before it, the views' and the dialogs' alike (view.m). */
void uikit_enqueue(dispatch_block_t op);

#endif
