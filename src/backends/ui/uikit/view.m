#import <UIKit/UIKit.h>

#include <stdlib.h>

#include "src/shared/upcall.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "../records/records.h"
#import "surface.h"

/* A UIView per view record, an ATLLabel per text record, in the surface the host gave; the shown
 * activity's frame goes to the surface as ATLSurface. Changes queue on the Android main thread and
 * reach UIKit's thread in order, one batch per layout pass. Text measurement waits for UIKit's thread:
 * deadlock-free only while main never waits for the Android thread. */

@interface ATLLabel : UILabel
@property(nonatomic) UIEdgeInsets padding;
@end

@implementation ATLLabel
// at the top left of the padded bounds, TextView's default gravity; UILabel centers vertically
- (void)drawTextInRect:(CGRect)rect
{
	CGRect inner = UIEdgeInsetsInsetRect(rect, self.padding);
	CGRect text = [self textRectForBounds:inner limitedToNumberOfLines:self.numberOfLines];
	text.origin = inner.origin;
	text.size.width = inner.size.width;
	[super drawTextInRect:text];
}
@end

static struct ui_window *app_window;
static void *surface; // a retained UIView; blocks capture it when queued, the window's close clears it

static NSMutableArray<dispatch_block_t> *pending;
static bool flush_posted;

// the surface's points: hosts pass density 160 × screen scale
static CGFloat scale(void)
{
	return atl_window_get()->density_dpi / 160.;
}

// UIKit's thread only
static UIView *native(struct ui_view *view)
{
	return (__bridge UIView *)view->native;
}

static UIColor *color(uint32_t argb)
{
	return [UIColor colorWithRed:((argb >> 16) & 0xFF) / 255. green:((argb >> 8) & 0xFF) / 255. blue:(argb & 0xFF) / 255. alpha:(argb >> 24) / 255.];
}

static UIFont *font(float size, int style)
{
	UIFont *font = [UIFont systemFontOfSize:size / scale()];
	UIFontDescriptorSymbolicTraits traits = 0;
	if (style & UI_TEXT_BOLD)
		traits |= UIFontDescriptorTraitBold;
	if (style & UI_TEXT_ITALIC)
		traits |= UIFontDescriptorTraitItalic;
	UIFontDescriptor *descriptor = traits ? [font.fontDescriptor fontDescriptorWithSymbolicTraits:traits] : nil;
	return descriptor ? [UIFont fontWithDescriptor:descriptor size:0] : font;
}

static void flush(void)
{
	if (!pending.count)
		return;
	NSArray<dispatch_block_t> *ops = pending;
	pending = nil;
	dispatch_async(dispatch_get_main_queue(), ^{
		for (dispatch_block_t op in ops)
			op();
	});
}

static void flush_post(void *data)
{
	@autoreleasepool {
		flush_posted = false;
		if (app_window && app_window->layout_pending) // the pass flushes, with its frames
			return;
		flush();
	}
}

static void enqueue(dispatch_block_t op)
{
	if (!pending)
		pending = [NSMutableArray array];
	[pending addObject:op];
	if (!flush_posted)
		flush_posted = loop_main_post(flush_post, NULL);
}

void records_window_created(struct ui_window *window, void *surface_view)
{
	app_window = window;
	if (surface_view)
		surface = (void *)CFRetain(surface_view);
}

void records_view_created(struct ui_view *view)
{
	@autoreleasepool {
		bool text = view->kind == NODE_TEXT;
		enqueue(^{
			UIView *created;
			if (text) {
				ATLLabel *label = [ATLLabel new];
				label.numberOfLines = 0;
				created = label;
			} else {
				created = [UIView new];
				created.clipsToBounds = YES; // ViewGroup's clipChildren
			}
			view->native = (void *)CFBridgingRetain(created);
		});
	}
}

void records_child_added(struct ui_view *parent, struct ui_view *child, size_t index)
{
	@autoreleasepool {
		enqueue(^{
			[native(parent) insertSubview:native(child) atIndex:index];
		});
	}
}

void records_child_removed(struct ui_view *parent, struct ui_view *child)
{
	@autoreleasepool {
		enqueue(^{
			UIView *removed = native(child);
			if (removed.superview == native(parent)) // UIKit moves a view added to a second parent
				[removed removeFromSuperview];
		});
	}
}

// after every block queued before it, so no block reads the record once it is freed
void records_view_freed(struct ui_view *view)
{
	@autoreleasepool {
		enqueue(^{
			UIView *freed = CFBridgingRelease(view->native);
			[freed removeFromSuperview];
			free(view);
		});
	}
}

void records_root_added(struct ui_window *window, struct ui_view *root)
{
	if (window != app_window || !surface)
		return;
	void *target = surface;
	@autoreleasepool {
		enqueue(^{
			native(root).hidden = YES;
			[(__bridge UIView *)target addSubview:native(root)];
		});
	}
}

void records_root_shown(struct ui_window *window, struct ui_view *root)
{
	if (window != app_window || !surface)
		return;
	void *target = surface;
	@autoreleasepool {
		enqueue(^{
			UIView *shown = native(root);
			UIView *surface_view = (__bridge UIView *)target;
			for (UIView *other in surface_view.subviews)
				other.hidden = other != shown;
			[surface_view bringSubviewToFront:shown];
		});
	}
}

void records_root_removed(struct ui_window *window, struct ui_view *root)
{
	if (window != app_window)
		return;
	@autoreleasepool {
		enqueue(^{
			[native(root) removeFromSuperview];
		});
	}
}

void records_frame_changed(struct ui_window *window)
{
	if (window != app_window || !surface)
		return;
	struct ui_frame frame = window->frame;
	void *target = surface;
	@autoreleasepool {
		enqueue(^{
			id surface_view = (__bridge id)target;
			if ([surface_view conformsToProtocol:@protocol(ATLSurface)])
				[(id<ATLSurface>)surface_view atlFrameChanged:frame.has_color ? color(frame.color) : nil light:frame.light];
		});
	}
}

// the loop quits after this, so no flush_post would run
void records_window_closed(struct ui_window *window)
{
	if (window != app_window || !surface)
		return;
	void *closed = surface;
	surface = NULL;
	@autoreleasepool {
		enqueue(^{
			CFRelease(closed);
		});
		flush();
	}
}

void records_padding_changed(struct ui_view *view)
{
	if (view->kind != NODE_TEXT) // Java lays out a ViewGroup's padding
		return;
	CGFloat s = scale();
	UIEdgeInsets padding = UIEdgeInsetsMake(view->padding_top / s, view->padding_left / s, view->padding_bottom / s, view->padding_right / s);
	@autoreleasepool {
		enqueue(^{
			ATLLabel *label = (ATLLabel *)native(view);
			label.padding = padding;
			[label setNeedsDisplay];
		});
	}
}

void records_background_changed(struct ui_view *view, uint32_t argb)
{
	@autoreleasepool {
		enqueue(^{
			native(view).backgroundColor = color(argb);
		});
	}
}

void records_text_changed(struct ui_view *view)
{
	@autoreleasepool {
		NSString *text = [NSString stringWithUTF8String:view->text] ?: @"";
		float size = view->text_size;
		int style = view->text_style;
		enqueue(^{
			ATLLabel *label = (ATLLabel *)native(view);
			label.text = text;
			label.font = font(size, style);
		});
	}
}

void records_text_color_changed(struct ui_view *view, uint32_t argb)
{
	@autoreleasepool {
		enqueue(^{
			((ATLLabel *)native(view)).textColor = color(argb);
		});
	}
}

void records_text_extent(struct ui_view *view, int max_width, int *width, int *height)
{
	@autoreleasepool {
		NSString *text = [NSString stringWithUTF8String:view->text] ?: @"";
		float size = view->text_size;
		int style = view->text_style;
		CGFloat s = scale();
		__block CGSize fit;
		// a label of its own: the view's label may still wait for queued changes
		dispatch_sync(dispatch_get_main_queue(), ^{
			static ATLLabel *scratch;
			if (!scratch) {
				scratch = [ATLLabel new];
				scratch.numberOfLines = 0;
			}
			scratch.font = font(size, style);
			scratch.text = text;
			fit = [scratch sizeThatFits:CGSizeMake(max_width < 0 ? CGFLOAT_MAX : max_width / s, CGFLOAT_MAX)];
			if (!text.length) // a TextView keeps one line's height
				fit.height = scratch.font.lineHeight;
		});
		*width = ceil(fit.width * s);
		*height = ceil(fit.height * s);
		if (max_width >= 0 && *width > max_width)
			*width = max_width;
	}
}

struct frame {
	struct ui_view *view;
	CGRect rect; // pt
};

static size_t count_views(struct ui_view *view)
{
	size_t count = 1;
	for (size_t i = 0; i < view->n_children; i++)
		count += count_views(view->children[i]);
	return count;
}

static void collect_frames(struct ui_view *view, CGFloat s, struct frame **next)
{
	*(*next)++ = (struct frame){view, CGRectMake(view->left / s, view->top / s, (view->right - view->left) / s, (view->bottom - view->top) / s)};
	for (size_t i = 0; i < view->n_children; i++)
		collect_frames(view->children[i], s, next);
}

void records_layout_done(struct ui_window *window)
{
	@autoreleasepool {
		if (window == app_window && window->shown) {
			size_t count = count_views(window->shown);
			struct frame *frames = malloc(count * sizeof(*frames)), *next = frames;
			collect_frames(window->shown, scale(), &next);
			enqueue(^{
				for (size_t i = 0; i < count; i++)
					native(frames[i].view).frame = frames[i].rect;
				free(frames);
			});
		}
		flush();
	}
}
