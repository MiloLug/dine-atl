#include <stdlib.h>
#include <string.h>

#include "src/shared/input.h"
#include "src/shared/upcall.h"
#include "src/slots/gfx.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "records.h"

/* Layout mirrors GTK's: the root at the window size, then each ui_view_new view lays out its
 * children. The backend measures text. */

#define MEASURE_SPEC_EXACTLY (1 << 30)
#define MEASURE_SPEC_AT_MOST (2 << 30)
#define MEASURE_SPEC_MASK    (0x3 << 30)

#define DEFAULT_TEXT_SIZE_SP 14.f

#define MAX_PASSES_IN_A_ROW  8

static struct ui_window *the_window;

void ui_display_get(struct ui_display *display)
{
	const struct atl_window_config *config = atl_window_get();
	display->width = config->width;
	display->height = config->height;
	display->night = config->night;
}

static void lay_out(struct ui_window *window)
{
	struct ui_view *root = window->shown;
	if (!root)
		return;
	if (++window->passes_in_a_row > MAX_PASSES_IN_A_ROW) {
		fprintf(stderr, "records ui: layout still requested after %d passes, giving up\n", MAX_PASSES_IN_A_ROW);
		return;
	}

	const struct atl_window_config *config = atl_window_get();
	root->left = root->top = 0;
	root->right = config->width;
	root->bottom = config->height;
	atl_upcall_view_measure(root->ref, config->width | MEASURE_SPEC_EXACTLY, config->height | MEASURE_SPEC_EXACTLY);
	atl_upcall_view_layout(root->ref, config->width, config->height);

	if (!window->ready) {
		window->ready = true;
		atl_upcall_window_ready();
	}
}

// the observers of one kind, as of now: a listener may add or remove observations
static void notify_observers(struct ui_window *window, enum ui_observe what)
{
	size_t count = 0;
	for (struct ui_observation *o = window->observations; o; o = o->next)
		count += o->what == what;
	if (!count)
		return;
	struct atl_ref **observers = malloc(count * sizeof(*observers)), **next = observers;
	for (struct ui_observation *o = window->observations; o; o = o->next) {
		if (o->what == what)
			*next++ = atl_ref_dup(o->observer);
	}
	for (size_t i = 0; i < count; i++) {
		if (what == UI_OBSERVE_LAYOUT)
			atl_upcall_global_layout(observers[i]);
		else
			atl_upcall_pre_draw(observers[i]);
		atl_ref_free(observers[i]);
	}
	free(observers);
}

static void draw_view(struct ui_view *view)
{
	view->dirty = false; // an invalidate inside its draw() is for the next pass
	view->drawn_width = view->right - view->left;
	view->drawn_height = view->bottom - view->top;
	if (view->list)
		gfx_canvas_free(view->list);
	view->list = gfx_canvas_new();
	atl_upcall_view_draw(view->ref, view->list);
	records_test_view_drawn(view);
}

static void draw_tree(struct ui_view *view)
{
	if (view->visibility != UI_VIEW_VISIBLE)
		return;
	if (view->draws && (view->dirty || view->right - view->left != view->drawn_width || view->bottom - view->top != view->drawn_height))
		draw_view(view);
	for (size_t i = 0; i < view->n_children; i++)
		draw_tree(view->children[i]);
}

// a layout pass stands in for the frame, its draws included
static void layout_pass(void *data)
{
	struct ui_window *window = data;
	window->layout_pending = false;
	lay_out(window);
	notify_observers(window, UI_OBSERVE_LAYOUT);
	notify_observers(window, UI_OBSERVE_DRAW);
	if (window->shown)
		draw_tree(window->shown);
	if (!window->layout_pending) // a listener that keeps requesting layout counts too
		window->passes_in_a_row = 0;
	records_layout_done(window);
}

static void draw_pass(void *data)
{
	struct ui_window *window = data;
	window->draw_pending = false;
	if (!window->layout_pending && window->shown) // else the layout pass draws
		draw_tree(window->shown);
}

static void schedule_draw(struct ui_window *window)
{
	if (window->draw_pending || window->layout_pending)
		return;
	window->draw_pending = true;
	loop_main_post(draw_pass, window);
}

static void schedule_layout(struct ui_window *window)
{
	if (window->layout_pending)
		return;
	window->layout_pending = true;
	loop_main_post(layout_pass, window);
}

struct ui_window *ui_window_new(void *surface)
{
	the_window = calloc(1, sizeof(*the_window));
	records_window_created(the_window, surface);
	return the_window;
}

struct ui_window *records_app_window(void)
{
	return the_window;
}

void records_touch(const struct atl_touch *touch)
{
	if (the_window && the_window->shown)
		atl_upcall_touch(the_window->shown->ref, touch);
}

void records_key(const struct atl_key *key)
{
	if (the_window && the_window->shown)
		atl_upcall_key(the_window->shown->ref, key);
}

void ui_window_set_title(struct ui_window *window, const char *title) {}

void ui_window_clear_background(struct ui_window *window) {}

void ui_window_add_activity(struct ui_window *window, struct ui_view *root)
{
	records_root_added(window, root);
}

void ui_window_show_activity(struct ui_window *window, struct ui_view *root)
{
	window->shown = root;
	records_root_shown(window, root);
	schedule_layout(window);
}

void ui_window_remove_activity(struct ui_window *window, struct ui_view *root)
{
	if (window->shown == root)
		window->shown = NULL;
	records_root_removed(window, root);
}

void ui_window_set_back_enabled(struct ui_window *window, bool enabled) {}

void ui_window_set_frame(struct ui_window *window, const struct ui_frame *frame)
{
	struct ui_frame *current = &window->frame;
	if (current->has_color == frame->has_color && current->fullscreen == frame->fullscreen
	    && (!frame->has_color || (current->color == frame->color && current->light == frame->light)))
		return;
	*current = *frame;
	records_frame_changed(window);
}

void ui_window_close(struct ui_window *window)
{
	records_window_closed(window);
	loop_main_quit();
}

void ui_window_get_size(struct ui_window *window, int *width, int *height)
{
	*width = atl_window_get()->width;
	*height = atl_window_get()->height;
}

bool ui_window_is_multi_window(struct ui_window *window)
{
	return false;
}

struct ui_observation *ui_window_observe(struct ui_window *window, struct atl_ref *observer, enum ui_observe what)
{
	struct ui_observation *observation = malloc(sizeof(*observation));
	*observation = (struct ui_observation){.next = window->observations, .window = window, .observer = observer, .what = what};
	window->observations = observation;
	return observation;
}

void ui_window_unobserve(struct ui_window *window, struct ui_observation *observation)
{
	if (observation->window) {
		for (struct ui_observation **link = &observation->window->observations; *link; link = &(*link)->next) {
			if (*link == observation) {
				*link = observation->next;
				break;
			}
		}
	}
	atl_ref_free(observation->observer);
	free(observation);
}

void records_observations_orphan(struct ui_window *window)
{
	for (struct ui_observation *o = window->observations; o; o = o->next)
		o->window = NULL;
	window->observations = NULL;
}

// the caller sets its kind's fields, then calls records_view_created
static struct ui_view *view_new(enum node_kind kind, struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits)
{
	struct ui_view *view = calloc(1, sizeof(*view));
	view->kind = kind;
	view->ref = view_ref;
	view->class_name = strdup(class_name);
	view->draws = view->dirty = traits->draws;
	view->alpha = 1;
	view->enabled = true;
	if (kind == NODE_TEXT) {
		view->text = strdup("");
		view->text_size = DEFAULT_TEXT_SIZE_SP * atl_window_get()->density_dpi / 160;
	}
	return view;
}

static struct ui_view *created(struct ui_view *view)
{
	records_view_created(view);
	return view;
}

struct ui_view *ui_view_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits)
{
	return created(view_new(NODE_VIEW, view_ref, class_name, traits));
}

struct ui_view *ui_scroll_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits, bool horizontal)
{
	struct ui_view *view = view_new(NODE_SCROLL, view_ref, class_name, traits);
	view->horizontal = horizontal;
	return created(view);
}

void ui_view_scroll_to(struct ui_view *view, int x, int y, bool animated)
{
	view->scroll_x = x;
	view->scroll_y = y;
	records_scroll_changed(view, animated);
}

// focus is Java's; a field's input target follows ui_edit_set_keyboard
void ui_view_set_focused(struct ui_view *view, bool focused) {}

void ui_view_add_child(struct ui_view *parent, struct ui_view *child, int index)
{
	if (index < 0 || (size_t)index > parent->n_children)
		index = parent->n_children;
	parent->children = realloc(parent->children, (parent->n_children + 1) * sizeof(*parent->children));
	memmove(&parent->children[index + 1], &parent->children[index], (parent->n_children - index) * sizeof(*parent->children));
	parent->children[index] = child;
	parent->n_children++;
	records_child_added(parent, child, index);
}

void ui_view_remove_child(struct ui_view *parent, struct ui_view *child)
{
	for (size_t i = 0; i < parent->n_children; i++) {
		if (parent->children[i] == child) {
			memmove(&parent->children[i], &parent->children[i + 1], (parent->n_children - i - 1) * sizeof(*parent->children));
			parent->n_children--;
			records_child_removed(parent, child);
			return;
		}
	}
}

static void hold(struct gfx_drawable **held, struct gfx_drawable *drawable)
{
	if (drawable)
		gfx_drawable_ref(drawable);
	if (*held)
		gfx_drawable_unref(*held);
	*held = drawable;
}

static void set_background(struct ui_view *view, struct gfx_drawable *drawable)
{
	hold(&view->background, drawable);
}

void ui_view_free(struct ui_view *view)
{
	records_test_view_freed(view);
	free(view->children); // the children may be freed already
	free(view->class_name);
	free(view->text);
	free((char *)view->edit_config.hint);
	set_background(view, NULL);
	hold(&view->image, NULL);
	if (view->list)
		gfx_canvas_free(view->list);
	atl_ref_free(view->ref);
	view->ref = NULL; // uikit: a scroll report still queued finds the view gone
	records_view_freed(view);
}

void ui_view_set_layout_params(struct ui_view *view, const struct ui_layout_params *params) {}

void ui_view_set_padding(struct ui_view *view, int left, int top, int right, int bottom)
{
	view->padding_left = left;
	view->padding_top = top;
	view->padding_right = right;
	view->padding_bottom = bottom;
	records_padding_changed(view);
}

void ui_view_set_background_color(struct ui_view *view, uint32_t argb)
{
	set_background(view, NULL);
	view->has_background = true;
	records_background_changed(view, argb);
}

void ui_view_set_background_drawable(struct ui_view *view, struct gfx_drawable *drawable)
{
	set_background(view, drawable);
	view->has_background = drawable;
	records_background_changed(view, 0);
}

void ui_view_set_visibility(struct ui_view *view, int visibility, float alpha, bool enabled)
{
	// a subtree that shows again may hold views invalidated while hidden; Java requests a layout
	// only for a change to or from GONE
	if (visibility == UI_VIEW_VISIBLE && view->visibility != UI_VIEW_VISIBLE && the_window)
		schedule_draw(the_window);
	view->visibility = visibility;
	view->alpha = alpha;
	view->enabled = enabled;
	records_visibility_changed(view);
}

void ui_view_invalidate(struct ui_view *view)
{
	if (!view->draws)
		return;
	view->dirty = true;
	if (the_window)
		schedule_draw(the_window);
}

void ui_view_draw_background(struct ui_view *view, struct gfx_canvas *canvas) {}
void ui_view_draw_content(struct ui_view *view, struct gfx_canvas *canvas) {}
void ui_view_draw_children(struct ui_view *view, struct gfx_canvas *canvas) {}
void ui_view_draw_child(struct ui_view *view, struct ui_view *child, struct gfx_canvas *canvas) {}

// layout applies the translations: a pass moves the frames
void ui_view_children_moved(struct ui_view *view)
{
	ui_view_request_layout(view);
}

void ui_view_request_layout(struct ui_view *view)
{
	if (the_window)
		schedule_layout(the_window);
}

static int resolve(int natural, int spec)
{
	int size = spec & ~MEASURE_SPEC_MASK;
	switch (spec & MEASURE_SPEC_MASK) {
		case MEASURE_SPEC_EXACTLY:
			return size;
		case MEASURE_SPEC_AT_MOST:
			return natural < size ? natural : size;
		default:
			return natural;
	}
}

void ui_view_measure(struct ui_view *view, int width_spec, int height_spec, int *width, int *height)
{
	int padding_h = view->padding_left + view->padding_right;
	int padding_v = view->padding_top + view->padding_bottom;
	int content_width = 0, content_height = 0;
	if (view->kind == NODE_TEXT) {
		int max_width = -1; // UNSPECIFIED
		if (width_spec & MEASURE_SPEC_MASK) {
			max_width = (width_spec & ~MEASURE_SPEC_MASK) - padding_h;
			if (max_width < 0)
				max_width = 0;
		}
		records_text_extent(view, max_width, &content_width, &content_height);
	} else if (view->kind == NODE_IMAGE) {
		content_width = view->image_width > 0 ? view->image_width : 0;
		content_height = view->image_height > 0 ? view->image_height : 0;
	} else if (view->kind == NODE_PROGRESS) {
		// Widget.ProgressBar's 48dp; a bar 20dp tall, as Widget.ProgressBar.Horizontal
		int dp = atl_window_get()->density_dpi;
		content_width = 48 * dp / 160;
		content_height = (view->indeterminate ? 48 : 20) * dp / 160;
	}
	*width = resolve(content_width + padding_h, width_spec);
	*height = resolve(content_height + padding_v, height_spec);
}

void ui_view_layout(struct ui_view *view, int left, int top, int right, int bottom, float translation_x, float translation_y)
{
	view->left = left + (int)translation_x;
	view->top = top + (int)translation_y;
	view->right = right + (int)translation_x;
	view->bottom = bottom + (int)translation_y;
	if (view->kind == NODE_VIEW || view->kind == NODE_SCROLL)
		atl_upcall_view_layout(view->ref, right - left, bottom - top);
}

struct ui_view *ui_text_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits, const char *text)
{
	struct ui_view *view = created(view_new(NODE_TEXT, view_ref, class_name, traits));
	if (text)
		ui_text_set_text(view, text);
	return view;
}

void ui_text_set_text(struct ui_view *view, const char *text)
{
	if (view->edit)
		return;
	free(view->text);
	view->text = strdup(text);
	if (view->kind == NODE_TEXT)
		records_text_changed(view);
}

void ui_text_set_color(struct ui_view *view, uint32_t argb)
{
	if (view->kind == NODE_TEXT)
		records_text_color_changed(view, argb);
}

void ui_text_set_size(struct ui_view *view, float size)
{
	view->text_size = size;
	if (view->kind == NODE_TEXT)
		records_text_changed(view);
}

void ui_text_set_style(struct ui_view *view, int style)
{
	view->text_style = style;
	if (view->kind == NODE_TEXT)
		records_text_changed(view);
}

void ui_text_set_markup(struct ui_view *view, bool markup)
{
	if (!view->edit)
		view->markup = markup;
}

void ui_text_set_alignment(struct ui_view *view, int alignment)
{
	view->alignment = alignment;
	if (view->kind == NODE_TEXT)
		records_text_changed(view);
}

void ui_text_set_selectable(struct ui_view *view, bool selectable)
{
	if (view->kind != NODE_TEXT || view->is_button || view->edit || view->selectable == selectable)
		return;
	view->selectable = selectable;
	records_selectable_changed(view);
}

// Java holds the drawables; nothing draws them before M5
void ui_text_set_drawables(struct ui_view *view, struct gfx_drawable *left, struct gfx_drawable *top, struct gfx_drawable *right, struct gfx_drawable *bottom) {}

struct ui_view *ui_button_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits, enum ui_button kind)
{
	struct ui_view *view = view_new(NODE_TEXT, view_ref, class_name, traits);
	view->is_button = true;
	view->button = kind;
	return created(view);
}

void ui_button_set_state(struct ui_view *view, bool pressed, bool checked)
{
	if (view->pressed == pressed && view->checked == checked)
		return;
	view->pressed = pressed;
	view->checked = checked;
	records_button_changed(view);
}

struct ui_view *ui_image_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits)
{
	struct ui_view *view = view_new(NODE_IMAGE, view_ref, class_name, traits);
	view->image_width = view->image_height = -1;
	return created(view);
}

void ui_image_set_drawable(struct ui_view *view, struct gfx_drawable *drawable, int width, int height)
{
	hold(&view->image, drawable);
	view->image_width = width;
	view->image_height = height;
	ui_view_request_layout(view);
}

void ui_image_set_scale_type(struct ui_view *view, int scale_type)
{
	view->scale_type = scale_type;
}

struct ui_view *ui_progress_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits)
{
	return created(view_new(NODE_PROGRESS, view_ref, class_name, traits));
}

void ui_progress_set_fraction(struct ui_view *view, float fraction)
{
	view->fraction = fraction;
	records_progress_changed(view);
}

void ui_progress_set_indeterminate(struct ui_view *view, bool indeterminate)
{
	view->indeterminate = indeterminate;
	records_progress_changed(view);
}

struct ui_view *ui_edit_new(struct atl_ref *view_ref, const char *class_name, const struct ui_view_traits *traits)
{
	struct ui_view *view = view_new(NODE_TEXT, view_ref, class_name, traits);
	view->edit = true;
	return created(view);
}

void ui_edit_set_config(struct ui_view *view, const struct ui_edit_config *config)
{
	free((char *)view->edit_config.hint);
	view->edit_config = *config;
	view->edit_config.hint = config->hint ? strdup(config->hint) : NULL;
	records_edit_config_changed(view);
	ui_view_request_layout(view); // the hint and the line count measure
}

void records_edit_set(struct ui_view *view, const char *text, int start, int end)
{
	if (strcmp(view->text, text)) {
		free(view->text);
		view->text = strdup(text);
		ui_view_request_layout(view);
	}
	view->sel_start = start;
	view->sel_end = end;
}

void ui_edit_set_state(struct ui_view *view, const char *text, int start, int end, uint32_t ack)
{
	records_edit_state_changed(view, text, start, end, ack);
}

bool ui_edit_set_keyboard(struct ui_view *view, bool shown)
{
	return records_edit_keyboard(view, shown);
}

void records_edit_report(struct ui_view *view, const struct atl_edit_report *report)
{
	if (!view->ref) // freed; its record waits for this post to pass
		return;
	records_edit_set(view, report->text, report->start, report->end);
	atl_upcall_edit_report(view->ref, report);
}
