#import <UIKit/UIKit.h>

#import "panel.h"

/* Metrics, pt: iOS 27's UIAlertController on the iPhone 13, and rows, a progress view and content
 * that it doesn't have. */
static const CGFloat max_width = 320;
const CGFloat panel_margin = 20;
static const CGFloat corner = 34;
static const CGFloat text_top = 22;    // to the title's frame
static const CGFloat text_side = 30;
static const CGFloat text_gap = 8;     // the title's frame to the message's
static const CGFloat header_end = 4;   // after the last text, row or progress
static const CGFloat part_gap = 12;    // between the texts, the progress and the rows
static const CGFloat row_height = 44;
static const CGFloat edge = 16;        // the buttons' and the root's side, and above and below the buttons
static const CGFloat button_height = 48;
static const CGFloat button_gap = 8;

CGFloat panel_width(CGFloat surface_width)
{
	return MIN(max_width, surface_width - 2 * panel_margin);
}

const CGFloat panel_side = edge;

CGFloat panel_header_cap(CGFloat max_height, CGFloat footer, bool content)
{
	CGFloat room = max_height - footer;
	return content ? room / 2 : room;
}

UIColor *panel_dim_color(void)
{
	return [UIColor colorWithDynamicProvider:^UIColor *(UITraitCollection *traits) {
		return [UIColor colorWithWhite:0 alpha:traits.userInterfaceStyle == UIUserInterfaceStyleDark ? 0.48 : 0.2];
	}];
}

@implementation ATLPanelView {
	UIVisualEffectView *background;
	UILabel *title, *message;
	UIActivityIndicatorView *spinner;
	UIProgressView *bar;
	UIScrollView *rowsView; // the rows, scrolling when the screen can't hold them
	NSMutableArray<UIButton *> *rows;
	enum ui_alert_check check;
	enum ui_alert_progress progress;
	NSMutableArray<NSNumber *> *checked;
	UIButton *buttons[3]; // positive, negative, neutral; hidden without a label
}

- (instancetype)initWithFrame:(CGRect)frame
{
	self = [super initWithFrame:frame];
	if (!self)
		return nil;
	UIVisualEffect *effect;
	if (@available(iOS 26.0, *))
		effect = [UIGlassEffect effectWithStyle:UIGlassEffectStyleRegular];
	else
		effect = [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemMaterial];
	background = [[UIVisualEffectView alloc] initWithEffect:effect];
	background.layer.cornerRadius = corner;
	background.layer.cornerCurve = kCACornerCurveContinuous;
	background.clipsToBounds = YES;
	[self addSubview:background];
	title = [UILabel new];
	title.font = [UIFont preferredFontForTextStyle:UIFontTextStyleHeadline];
	title.adjustsFontForContentSizeCategory = YES;
	title.numberOfLines = 0;
	title.accessibilityTraits = UIAccessibilityTraitHeader;
	message = [UILabel new];
	message.font = [UIFont preferredFontForTextStyle:UIFontTextStyleSubheadline];
	message.adjustsFontForContentSizeCategory = YES;
	message.numberOfLines = 0;
	message.textColor = UIColor.secondaryLabelColor;
	spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleMedium];
	bar = [[UIProgressView alloc] initWithProgressViewStyle:UIProgressViewStyleDefault];
	rows = [NSMutableArray array];
	rowsView = [UIScrollView new];
	_atlHeaderCap = CGFLOAT_MAX;
	for (UIView *part in @[title, message, spinner, bar, rowsView])
		[self addSubview:part];
	for (int i = 0; i < 3; i++) {
		UIButton *button = buttons[i] = [UIButton buttonWithConfiguration:[ATLPanelView buttonConfiguration] primaryAction:nil];
		button.tag = -1 - i;
		[button addTarget:self action:@selector(tapped:) forControlEvents:UIControlEventTouchUpInside];
		[self addSubview:button];
	}
	return self;
}

// the alert's action: a capsule in the tertiary fill, its title in medium body text
+ (UIButtonConfiguration *)buttonConfiguration
{
	UIButtonConfiguration *config = [UIButtonConfiguration filledButtonConfiguration];
	config.cornerStyle = UIButtonConfigurationCornerStyleCapsule;
	config.baseBackgroundColor = UIColor.tertiarySystemFillColor;
	config.baseForegroundColor = UIColor.labelColor;
	config.titleLineBreakMode = NSLineBreakByTruncatingTail;
	config.titleTextAttributesTransformer = ^NSDictionary<NSAttributedStringKey, id> *(NSDictionary<NSAttributedStringKey, id> *attributes) {
		NSMutableDictionary *styled = [attributes mutableCopy];
		UIFont *body = [UIFont preferredFontForTextStyle:UIFontTextStyleBody];
		styled[NSFontAttributeName] = [UIFont systemFontOfSize:body.pointSize weight:UIFontWeightMedium];
		return styled;
	};
	return config;
}

- (void)tapped:(UIButton *)sender
{
	int which = (int)sender.tag;
	if (which >= 0 && check != UI_ALERT_CHECK_NONE) { // the check moves at once, as UIKit's own lists do
		for (NSUInteger i = 0; i < checked.count; i++) {
			bool on = check == UI_ALERT_CHECK_MULTI ? (i == (NSUInteger)which ? !checked[i].boolValue : checked[i].boolValue) : i == (NSUInteger)which;
			checked[i] = @(on);
		}
		[self showChecks];
	}
	if (_action)
		_action(which);
}

- (void)showChecks
{
	for (NSUInteger i = 0; i < rows.count; i++) {
		bool on = i < checked.count && checked[i].boolValue;
		UIButtonConfiguration *config = rows[i].configuration;
		config.image = on ? [UIImage systemImageNamed:@"checkmark"] : nil;
		rows[i].configuration = config;
		rows[i].accessibilityTraits = UIAccessibilityTraitButton | (on ? UIAccessibilityTraitSelected : 0);
	}
}

static NSString *string(const char *chars)
{
	return chars ? [NSString stringWithUTF8String:chars] : nil;
}

- (void)atlSetAlert:(const struct ui_alert *)alert
{
	_atlContent = alert->content;
	self.overrideUserInterfaceStyle = alert->light ? UIUserInterfaceStyleLight : UIUserInterfaceStyleDark;
	title.text = string(alert->title);
	title.hidden = !alert->title;
	message.text = string(alert->message);
	message.hidden = !alert->message;
	progress = alert->progress;
	[self atlSetProgress:alert->value max:alert->max indeterminate:alert->indeterminate];
	for (UIButton *row in rows)
		[row removeFromSuperview];
	[rows removeAllObjects];
	check = alert->check;
	checked = [NSMutableArray array];
	for (int i = 0; i < alert->n_items; i++) {
		UIButtonConfiguration *config = [UIButtonConfiguration plainButtonConfiguration];
		config.title = string(alert->items[i]) ?: @"";
		config.imagePlacement = NSDirectionalRectEdgeTrailing;
		config.contentInsets = NSDirectionalEdgeInsetsZero;
		config.baseForegroundColor = UIColor.labelColor;
		UIButton *row = [UIButton buttonWithConfiguration:config primaryAction:nil];
		row.contentHorizontalAlignment = UIControlContentHorizontalAlignmentFill;
		row.tag = i;
		[row addTarget:self action:@selector(tapped:) forControlEvents:UIControlEventTouchUpInside];
		[rowsView addSubview:row];
		[rows addObject:row];
		[checked addObject:@(alert->checked && alert->checked[i])];
	}
	[self showChecks];
	for (int i = 0; i < 3; i++)
		[self atlSetButton:i label:string(alert->buttons[i]) enabled:!alert->disabled[i]];
	[self setNeedsLayout];
}

// UIProgressView has no indeterminate look: a spinner stands in
- (void)atlSetProgress:(int)value max:(int)max indeterminate:(BOOL)indeterminate
{
	bool spins = progress == UI_ALERT_PROGRESS_SPINNER || (progress == UI_ALERT_PROGRESS_BAR && indeterminate);
	spinner.hidden = !spins;
	bar.hidden = progress != UI_ALERT_PROGRESS_BAR || spins;
	if (spins)
		[spinner startAnimating];
	else
		[spinner stopAnimating];
	bar.progress = max > 0 ? (float)value / max : 0;
	[self setNeedsLayout];
}

- (void)atlSetButton:(int)index label:(NSString *)label enabled:(BOOL)enabled
{
	UIButton *button = buttons[index];
	UIButtonConfiguration *config = button.configuration;
	config.title = label;
	button.configuration = config;
	button.hidden = !label;
	button.enabled = enabled;
	[self setNeedsLayout];
}

// the shown buttons as UIAlertController orders Android's: neutral, negative, positive
- (NSArray<UIButton *> *)shownButtons
{
	NSMutableArray<UIButton *> *shown = [NSMutableArray array];
	for (int i = 2; i >= 0; i--) {
		if (!buttons[i].hidden)
			[shown addObject:buttons[i]];
	}
	return shown;
}

// two that fit side by side sit so, as UIAlertController's do; else a column
- (bool)sideBySide:(NSArray<UIButton *> *)shown width:(CGFloat)width
{
	if (shown.count != 2)
		return false;
	CGFloat half = (width - 2 * edge - button_gap) / 2;
	for (UIButton *button in shown) {
		if ([button sizeThatFits:CGSizeMake(CGFLOAT_MAX, button_height)].width > half)
			return false;
	}
	return true;
}

// lays the parts out at width (when apply), returns the header's height, at most cap with rows
- (CGFloat)layHeader:(CGFloat)width cap:(CGFloat)cap apply:(bool)apply
{
	CGFloat inner = width - 2 * text_side, y = text_top;
	bool parts = false;
	for (UILabel *label in @[title, message]) {
		if (label.hidden)
			continue;
		if (parts)
			y += text_gap;
		CGFloat height = ceil([label sizeThatFits:CGSizeMake(inner, CGFLOAT_MAX)].height);
		if (apply)
			label.frame = CGRectMake(text_side, y, inner, height);
		y += height;
		parts = true;
	}
	UIView *progress = !spinner.hidden ? spinner : !bar.hidden ? bar : nil;
	if (progress) {
		y += parts ? part_gap : 0;
		CGSize size = [progress sizeThatFits:CGSizeMake(inner, CGFLOAT_MAX)];
		if (apply)
			progress.frame = progress == bar ? CGRectMake(text_side, y, inner, size.height) : CGRectMake(text_side, y, size.width, size.height);
		y += size.height;
		parts = true;
	}
	rowsView.hidden = !rows.count;
	if (rows.count) {
		y += parts ? part_gap / 2 : 0;
		CGFloat full = rows.count * row_height;
		CGFloat shown = MAX(MIN(full, cap - y - header_end), 0); // the rest scrolls
		if (apply) {
			rowsView.frame = CGRectMake(text_side, y, inner, shown);
			rowsView.contentSize = CGSizeMake(inner, full);
			for (NSUInteger i = 0; i < rows.count; i++)
				rows[i].frame = CGRectMake(0, i * row_height, inner, row_height);
		}
		y += shown;
		parts = true;
	}
	return parts ? y + header_end : edge;
}

- (CGFloat)layFooter:(CGFloat)width top:(CGFloat)top apply:(bool)apply
{
	NSArray<UIButton *> *shown = [self shownButtons];
	if (!shown.count)
		return text_top;
	CGFloat inner = width - 2 * edge, y = top + edge;
	if ([self sideBySide:shown width:width]) {
		CGFloat half = (inner - button_gap) / 2;
		if (apply) {
			shown[0].frame = CGRectMake(edge, y, half, button_height);
			shown[1].frame = CGRectMake(edge + half + button_gap, y, half, button_height);
		}
		return edge + button_height + edge;
	}
	for (UIButton *button in shown) {
		if (apply)
			button.frame = CGRectMake(edge, y, inner, button_height);
		y += button_height + button_gap;
	}
	return y - button_gap - top + edge;
}

- (CGFloat)atlHeaderHeightForWidth:(CGFloat)width cap:(CGFloat)cap
{
	return [self layHeader:width cap:cap apply:false];
}

- (CGFloat)atlFooterHeightForWidth:(CGFloat)width
{
	return [self layFooter:width top:0 apply:false];
}

- (void)layoutSubviews
{
	[super layoutSubviews];
	CGRect bounds = self.bounds;
	background.frame = bounds;
	[self layHeader:bounds.size.width cap:_atlHeaderCap apply:true];
	CGFloat footer = [self atlFooterHeightForWidth:bounds.size.width];
	[self layFooter:bounds.size.width top:bounds.size.height - footer apply:true];
}
@end
