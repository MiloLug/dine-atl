#import <QuartzCore/QuartzCore.h>
#import <UIKit/UIKit.h>

#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>

#include "src/shared/input.h"
#include "src/shared/upcall.h"
#include "src/slots/gfx.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "../../gfx/coregraphics/coregraphics.h"
#include "../records/records.h"
#import "input.h"
#import "queue.h"
#import "surface.h"

/* A UIView per view record, an ATLLabel per text record (an ATLButton per button's, a UITextField or
 * UITextView per edit record's), in the surface the host gave; the shown activity's frame goes to
 * the surface as ATLSurface. Changes queue on the Android main thread and reach UIKit's thread in
 * order, one batch per layout or draw pass. Text measurement waits for UIKit's thread:
 * deadlock-free only while main never waits for the Android thread. */

// each node view hands the touches UIKit gives it to input.m
#define FORWARD_TOUCHES                                                            \
	-(void)touchesBegan : (NSSet<UITouch *> *)touches withEvent : (UIEvent *)event \
	{                                                                              \
		input_touches(touches, event, UITouchPhaseBegan);                          \
	}                                                                              \
	-(void)touchesMoved : (NSSet<UITouch *> *)touches withEvent : (UIEvent *)event \
	{                                                                              \
		input_touches(touches, event, UITouchPhaseMoved);                          \
	}                                                                              \
	-(void)touchesEnded : (NSSet<UITouch *> *)touches withEvent : (UIEvent *)event \
	{                                                                              \
		input_touches(touches, event, UITouchPhaseEnded);                          \
	}                                                                              \
	-(void)touchesCancelled : (NSSet<UITouch *> *)touches withEvent : (UIEvent *)event \
	{                                                                              \
		input_touches(touches, event, UITouchPhaseCancelled);                      \
	}

// an activity's root is the first responder while shown: the hardware keyboard's presses
@interface ATLView : UIView
@property(nonatomic) BOOL activityRoot;
@end

static void root_take_responder(UIView *root);

// an edit view's report waiting for the Android thread; freed there, after the record
struct edit_report_box {
	struct ui_view *view;
	bool posted;
	char *prev, *text; // the waiting report's
	int prev_start, prev_end, start, end;
	uint32_t seq;
};

@interface ATLEditState : NSObject
@property(nonatomic) struct ui_view *record; // NULL once freed
@property(nonatomic) struct edit_report_box *box;
@property(nonatomic, copy) NSString *prev; // the last reported or applied state
@property(nonatomic) NSInteger prevStart, prevEnd;
@property(nonatomic) uint32_t seq;
@property(nonatomic) bool applying;    // a snapshot's changes don't report
@property(nonatomic) bool javaFocus;   // Java moves the first responder: no focus upcall
@property(nonatomic) bool wasMarked;   // the next committed state reports, changed or not
@property(nonatomic) bool multiline;
@property(nonatomic, copy) NSString *hint;
@property(nonatomic) UIEdgeInsets padding;
@end

@interface ATLEditField : UITextField
@property(nonatomic) ATLEditState *atlState;
@end
// UIKit's thread: the field the keyboard types into, as Java asked or the user tapped
static UIView *keyboard_field;
static void edit_box_free(struct edit_report_box *box);

@implementation ATLView
FORWARD_TOUCHES

- (BOOL)canBecomeFirstResponder
{
	return _activityRoot;
}

// shown before Dine put the surface in a window
- (void)didMoveToWindow
{
	[super didMoveToWindow];
	if (_activityRoot && !self.hidden && self.window)
		root_take_responder(self);
}

- (void)pressesBegan:(NSSet<UIPress *> *)presses withEvent:(UIPressesEvent *)event
{
	input_presses(presses, true);
}

- (void)pressesEnded:(NSSet<UIPress *> *)presses withEvent:(UIPressesEvent *)event
{
	input_presses(presses, false);
}

- (void)pressesCancelled:(NSSet<UIPress *> *)presses withEvent:(UIPressesEvent *)event
{
	input_presses(presses, false);
}
@end

// a text record's view, ATLLabel or ATLButton
@protocol ATLText
- (void)atlSetText:(NSString *)text font:(UIFont *)font alignment:(NSTextAlignment)alignment;
- (void)atlSetColor:(UIColor *)color;
- (void)atlSetPadding:(UIEdgeInsets)padding;
@end

// an edit record's view
@protocol ATLEditor <ATLText, UITextInput>
@property(nonatomic) ATLEditState *atlState; // moves to the view that replaces this one
@end

static CGFloat scale(void);

// a node's display list (background, onDraw) under its subviews, drawn asynchronously by CA
@interface ATLDrawLayer : CALayer
@property(nonatomic) struct gfx_canvas *list; // a ref of its own
@end

@implementation ATLDrawLayer
+ (id<CAAction>)defaultActionForKey:(NSString *)event
{
	return (id<CAAction>)[NSNull null];
}

- (void)setList:(struct gfx_canvas *)list
{
	if (list)
		cg_list_ref(list);
	if (_list)
		gfx_canvas_free(_list);
	_list = list;
}

- (void)dealloc
{
	if (_list)
		gfx_canvas_free(_list);
}

- (void)drawInContext:(CGContextRef)context
{
	if (_list)
		cg_list_draw(_list, context, self.contentsScale);
}
@end

// a label's text is its own layer's contents, which every sublayer covers: it draws its list itself
@interface ATLLabel : UILabel <ATLText>
@property(nonatomic) UIEdgeInsets padding;
@property(nonatomic) struct gfx_canvas *list; // a ref of its own
@end

@implementation ATLLabel
FORWARD_TOUCHES

- (void)setList:(struct gfx_canvas *)list
{
	if (list)
		cg_list_ref(list);
	if (_list)
		gfx_canvas_free(_list);
	_list = list;
	[self setNeedsDisplay];
}

- (void)dealloc
{
	if (_list)
		gfx_canvas_free(_list);
}

- (void)drawRect:(CGRect)rect
{
	if (_list)
		cg_list_draw(_list, UIGraphicsGetCurrentContext(), scale());
	[super drawRect:rect];
}

- (void)atlSetText:(NSString *)text font:(UIFont *)font alignment:(NSTextAlignment)alignment
{
	self.text = text;
	self.font = font;
	self.textAlignment = alignment;
}

- (void)atlSetColor:(UIColor *)color
{
	self.textColor = color;
}

- (void)atlSetPadding:(UIEdgeInsets)padding
{
	self.padding = padding;
	[self setNeedsDisplay];
}

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

@interface ATLProgress : UIView
@property(nonatomic, readonly) UIProgressView *bar;
@property(nonatomic, readonly) UIActivityIndicatorView *spinner;
@end

@implementation ATLProgress
FORWARD_TOUCHES

- (instancetype)init
{
	if ((self = [super initWithFrame:CGRectZero])) {
		_bar = [[UIProgressView alloc] initWithProgressViewStyle:UIProgressViewStyleDefault];
		_spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleMedium];
		_spinner.hidden = YES;
		[self addSubview:_bar];
		[self addSubview:_spinner];
	}
	return self;
}

- (void)layoutSubviews
{
	[super layoutSubviews];
	CGRect bounds = self.bounds;
	CGFloat height = [_bar sizeThatFits:bounds.size].height;
	_bar.frame = CGRectMake(0, (bounds.size.height - height) / 2, bounds.size.width, height);
	_spinner.center = CGPointMake(CGRectGetMidX(bounds), CGRectGetMidY(bounds));
}
@end

static void click_post(void *data)
{
	struct ui_view *view = data;
	if (view->ref) // its last free waits behind this post
		atl_upcall_click(view->ref);
}

static UIImage *box_image(enum ui_button kind, bool checked)
{
	NSString *name = kind == UI_BUTTON_CHECK ? (checked ? @"checkmark.square.fill" : @"square")
	                                         : (checked ? @"largecircle.fill.circle" : @"circle");
	UIImage *image = [UIImage systemImageNamed:name];
	if (!image) {
		printf("atl: no SF Symbol %s\n", name.UTF8String);
		fflush(stdout);
	}
	return image;
}

#define SWITCH_SPACING 8 // pt between a switch's text and its UISwitch

/* A button record's view: a UIButton, or a label and a UISwitch, showing Java's state only; the touches
 * go to input.m as any view's. Accessibility activation (VoiceOver, the debug agent's tap) is a click,
 * as TalkBack's is. */
@interface ATLButton : UIView <ATLText>
@property(nonatomic) bool enabled;
@property(nonatomic) bool hasBackground; // push and toggle: plain instead of gray
- (instancetype)initWithRecord:(struct ui_view *)view kind:(enum ui_button)kind;
- (void)atlSetPressed:(bool)pressed checked:(bool)checked;
- (void)detachRecord;
- (CGSize)contentSizeThatFits:(CGFloat)width; // pt, without padding
@end

@implementation ATLButton {
	struct ui_view *_record; // NULL for a scratch view, or once freed
	enum ui_button _kind;
	UIButton *_button; // all but a switch
	UILabel *_label;   // a switch's text
	UISwitch *_toggle;
	NSString *_text;
	UIFont *_font;
	UIColor *_color;
	UIEdgeInsets _padding;
	bool _pressed, _checked;
}
FORWARD_TOUCHES

- (instancetype)initWithRecord:(struct ui_view *)view kind:(enum ui_button)kind
{
	if ((self = [super initWithFrame:CGRectZero])) {
		_record = view;
		_kind = kind;
		_text = @"";
		_enabled = true;
		if (kind == UI_BUTTON_SWITCH) {
			_label = [UILabel new];
			_label.numberOfLines = 0;
			_toggle = [UISwitch new];
			_toggle.userInteractionEnabled = NO;
			[self addSubview:_label];
			[self addSubview:_toggle];
		} else {
			_button = [UIButton buttonWithConfiguration:[UIButtonConfiguration plainButtonConfiguration] primaryAction:nil];
			_button.userInteractionEnabled = NO;
			[self addSubview:_button];
		}
		self.isAccessibilityElement = YES;
		[self update];
	}
	return self;
}

- (void)update
{
	UIColor *color = _enabled ? _color : nil; // nil: the control's own, dimmed when disabled
	if (_toggle) {
		_label.text = _text;
		_label.font = _font;
		_label.textColor = color ?: UIColor.labelColor;
		[_toggle setOn:_checked animated:YES];
		_toggle.enabled = _enabled;
	} else {
		bool box = _kind == UI_BUTTON_CHECK || _kind == UI_BUTTON_RADIO;
		UIButtonConfiguration *config;
		if (box || _hasBackground)
			config = [UIButtonConfiguration plainButtonConfiguration];
		else if (_kind == UI_BUTTON_TOGGLE && _checked)
			config = [UIButtonConfiguration filledButtonConfiguration];
		else
			config = [UIButtonConfiguration grayButtonConfiguration];
		NSMutableDictionary *attributes = [NSMutableDictionary dictionary];
		if (_font)
			attributes[NSFontAttributeName] = _font;
		if (color)
			attributes[NSForegroundColorAttributeName] = color;
		config.attributedTitle = [[NSAttributedString alloc] initWithString:_text attributes:attributes];
		if (box) {
			config.image = box_image(_kind, _checked);
			config.imagePadding = 8;
			config.imagePlacement = NSDirectionalRectEdgeLeading;
			config.contentInsets = NSDirectionalEdgeInsetsZero;
			if (_font)
				config.preferredSymbolConfigurationForImage = [UIImageSymbolConfiguration configurationWithFont:_font];
		}
		_button.configuration = config;
		_button.contentHorizontalAlignment = box ? UIControlContentHorizontalAlignmentLeading : UIControlContentHorizontalAlignmentCenter;
		_button.highlighted = _pressed;
		_button.selected = _kind == UI_BUTTON_TOGGLE && _checked;
		_button.enabled = _enabled;
	}
	self.accessibilityLabel = _text;
	UIAccessibilityTraits traits = UIAccessibilityTraitButton;
	if (_checked && _kind != UI_BUTTON_PUSH && _kind != UI_BUTTON_SWITCH)
		traits |= UIAccessibilityTraitSelected;
	if (!_enabled)
		traits |= UIAccessibilityTraitNotEnabled;
	self.accessibilityTraits = traits;
	self.accessibilityValue = _kind == UI_BUTTON_SWITCH ? (_checked ? @"1" : @"0") : nil;
}

- (void)layoutSubviews
{
	[super layoutSubviews];
	CGRect inner = UIEdgeInsetsInsetRect(self.bounds, _padding);
	if (_toggle) {
		// its drawn size: intrinsicContentSize leaves out an alignment inset
		CGSize size = [_toggle sizeThatFits:CGSizeZero];
		_toggle.frame = CGRectMake(CGRectGetMaxX(inner) - size.width, CGRectGetMidY(inner) - size.height / 2, size.width, size.height);
		_label.frame = CGRectMake(inner.origin.x, inner.origin.y, MAX(0, inner.size.width - size.width - SWITCH_SPACING), inner.size.height);
	} else {
		_button.frame = inner;
	}
}

- (CGSize)contentSizeThatFits:(CGFloat)width
{
	if (_toggle) {
		CGSize toggle = [_toggle sizeThatFits:CGSizeZero];
		CGSize label = [_label sizeThatFits:CGSizeMake(MAX(0, width - toggle.width - SWITCH_SPACING), CGFLOAT_MAX)];
		if (!_text.length)
			label.height = _label.font.lineHeight;
		return CGSizeMake(label.width + SWITCH_SPACING + toggle.width, MAX(label.height, toggle.height));
	}
	return [_button sizeThatFits:CGSizeMake(width, CGFLOAT_MAX)];
}

- (void)atlSetText:(NSString *)text font:(UIFont *)font alignment:(NSTextAlignment)alignment
{
	_text = text;
	_font = font;
	[self update];
	[self setNeedsLayout];
}

- (void)atlSetColor:(UIColor *)color
{
	_color = color;
	[self update];
}

- (void)atlSetPadding:(UIEdgeInsets)padding
{
	_padding = padding;
	[self setNeedsLayout];
}

- (void)atlSetPressed:(bool)pressed checked:(bool)checked
{
	_pressed = pressed;
	_checked = checked;
	[self update];
}

- (void)setEnabled:(bool)enabled
{
	_enabled = enabled;
	[self update];
}

- (void)setHasBackground:(bool)hasBackground
{
	_hasBackground = hasBackground;
	[self update];
}

- (void)detachRecord
{
	_record = NULL;
}

- (BOOL)accessibilityActivate
{
	if (!_enabled || !_record)
		return NO;
	loop_main_post(click_post, _record);
	return YES;
}
@end

// the offset UIKit reached, for the Android thread
struct scroll_report {
	struct ui_view *view;
	int x, y; // px
	bool posted;
};

static pthread_mutex_t report_lock = PTHREAD_MUTEX_INITIALIZER;

// a record's ref is NULL from ui_view_free; its last free waits behind the reports in the queue
static void deliver_report(void *data)
{
	struct scroll_report *report = data;
	pthread_mutex_lock(&report_lock);
	int x = report->x, y = report->y;
	report->posted = false;
	pthread_mutex_unlock(&report_lock);
	struct ui_view *view = report->view;
	if (!view->ref)
		return;
	view->scroll_x = x;
	view->scroll_y = y;
	atl_upcall_scrolled(view->ref, x, y);
}

/* the toolkit's offset is the truth: each one UIKit reaches goes to Java, the newest while one waits.
 * The user's bounce past an end stays UIKit's (AOSP overflings 6 dp at most); Java's own requests go
 * back unclamped, since a scrollTo made in a layout pass reaches UIKit before its contentSize. */
@interface ATLScrollView : UIScrollView <UIScrollViewDelegate>
@property(nonatomic, readonly) struct scroll_report *report;
@end

@implementation ATLScrollView
FORWARD_TOUCHES

- (instancetype)initWithRecord:(struct ui_view *)view horizontal:(bool)horizontal
{
	if ((self = [super initWithFrame:CGRectZero])) {
		_report = calloc(1, sizeof(*_report));
		_report->view = view;
		self.delegate = self;
		self.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever; // the safe area is the host's
		self.multipleTouchEnabled = YES;
		self.showsHorizontalScrollIndicator = horizontal;
		self.showsVerticalScrollIndicator = !horizontal;
		self.alwaysBounceHorizontal = horizontal;
		self.alwaysBounceVertical = !horizontal;
	}
	return self;
}

- (void)scrollViewDidScroll:(UIScrollView *)scrollView
{
	CGFloat s = atl_window_get()->density_dpi / 160.;
	CGPoint offset = self.contentOffset;
	if (self.isDragging || self.isDecelerating) {
		CGSize size = self.bounds.size;
		CGSize content = self.contentSize;
		offset.x = MAX(0, MIN(offset.x, content.width - size.width));
		offset.y = MAX(0, MIN(offset.y, content.height - size.height));
	}
	pthread_mutex_lock(&report_lock);
	_report->x = lround(offset.x * s);
	_report->y = lround(offset.y * s);
	bool post = !_report->posted;
	_report->posted = true;
	pthread_mutex_unlock(&report_lock);
	if (post && !loop_main_post(deliver_report, _report)) {
		pthread_mutex_lock(&report_lock);
		_report->posted = false;
		pthread_mutex_unlock(&report_lock);
	}
}
@end

static struct ui_window *app_window;
static void *surface; // a retained UIView; blocks capture it when queued, the window's close clears it

static NSMutableArray<dispatch_block_t> *pending;
static bool flush_posted;
static atomic_int batches_in_flight; // flushed, not yet run on UIKit's thread

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
	atomic_fetch_add(&batches_in_flight, 1);
	dispatch_async(dispatch_get_main_queue(), ^{
		for (dispatch_block_t op in ops)
			op();
		atomic_fetch_sub(&batches_in_flight, 1);
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

void uikit_enqueue(dispatch_block_t op)
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
		enum node_kind kind = view->kind;
		bool horizontal = view->horizontal;
		bool is_button = view->is_button, edit = view->edit;
		enum ui_button button = view->button;
		uikit_enqueue(^{
			UIView *created;
			if (kind == NODE_TEXT && is_button) {
				created = [[ATLButton alloc] initWithRecord:view kind:button];
			} else if (kind == NODE_TEXT && edit) {
				ATLEditField *field = [ATLEditField new];
				field.atlState.record = view;
				field.atlState.box = calloc(1, sizeof(struct edit_report_box));
				field.atlState.box->view = view;
				created = field;
			} else if (kind == NODE_TEXT) {
				ATLLabel *label = [ATLLabel new];
				label.numberOfLines = 0;
				created = label;
			} else if (kind == NODE_PROGRESS) {
				created = [ATLProgress new];
			} else if (kind == NODE_SCROLL) {
				created = [[ATLScrollView alloc] initWithRecord:view horizontal:horizontal];
			} else {
				created = [ATLView new];
				created.clipsToBounds = YES; // ViewGroup's clipChildren
			}
			created.multipleTouchEnabled = YES;
			view->native = (void *)CFBridgingRetain(created);
		});
	}
}

void records_child_added(struct ui_view *parent, struct ui_view *child, size_t index)
{
	@autoreleasepool {
		uikit_enqueue(^{
			[native(parent) insertSubview:native(child) atIndex:index];
		});
	}
}

void records_child_removed(struct ui_view *parent, struct ui_view *child)
{
	@autoreleasepool {
		uikit_enqueue(^{
			UIView *removed = native(child);
			if (removed.superview == native(parent)) // UIKit moves a view added to a second parent
				[removed removeFromSuperview];
		});
	}
}

static void free_record(void *data)
{
	struct ui_view *view = data;
	if (view->edit)
		edit_box_free(view->native);
	else
		free(view->native); // a scroll node's report, else NULL
	free(view);
}

// after every block queued before it, so no block reads the record once it is freed; then back on
// the Android thread, after the scroll reports UIKit posted before
void records_view_freed(struct ui_view *view)
{
	@autoreleasepool {
		uikit_enqueue(^{
			UIView *freed = CFBridgingRelease(view->native);
			struct scroll_report *report = NULL;
			if ([freed isKindOfClass:[ATLScrollView class]]) {
				((ATLScrollView *)freed).delegate = nil;
				report = ((ATLScrollView *)freed).report;
			}
			if ([freed isKindOfClass:[ATLButton class]]) // VoiceOver may still hold it
				[(ATLButton *)freed detachRecord];
			if ([freed conformsToProtocol:@protocol(ATLEditor)]) {
				ATLEditState *state = ((id<ATLEditor>)freed).atlState;
				state.record = NULL;
				state.javaFocus = true;
				report = (struct scroll_report *)state.box;
				state.box = NULL;
				if (keyboard_field == freed)
					keyboard_field = nil;
			}
			[freed removeFromSuperview];
			view->native = report;
			if (!loop_main_post(free_record, view))
				free_record(view);
		});
	}
}

void records_root_added(struct ui_window *window, struct ui_view *root)
{
	if (window != app_window || !surface)
		return;
	void *target = surface;
	@autoreleasepool {
		uikit_enqueue(^{
			ATLView *added = (ATLView *)native(root);
			added.hidden = YES;
			added.activityRoot = YES;
			[(__bridge UIView *)target addSubview:added];
		});
	}
}

void records_root_shown(struct ui_window *window, struct ui_view *root)
{
	if (window != app_window || !surface)
		return;
	void *target = surface;
	@autoreleasepool {
		bool visible = root->visibility == UI_VIEW_VISIBLE;
		uikit_enqueue(^{
			UIView *shown = native(root);
			UIView *surface_view = (__bridge UIView *)target;
			for (UIView *other in surface_view.subviews)
				other.hidden = other != shown || !visible;
			[surface_view bringSubviewToFront:shown];
			root_take_responder(shown);
		});
	}
}

void records_root_removed(struct ui_window *window, struct ui_view *root)
{
	if (window != app_window)
		return;
	@autoreleasepool {
		uikit_enqueue(^{
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
		uikit_enqueue(^{
			id surface_view = (__bridge id)target;
			if ([surface_view conformsToProtocol:@protocol(ATLSurface)])
				[(id<ATLSurface>)surface_view atlFrameChanged:frame.has_color ? color(frame.color) : nil light:frame.light fullscreen:frame.fullscreen];
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
		uikit_enqueue(^{
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
		uikit_enqueue(^{
			[(id<ATLText>)native(view) atlSetPadding:padding];
		});
	}
}

void records_background_changed(struct ui_view *view, uint32_t argb)
{
	@autoreleasepool {
		bool has_background = view->has_background;
		uikit_enqueue(^{
			UIView *changed = native(view);
			changed.backgroundColor = color(argb);
			if ([changed isKindOfClass:[ATLButton class]])
				((ATLButton *)changed).hasBackground = has_background;
		});
	}
}

void records_progress_changed(struct ui_view *view)
{
	@autoreleasepool {
		float fraction = view->fraction;
		bool indeterminate = view->indeterminate;
		uikit_enqueue(^{
			ATLProgress *progress = (ATLProgress *)native(view);
			progress.bar.progress = fraction;
			progress.bar.hidden = indeterminate;
			progress.spinner.hidden = !indeterminate;
			if (indeterminate)
				[progress.spinner startAnimating];
			else
				[progress.spinner stopAnimating];
		});
	}
}

void records_visibility_changed(struct ui_view *view)
{
	@autoreleasepool {
		bool hidden = view->visibility != UI_VIEW_VISIBLE;
		CGFloat alpha = view->alpha;
		bool enabled = view->enabled;
		void *target = surface;
		uikit_enqueue(^{
			UIView *changed = native(view);
			UIView *surface_view = (__bridge UIView *)target;
			// activity roots: only the front one (records_root_shown) shows
			bool behind = surface_view && changed.superview == surface_view && changed != surface_view.subviews.lastObject;
			changed.hidden = hidden || behind;
			changed.alpha = alpha;
			changed.userInteractionEnabled = enabled;
			if ([changed isKindOfClass:[ATLButton class]])
				((ATLButton *)changed).enabled = enabled;
			if ([changed isKindOfClass:[UITextField class]])
				((UITextField *)changed).enabled = enabled;
		});
	}
}

static NSTextAlignment text_alignment(int alignment)
{
	return alignment == UI_TEXT_ALIGN_CENTER ? NSTextAlignmentCenter : alignment == UI_TEXT_ALIGN_END ? NSTextAlignmentRight : NSTextAlignmentNatural;
}

void records_text_changed(struct ui_view *view)
{
	@autoreleasepool {
		NSString *text = [NSString stringWithUTF8String:view->text] ?: @"";
		float size = view->text_size;
		int style = view->text_style;
		NSTextAlignment alignment = text_alignment(view->alignment);
		uikit_enqueue(^{
			[(id<ATLText>)native(view) atlSetText:text font:font(size, style) alignment:alignment];
		});
	}
}

void records_text_color_changed(struct ui_view *view, uint32_t argb)
{
	@autoreleasepool {
		uikit_enqueue(^{
			[(id<ATLText>)native(view) atlSetColor:color(argb)];
		});
	}
}

void records_button_changed(struct ui_view *view)
{
	@autoreleasepool {
		bool pressed = view->pressed, checked = view->checked;
		uikit_enqueue(^{
			[(ATLButton *)native(view) atlSetPressed:pressed checked:checked];
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
		bool is_button = view->is_button, has_background = view->has_background;
		bool edit = view->edit, multiline = view->edit_config.multiline, selectable = view->selectable;
		NSString *hint = view->edit_config.hint ? [NSString stringWithUTF8String:view->edit_config.hint] : nil;
		enum ui_button button = view->button;
		__block CGSize fit;
		// a view of its own: the record's view may still wait for queued changes
		dispatch_sync(dispatch_get_main_queue(), ^{
			CGFloat width = max_width < 0 ? CGFLOAT_MAX : max_width / s;
			if (is_button) {
				static ATLButton *scratches[UI_BUTTON_SWITCH + 1];
				if (!scratches[button])
					scratches[button] = [[ATLButton alloc] initWithRecord:NULL kind:button];
				ATLButton *scratch = scratches[button];
				scratch.hasBackground = has_background;
				[scratch atlSetText:text font:font(size, style) alignment:NSTextAlignmentNatural];
				fit = [scratch contentSizeThatFits:width];
				return;
			}
			if (edit || selectable) {
				static UITextField *field;
				static UITextView *area;
				if (!field) {
					field = [UITextField new];
					area = [UITextView new]; // as ATLEditView and ATLSelectableText lay out
					area.scrollEnabled = NO;
					area.textContainerInset = UIEdgeInsetsZero;
					area.textContainer.lineFragmentPadding = 0;
				}
				UIView *scratch = edit && !multiline ? field : area;
				UIFont *scratch_font = font(size, style);
				CGSize bound = CGSizeMake(width, CGFLOAT_MAX);
				fit = CGSizeZero;
				for (NSString *shown in hint && edit ? @[text, hint] : @[text]) {
					// an empty UITextField measures taller (22 pt at 14 pt) than one with text (18.7)
					NSString *measured = shown.length ? shown : @" ";
					if (scratch == field) {
						field.font = scratch_font;
						field.text = measured;
					} else {
						area.font = scratch_font;
						area.text = measured;
					}
					CGSize size = [scratch sizeThatFits:bound];
					fit = CGSizeMake(MAX(fit.width, size.width), MAX(fit.height, size.height));
				}
				fit.height = MAX(fit.height, scratch_font.lineHeight); // a field keeps one line's height
				return;
			}
			static ATLLabel *scratch;
			if (!scratch) {
				scratch = [ATLLabel new];
				scratch.numberOfLines = 0;
			}
			scratch.font = font(size, style);
			scratch.text = text;
			fit = [scratch sizeThatFits:CGSizeMake(width, CGFLOAT_MAX)];
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
	CGRect rect;    // pt
	CGSize content; // a scroll node's: its child's size
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
	CGSize content = CGSizeZero;
	if (view->kind == NODE_SCROLL && view->n_children) {
		// Android's extent: the child's size and the node's padding (margins don't count), along its axis
		struct ui_view *child = view->children[0];
		int width = view->horizontal ? view->padding_left + child->right - child->left + view->padding_right : view->right - view->left;
		int height = view->horizontal ? view->bottom - view->top : view->padding_top + child->bottom - child->top + view->padding_bottom;
		content = CGSizeMake(width / s, height / s);
	}
	*(*next)++ = (struct frame){view, CGRectMake(view->left / s, view->top / s, (view->right - view->left) / s, (view->bottom - view->top) / s), content};
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
			uikit_enqueue(^{
				for (size_t i = 0; i < count; i++) {
					UIView *framed = native(frames[i].view);
					framed.frame = frames[i].rect;
					if (frames[i].view->kind == NODE_SCROLL)
						((UIScrollView *)framed).contentSize = frames[i].content;
				}
				free(frames);
			});
		}
		flush();
	}
}

/* Draw passes run at most once per frame: a CADisplayLink on UIKit's thread, paused while no view
 * waits, posts the pass to the Android thread. A frame whose last batch UIKit hasn't run is skipped,
 * so UIKit's thread never waits and batches don't pile up behind it. */

static CADisplayLink *frame_link; // UIKit's thread

static void frame_post(void *data)
{
	@autoreleasepool {
		if (app_window)
			records_draw_pass(app_window);
	}
}

@interface ATLFrameTarget : NSObject
@end

@implementation ATLFrameTarget
- (void)tick:(CADisplayLink *)link
{
	if (atomic_load(&batches_in_flight))
		return;
	link.paused = YES;
	loop_main_post(frame_post, NULL);
}
@end

void records_draw_wanted(struct ui_window *window)
{
	@autoreleasepool {
		dispatch_async(dispatch_get_main_queue(), ^{
			if (!frame_link) {
				frame_link = [CADisplayLink displayLinkWithTarget:[ATLFrameTarget new] selector:@selector(tick:)];
				[frame_link addToRunLoop:NSRunLoop.mainRunLoop forMode:NSRunLoopCommonModes];
			}
			frame_link.paused = NO;
		});
	}
}

// UIKit's thread
static ATLDrawLayer *draw_layer(UIView *view, bool create)
{
	for (CALayer *sublayer in view.layer.sublayers) {
		if ([sublayer isKindOfClass:[ATLDrawLayer class]])
			return (ATLDrawLayer *)sublayer;
	}
	if (!create)
		return nil;
	ATLDrawLayer *layer = [ATLDrawLayer layer];
	layer.zPosition = -1; // under the subviews, whatever index UIKit gives them
	layer.drawsAsynchronously = YES;
	[view.layer insertSublayer:layer atIndex:0];
	return layer;
}

/* UIKit's thread: what the view's last draw recorded (NULL: nothing), size in points, s px per
 * point; at the bounds' origin, so it scrolls with the view's subviews as Android's content does */
static void show_list(UIView *view, struct gfx_canvas *list, CGSize size, CGFloat s)
{
	if ([view isKindOfClass:[ATLLabel class]]) {
		((ATLLabel *)view).list = list;
		return;
	}
	ATLDrawLayer *layer = draw_layer(view, list != NULL);
	if (!layer)
		return;
	layer.list = list;
	if (!list) {
		[layer removeFromSuperlayer];
		return;
	}
	layer.contentsScale = s;
	layer.frame = CGRectMake(0, 0, size.width, size.height);
	[layer setNeedsDisplay];
}

static struct gfx_canvas *shown_list(UIView *view, CGSize *size)
{
	if ([view isKindOfClass:[ATLLabel class]]) {
		*size = view.bounds.size;
		return ((ATLLabel *)view).list;
	}
	ATLDrawLayer *layer = draw_layer(view, false);
	*size = layer.bounds.size;
	return layer.list;
}

void records_view_drawn(struct ui_view *view)
{
	@autoreleasepool {
		struct gfx_canvas *list = view->list ? cg_list_ref(view->list) : NULL;
		CGFloat s = scale();
		CGSize size = CGSizeMake(view->drawn_width / s, view->drawn_height / s);
		uikit_enqueue(^{
			show_list(native(view), list, size, s);
			if (list)
				gfx_canvas_free(list);
		});
	}
}

void records_draw_done(struct ui_window *window)
{
	@autoreleasepool {
		if (!window->layout_pending) // else the layout pass flushes, with its frames
			flush();
	}
}

// a scroll node's UIScrollView animates and reports; another view's bounds shift its subviews
void records_scroll_changed(struct ui_view *view, bool animated)
{
	@autoreleasepool {
		CGFloat s = scale();
		CGPoint offset = CGPointMake(view->scroll_x / s, view->scroll_y / s);
		bool scroll_node = view->kind == NODE_SCROLL;
		uikit_enqueue(^{
			UIView *scrolled = native(view);
			if (scroll_node) {
				[(UIScrollView *)scrolled setContentOffset:offset animated:animated];
			} else {
				CGRect bounds = scrolled.bounds;
				bounds.origin = offset;
				scrolled.bounds = bounds;
			}
		});
	}
}

/* An edit view is the mirror UITextInput reads on UIKit's thread. Each committed change reports with
 * the state it started from; a report still waiting for the Android thread takes the next one too.
 * Java's snapshot applies only when it acks the last report and no marked text is open. */

static pthread_mutex_t edit_lock = PTHREAD_MUTEX_INITIALIZER;

static void edit_box_free(struct edit_report_box *box)
{
	if (!box)
		return;
	free(box->prev);
	free(box->text);
	free(box);
}

static void deliver_edit(void *data)
{
	struct edit_report_box *box = data;
	pthread_mutex_lock(&edit_lock);
	struct atl_edit_report report = {box->prev, box->prev_start, box->prev_end, box->text, box->start, box->end, box->seq};
	box->prev = box->text = NULL;
	box->posted = false;
	pthread_mutex_unlock(&edit_lock);
	if (report.prev && report.text)
		records_edit_report(box->view, &report); // skips a freed record
	free((char *)report.prev);
	free((char *)report.text);
}

static void edit_post(void (*fn)(void *), struct ui_view *view)
{
	if (view)
		loop_main_post(fn, view);
}

static void action_post(void *data)
{
	struct ui_view *view = data;
	if (view->ref)
		atl_upcall_edit_action(view->ref);
}

static void focus_in_post(void *data)
{
	struct ui_view *view = data;
	if (view->ref)
		atl_upcall_edit_focus(view->ref, true, true);
}

static void focus_out_post(void *data)
{
	struct ui_view *view = data;
	if (view->ref)
		atl_upcall_edit_focus(view->ref, false, false);
}


@implementation ATLEditState
@end

static NSString *editor_text(UIView<UITextInput> *editor)
{
	NSString *text = [editor isKindOfClass:[UITextField class]] ? ((UITextField *)editor).text : ((UITextView *)editor).text;
	return text ?: @"";
}

static NSRange editor_selection(UIView<UITextInput> *editor)
{
	UITextRange *range = editor.selectedTextRange;
	if (!range)
		return NSMakeRange(editor_text(editor).length, 0);
	NSInteger start = [editor offsetFromPosition:editor.beginningOfDocument toPosition:range.start];
	NSInteger end = [editor offsetFromPosition:editor.beginningOfDocument toPosition:range.end];
	return NSMakeRange(start, end - start);
}

static void editor_set(UIView<UITextInput> *editor, NSString *text, NSInteger start, NSInteger end)
{
	if (![editor_text(editor) isEqualToString:text]) {
		if ([editor isKindOfClass:[UITextField class]])
			((UITextField *)editor).text = text;
		else
			((UITextView *)editor).text = text;
	}
	UITextPosition *from = [editor positionFromPosition:editor.beginningOfDocument offset:start];
	UITextPosition *to = [editor positionFromPosition:editor.beginningOfDocument offset:end];
	if (from && to)
		editor.selectedTextRange = [editor textRangeFromPosition:from toPosition:to];
}

// a committed change goes to Java; marked text waits for its end
static void editor_changed(UIView<UITextInput> *editor, ATLEditState *state)
{
	if (state.applying || !state.record || !state.box)
		return;
	if (editor.markedTextRange) {
		state.wasMarked = true;
		return;
	}
	NSString *text = editor_text(editor);
	NSRange selection = editor_selection(editor);
	if (!state.wasMarked && [text isEqualToString:state.prev] && selection.location == (NSUInteger)state.prevStart
	    && NSMaxRange(selection) == (NSUInteger)state.prevEnd)
		return;
	state.wasMarked = false;
	struct edit_report_box *box = state.box;
	pthread_mutex_lock(&edit_lock);
	if (!box->posted) {
		free(box->prev);
		box->prev = strdup(state.prev.UTF8String);
		box->prev_start = state.prevStart;
		box->prev_end = state.prevEnd;
	}
	free(box->text);
	box->text = strdup(text.UTF8String);
	box->start = selection.location;
	box->end = NSMaxRange(selection);
	box->seq = ++state.seq;
	bool post = !box->posted;
	box->posted = true;
	pthread_mutex_unlock(&edit_lock);
	state.prev = text;
	state.prevStart = selection.location;
	state.prevEnd = NSMaxRange(selection);
	if (post && !loop_main_post(deliver_edit, box)) {
		pthread_mutex_lock(&edit_lock);
		box->posted = false;
		pthread_mutex_unlock(&edit_lock);
	}
}

static void editor_began(UIView *editor, ATLEditState *state)
{
	keyboard_field = editor;
	if (!state.javaFocus)
		edit_post(focus_in_post, state.record);
}

// the root takes the hardware keyboard back unless another field took the responder
static void editor_ended(UIView *editor, ATLEditState *state)
{
	if (keyboard_field == editor)
		keyboard_field = nil;
	if (!state.javaFocus)
		edit_post(focus_out_post, state.record);
	dispatch_async(dispatch_get_main_queue(), ^{
		if (keyboard_field)
			return;
		for (UIView *root = editor.superview; root; root = root.superview) {
			if ([root isKindOfClass:[ATLView class]] && ((ATLView *)root).activityRoot && !root.hidden) {
				[root becomeFirstResponder];
				break;
			}
		}
	});
}

@interface ATLEditField () <ATLEditor, UITextFieldDelegate>
@end

@implementation ATLEditField
@synthesize atlState = _atlState;

- (instancetype)init
{
	if ((self = [super initWithFrame:CGRectZero])) {
		_atlState = [ATLEditState new];
		_atlState.prev = @"";
		self.delegate = self;
		self.borderStyle = UITextBorderStyleNone;
		[self addTarget:self action:@selector(atlChanged) forControlEvents:UIControlEventEditingChanged];
	}
	return self;
}

- (void)atlChanged
{
	editor_changed(self, _atlState);
}

- (void)textFieldDidChangeSelection:(UITextField *)textField
{
	editor_changed(self, _atlState);
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField
{
	edit_post(action_post, _atlState.record);
	return NO;
}

- (void)atlReturn
{
	[self textFieldShouldReturn:self];
}

- (void)textFieldDidBeginEditing:(UITextField *)textField
{
	editor_began(self, _atlState);
}

- (void)textFieldDidEndEditing:(UITextField *)textField
{
	editor_changed(self, _atlState); // a composition the end commits
	editor_ended(self, _atlState);
}

- (CGRect)textRectForBounds:(CGRect)bounds
{
	return UIEdgeInsetsInsetRect(bounds, _atlState.padding);
}

- (CGRect)editingRectForBounds:(CGRect)bounds
{
	return UIEdgeInsetsInsetRect(bounds, _atlState.padding);
}

- (CGRect)placeholderRectForBounds:(CGRect)bounds
{
	return UIEdgeInsetsInsetRect(bounds, _atlState.padding);
}

// Java's text arrives through snapshots only
- (void)atlSetText:(NSString *)text font:(UIFont *)font alignment:(NSTextAlignment)alignment
{
	self.font = font;
	self.textAlignment = alignment;
}

- (void)atlSetColor:(UIColor *)color
{
	self.textColor = color;
}

- (void)atlSetPadding:(UIEdgeInsets)padding
{
	_atlState.padding = padding;
	[self setNeedsLayout];
}
@end

@interface ATLEditView : UITextView <ATLEditor, UITextViewDelegate>
@end

@implementation ATLEditView {
	UILabel *_placeholder;
}
@synthesize atlState = _atlState;

- (instancetype)init
{
	if ((self = [super initWithFrame:CGRectZero textContainer:nil])) {
		_atlState = [ATLEditState new];
		_atlState.prev = @"";
		_atlState.multiline = true;
		self.delegate = self;
		self.scrollEnabled = NO; // Java sizes it to its lines
		self.backgroundColor = UIColor.clearColor;
		self.textContainerInset = UIEdgeInsetsZero;
		self.textContainer.lineFragmentPadding = 0;
		_placeholder = [UILabel new];
		_placeholder.textColor = UIColor.placeholderTextColor;
		_placeholder.numberOfLines = 0;
		[self addSubview:_placeholder];
	}
	return self;
}

- (void)atlShowPlaceholder
{
	_placeholder.text = _atlState.hint;
	_placeholder.font = self.font;
	_placeholder.hidden = self.text.length > 0;
	[self setNeedsLayout];
}

- (void)layoutSubviews
{
	[super layoutSubviews];
	CGRect inner = UIEdgeInsetsInsetRect(self.bounds, self.textContainerInset);
	CGSize size = [_placeholder sizeThatFits:CGSizeMake(inner.size.width, CGFLOAT_MAX)];
	_placeholder.frame = CGRectMake(inner.origin.x, inner.origin.y, inner.size.width, size.height);
}

- (void)textViewDidChange:(UITextView *)textView
{
	_placeholder.hidden = self.text.length > 0;
	editor_changed(self, _atlState);
}

- (void)textViewDidChangeSelection:(UITextView *)textView
{
	editor_changed(self, _atlState);
}

- (void)textViewDidBeginEditing:(UITextView *)textView
{
	editor_began(self, _atlState);
}

- (void)textViewDidEndEditing:(UITextView *)textView
{
	editor_changed(self, _atlState);
	editor_ended(self, _atlState);
}

- (void)atlSetText:(NSString *)text font:(UIFont *)font alignment:(NSTextAlignment)alignment
{
	self.font = font;
	self.textAlignment = alignment;
	[self atlShowPlaceholder];
}

- (void)atlSetColor:(UIColor *)color
{
	self.textColor = color;
}

- (void)atlSetPadding:(UIEdgeInsets)padding
{
	_atlState.padding = padding;
	self.textContainerInset = padding;
}
@end

// a TextView with setTextIsSelectable: UIKit's selection and its edit menu, not editable
@interface ATLSelectableText : UITextView <ATLText>
@end

@implementation ATLSelectableText
- (instancetype)init
{
	if ((self = [super initWithFrame:CGRectZero textContainer:nil])) {
		self.editable = NO;
		self.selectable = YES;
		self.scrollEnabled = NO;
		self.backgroundColor = UIColor.clearColor;
		self.textContainerInset = UIEdgeInsetsZero;
		self.textContainer.lineFragmentPadding = 0;
	}
	return self;
}

// the label a UILabel derives from its text, for VoiceOver and the debug agent
- (void)atlSetText:(NSString *)text font:(UIFont *)font alignment:(NSTextAlignment)alignment
{
	self.text = text;
	self.font = font;
	self.textAlignment = alignment;
	self.accessibilityLabel = text;
}

- (void)atlSetColor:(UIColor *)color
{
	self.textColor = color;
}

- (void)atlSetPadding:(UIEdgeInsets)padding
{
	self.textContainerInset = padding;
}
@end

// UIKit's thread: the keyboard's field when its root shows, else the root for the hardware keyboard
static void root_take_responder(UIView *root)
{
	if (keyboard_field && [keyboard_field isDescendantOfView:root]) {
		ATLEditState *state = ((id<ATLEditor>)keyboard_field).atlState;
		state.javaFocus = true;
		[keyboard_field becomeFirstResponder];
		state.javaFocus = false;
		return;
	}
	[root becomeFirstResponder];
}

/* UIKit's thread: replacement takes the old view's place, record and first responder; enabled is
 * the record's (a UILabel's own interaction is off) */
static void replace_native(struct ui_view *view, UIView *replacement, bool enabled)
{
	UIView *old = native(view);
	replacement.frame = old.frame;
	replacement.hidden = old.hidden;
	replacement.alpha = old.alpha;
	replacement.userInteractionEnabled = enabled;
	if ([replacement isKindOfClass:[UITextField class]])
		((UITextField *)replacement).enabled = enabled;
	replacement.backgroundColor = old.backgroundColor;
	replacement.multipleTouchEnabled = YES;
	bool responder = old.isFirstResponder;
	if (old.superview)
		[old.superview insertSubview:replacement aboveSubview:old];
	CGSize drawn;
	struct gfx_canvas *list = shown_list(old, &drawn);
	show_list(replacement, list, drawn, scale());
	// the old editor's end of editing and the new one's start are the swap's, not the user's
	ATLEditState *state = [old conformsToProtocol:@protocol(ATLEditor)] ? ((id<ATLEditor>)old).atlState : nil;
	state.javaFocus = true;
	if (keyboard_field == old)
		keyboard_field = replacement;
	[old removeFromSuperview];
	CFBridgingRelease(view->native);
	view->native = (void *)CFBridgingRetain(replacement);
	if (responder)
		[replacement becomeFirstResponder];
	if ([replacement conformsToProtocol:@protocol(ATLEditor)] && ((id<ATLEditor>)replacement).atlState == state)
		state.javaFocus = false;
}

// the number and phone pads have no return key: a bar above them holds it
static UIToolbar *return_bar(UIView *field, enum ui_return_key key)
{
	static NSString *const titles[] = {
		[UI_RETURN_DEFAULT] = @"Return", [UI_RETURN_GO] = @"Go", [UI_RETURN_SEARCH] = @"Search",
		[UI_RETURN_NEXT] = @"Next", [UI_RETURN_SEND] = @"Send",
	};
	UIToolbar *bar = [[UIToolbar alloc] initWithFrame:CGRectMake(0, 0, 320, 44)];
	UIBarButtonItem *item = key == UI_RETURN_DONE
	                            ? [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemDone target:field action:@selector(atlReturn)]
	                            : [[UIBarButtonItem alloc] initWithTitle:titles[key] style:UIBarButtonItemStyleDone target:field action:@selector(atlReturn)];
	bar.items = @[[[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemFlexibleSpace target:nil action:nil], item];
	[bar sizeToFit];
	return bar;
}

static UITextAutocapitalizationType autocap_type(enum ui_autocap autocap)
{
	static const UITextAutocapitalizationType types[] = {
		[UI_AUTOCAP_NONE] = UITextAutocapitalizationTypeNone, [UI_AUTOCAP_WORDS] = UITextAutocapitalizationTypeWords,
		[UI_AUTOCAP_SENTENCES] = UITextAutocapitalizationTypeSentences, [UI_AUTOCAP_CHARACTERS] = UITextAutocapitalizationTypeAllCharacters,
	};
	return types[autocap];
}

void records_edit_config_changed(struct ui_view *view)
{
	@autoreleasepool {
		struct ui_edit_config config = view->edit_config;
		bool enabled = view->enabled;
		NSString *hint = config.hint ? [NSString stringWithUTF8String:config.hint] : nil;
		uikit_enqueue(^{
			UIView<ATLEditor> *editor = (UIView<ATLEditor> *)native(view);
			ATLEditState *state = editor.atlState;
			if (state.multiline != config.multiline) {
				UIView<ATLEditor> *replacement = config.multiline ? (UIView<ATLEditor> *)[ATLEditView new] : (UIView<ATLEditor> *)[ATLEditField new];
				NSString *text = editor_text(editor);
				NSRange selection = editor_selection(editor);
				UIFont *font = [(id)editor font];
				UIColor *text_color = [(id)editor textColor];
				NSTextAlignment alignment = [(id)editor textAlignment];
				replacement.atlState = state;
				state.multiline = config.multiline;
				state.applying = true;
				[replacement atlSetText:text font:font alignment:alignment];
				[replacement atlSetColor:text_color];
				[replacement atlSetPadding:state.padding];
				editor_set(replacement, text, selection.location, NSMaxRange(selection));
				state.applying = false;
				replace_native(view, replacement, enabled);
				editor = replacement;
			}
			static const UIKeyboardType keyboards[] = {
				[UI_KEYBOARD_TEXT] = UIKeyboardTypeDefault, [UI_KEYBOARD_NUMBER] = UIKeyboardTypeNumberPad,
				[UI_KEYBOARD_DECIMAL] = UIKeyboardTypeDecimalPad, [UI_KEYBOARD_PHONE] = UIKeyboardTypePhonePad,
				[UI_KEYBOARD_EMAIL] = UIKeyboardTypeEmailAddress, [UI_KEYBOARD_URL] = UIKeyboardTypeURL,
			};
			static const UIReturnKeyType returns[] = {
				[UI_RETURN_DEFAULT] = UIReturnKeyDefault, [UI_RETURN_DONE] = UIReturnKeyDone, [UI_RETURN_GO] = UIReturnKeyGo,
				[UI_RETURN_SEARCH] = UIReturnKeySearch, [UI_RETURN_NEXT] = UIReturnKeyNext, [UI_RETURN_SEND] = UIReturnKeySend,
			};
			editor.keyboardType = keyboards[config.keyboard];
			editor.returnKeyType = returns[config.return_key];
			editor.autocapitalizationType = autocap_type(config.autocap);
			editor.autocorrectionType = config.autocorrect ? UITextAutocorrectionTypeDefault : UITextAutocorrectionTypeNo;
			editor.spellCheckingType = config.autocorrect ? UITextSpellCheckingTypeDefault : UITextSpellCheckingTypeNo;
			editor.secureTextEntry = config.secure;
			editor.accessibilityLabel = hint;
			state.hint = hint;
			if ([editor isKindOfClass:[UITextField class]]) {
				((UITextField *)editor).placeholder = hint;
				bool pad = config.keyboard == UI_KEYBOARD_NUMBER || config.keyboard == UI_KEYBOARD_DECIMAL || config.keyboard == UI_KEYBOARD_PHONE;
				((UITextField *)editor).inputAccessoryView = pad ? return_bar(editor, config.return_key) : nil;
			}
			else
				[(ATLEditView *)editor atlShowPlaceholder];
			if (editor.isFirstResponder)
				[editor reloadInputViews];
		});
	}
}

void records_edit_state_changed(struct ui_view *view, const char *text, int start, int end, uint32_t ack)
{
	records_edit_set(view, text, start, end);
	@autoreleasepool {
		NSString *string = [NSString stringWithUTF8String:text] ?: @"";
		uikit_enqueue(^{
			UIView<ATLEditor> *editor = (UIView<ATLEditor> *)native(view);
			ATLEditState *state = editor.atlState;
			if (ack != state.seq || editor.markedTextRange)
				return; // its newer report makes Java answer again
			state.applying = true;
			editor_set(editor, string, start, end);
			if ([editor isKindOfClass:[ATLEditView class]])
				[(ATLEditView *)editor atlShowPlaceholder];
			state.applying = false;
			state.prev = string;
			state.prevStart = start;
			state.prevEnd = end;
		});
	}
}

bool records_edit_keyboard(struct ui_view *view, bool shown)
{
	@autoreleasepool {
		uikit_enqueue(^{
			UIView<ATLEditor> *editor = (UIView<ATLEditor> *)native(view);
			ATLEditState *state = editor.atlState;
			state.javaFocus = true;
			if (shown) {
				keyboard_field = editor;
				[editor becomeFirstResponder];
			} else if (editor.isFirstResponder) {
				[editor resignFirstResponder];
			}
			state.javaFocus = false;
			if (!shown && keyboard_field == editor)
				keyboard_field = nil;
		});
	}
	return true;
}

void records_selectable_changed(struct ui_view *view)
{
	@autoreleasepool {
		NSString *text = [NSString stringWithUTF8String:view->text] ?: @"";
		float size = view->text_size;
		int style = view->text_style;
		NSTextAlignment alignment = text_alignment(view->alignment);
		CGFloat s = scale();
		UIEdgeInsets padding = UIEdgeInsetsMake(view->padding_top / s, view->padding_left / s, view->padding_bottom / s, view->padding_right / s);
		bool selectable = view->selectable, enabled = view->enabled;
		uikit_enqueue(^{
			UIView<ATLText> *old = (UIView<ATLText> *)native(view);
			UIColor *text_color = [(id)old textColor];
			UIView<ATLText> *replacement;
			if (selectable) {
				replacement = [ATLSelectableText new];
			} else {
				ATLLabel *label = [ATLLabel new];
				label.numberOfLines = 0;
				replacement = label;
			}
			[replacement atlSetText:text font:font(size, style) alignment:alignment];
			if (text_color)
				[replacement atlSetColor:text_color];
			[replacement atlSetPadding:padding];
			replace_native(view, replacement, enabled);
		});
	}
}
