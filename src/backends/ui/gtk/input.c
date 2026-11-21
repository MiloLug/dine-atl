#include <gtk/gtk.h>

#include "src/api-impl-jni/widgets/WrapperWidget.h"
#include "src/shared/input.h"
#include "src/shared/keys.h"
#include "src/shared/pointers.h"

#include "gtk_ui.h"

/* Each GtkWindow's touches and keys go to the root view it shows, whose Java dispatches them
 * (ATLInput): capture controllers see them before any widget. Popovers are widgets of a window, so
 * their events pass its controllers too. GTK keeps a gesture that starts on a widget marked native,
 * and a key while a text widget has GTK's focus until that widget leaves it. */

#define NATIVE_INPUT "atl-native-input"

// one gesture at a time, across windows
static struct {
	WrapperWidget *root; // held while the gesture lasts
	GtkNative *native;   // its events' surface
	bool native_input;   // GTK keeps it
	bool claimed;        // Java took the first DOWN: GTK doesn't see the gesture
	struct atl_pointers pointers; // keyed by the event sequence; NULL: the mouse
	int delivering;               // events on their way to Java: hover exits wait for them
} gesture;

void input_take_native(GtkWidget *widget)
{
	g_object_set_data(G_OBJECT(widget), NATIVE_INPUT, GINT_TO_POINTER(TRUE));
}

void input_return_native(GtkWidget *widget)
{
	g_object_set_data(G_OBJECT(widget), NATIVE_INPUT, NULL);
}

// the tracker is empty before the last event is delivered, but the pointer is up only once Java has it
bool input_pointers_up(void)
{
	return gesture.pointers.touch.count == 0 && !gesture.delivering;
}

// the root view a native shows: the app window's shown activity, or a window's content
static WrapperWidget *root_wrapper(GtkNative *native)
{
	GtkWidget *child = NULL;
	if (GTK_IS_WINDOW(native)) {
		child = gtk_window_get_child(GTK_WINDOW(native));
		if (GTK_IS_OVERLAY(child))
			child = gtk_overlay_get_child(GTK_OVERLAY(child));
		if (GTK_IS_STACK(child))
			child = gtk_stack_get_visible_child(GTK_STACK(child));
	}
	return child && WRAPPER_IS_WIDGET(child) ? WRAPPER_WIDGET(child) : NULL;
}

// the root a view is in: an activity's, a window's over it, or another native's
static WrapperWidget *view_root(GtkWidget *view)
{
	for (GtkWidget *widget = view; widget; widget = gtk_widget_get_parent(widget)) {
		GtkWidget *parent = gtk_widget_get_parent(widget);
		if (WRAPPER_IS_WIDGET(widget) && (GTK_IS_STACK(parent) || GTK_IS_OVERLAY(parent) || GTK_IS_NATIVE(parent)))
			return WRAPPER_WIDGET(widget);
	}
	return NULL;
}

// the root the DOWN at x, y (the native's) goes to; NULL: none, or the header bar's
static WrapperWidget *down_root(GtkNative *native, double x, double y)
{
	GtkWidget *content = GTK_IS_WINDOW(native) ? gtk_window_get_child(GTK_WINDOW(native)) : NULL;
	if (content && GTK_IS_OVERLAY(content)) { // the app window: its content, then the windows over the activity
		graphene_point_t point;
		if (!gtk_widget_compute_point(GTK_WIDGET(native), content, &GRAPHENE_POINT_INIT(x, y), &point)
		    || point.x < 0 || point.y < 0 || point.x >= gtk_widget_get_width(content) || point.y >= gtk_widget_get_height(content))
			return NULL;
		struct atl_touch down = {.action = ATL_ACTION_DOWN, .down_time = atl_uptime_ms(), .count = 1};
		down.time = down.down_time;
		down.pointers[0] = (struct atl_pointer){.x = point.x, .y = point.y};
		window_touch_outside(GTK_WINDOW(native), &down);
		GtkWidget *root = window_touch_root(GTK_WINDOW(native), point.x, point.y);
		return root && WRAPPER_IS_WIDGET(root) ? WRAPPER_WIDGET(root) : NULL;
	}
	WrapperWidget *root = root_wrapper(native);
	graphene_point_t point;
	if (!root || !gtk_widget_compute_point(GTK_WIDGET(native), GTK_WIDGET(root), &GRAPHENE_POINT_INIT(x, y), &point)
	    || point.x < 0 || point.y < 0 || point.x >= gtk_widget_get_width(GTK_WIDGET(root)) || point.y >= gtk_widget_get_height(GTK_WIDGET(root)))
		return NULL;
	return root;
}

// the event's position in the native's widget, px
static void native_point(GdkEvent *event, GtkNative *native, double *x, double *y)
{
	double off_x, off_y;
	gdk_event_get_position(event, x, y);
	gtk_native_get_surface_transform(native, &off_x, &off_y);
	*x -= off_x;
	*y -= off_y;
}

static bool picks_native_input(GtkNative *native, double x, double y)
{
	for (GtkWidget *widget = gtk_widget_pick(GTK_WIDGET(native), x, y, GTK_PICK_DEFAULT); widget; widget = gtk_widget_get_parent(widget)) {
		if (g_object_get_data(G_OBJECT(widget), NATIVE_INPUT))
			return true;
	}
	return false;
}

// the position in the gesture's root of x, y in its native, px; false when the two don't relate
static bool root_point(double x, double y, float *root_x, float *root_y)
{
	graphene_point_t point;
	if (!gtk_widget_compute_point(GTK_WIDGET(gesture.native), GTK_WIDGET(gesture.root), &GRAPHENE_POINT_INIT(x, y), &point))
		return false;
	*root_x = point.x;
	*root_y = point.y;
	return true;
}

// The gesture's root and native go; the caller holds the root to deliver the last event, then lets
// the hover exits out.
static void end_gesture(void)
{
	g_clear_object(&gesture.root);
	gesture.native = NULL;
}

/* The tracker is current before an event reaches Java, which may end the gesture meanwhile (a
 * window closing from its own UP: input_root_gone): delivery uses a copy of the event and a held
 * root, and no pointer state is read afterwards. The hover exits a gesture's end held back go out
 * when the last delivery of it returns, also when Java ended it during a DOWN or a MOVE. */
static bool deliver(WrapperWidget *root, const struct atl_touch *event)
{
	gesture.delivering++;
	bool handled = atl_upcall_touch(wrapper_widget_ref(root), event);
	gesture.delivering--;
	if (!gesture.delivering && !gesture.pointers.touch.count)
		view_flush_hover_exits();
	return handled;
}

// a new DOWN starts over: Android cancels what was down
static void cancel_gesture(void)
{
	struct atl_touch event;
	if (!atl_pointers_cancel(&gesture.pointers, atl_uptime_ms(), &event))
		return;
	WrapperWidget *root = g_object_ref(gesture.root);
	bool native_input = gesture.native_input;
	end_gesture();
	if (native_input)
		view_flush_hover_exits();
	else
		deliver(root, &event);
	g_object_unref(root);
}

static gboolean pointer_down(GtkNative *native, GdkEventSequence *sequence, double x, double y)
{
	if (gesture.pointers.touch.count && (native != gesture.native || atl_pointers_find(&gesture.pointers, sequence) >= 0)) {
		// its surface went before its UP (a closed dialog or popover), or its pointer came down
		// again
		cancel_gesture();
	}
	bool first = !gesture.pointers.touch.count;
	if (first) {
		if (!native)
			return FALSE;
		WrapperWidget *root = down_root(native, x, y);
		if (!root)
			return FALSE;
		gesture.root = g_object_ref(root);
		gesture.native = native;
		gesture.native_input = picks_native_input(native, x, y);
		gesture.claimed = false;
	} else if (gesture.pointers.touch.count == ATL_POINTERS_MAX) {
		return gesture.claimed;
	}
	float root_x, root_y;
	struct atl_touch event;
	if (!root_point(x, y, &root_x, &root_y) || !atl_pointers_down(&gesture.pointers, sequence, root_x, root_y, atl_uptime_ms(), &event)) {
		if (first)
			end_gesture();
		return gesture.claimed;
	}
	if (gesture.native_input)
		return FALSE;
	WrapperWidget *root = g_object_ref(gesture.root);
	bool handled = deliver(root, &event);
	g_object_unref(root);
	if (first)
		gesture.claimed = handled;
	return gesture.claimed;
}

void input_root_gone(GtkWidget *root)
{
	if (!gesture.pointers.touch.count || GTK_WIDGET(gesture.root) != root)
		return;
	struct atl_touch event;
	atl_pointers_cancel(&gesture.pointers, atl_uptime_ms(), &event);
	// posted: the window may be closing from inside the touch being delivered
	if (!gesture.native_input)
		atl_upcall_touch_posted(host_app, wrapper_widget_ref(gesture.root), &event);
	end_gesture();
	if (!gesture.delivering) // else the delivery's return lets them out
		view_flush_hover_exits();
}

bool input_tap(GtkWidget *view)
{
	WrapperWidget *root = view_root(view);
	GtkNative *native = gtk_widget_get_native(view);
	if (!root || !native || gesture.pointers.touch.count)
		return false;
	// wrappers laid out from Java get allocated 0x0 and keep their size in real_width and real_height
	WrapperWidget *wrapper = WRAPPER_WIDGET(view);
	float width = wrapper->real_width ?: gtk_widget_get_width(view);
	float height = wrapper->real_height ?: gtk_widget_get_height(view);
	graphene_point_t point;
	if (!gtk_widget_compute_point(view, GTK_WIDGET(root), &GRAPHENE_POINT_INIT(width / 2, height / 2), &point))
		return false;
	if (GTK_IS_ROOT(native))
		edit_flush_report(gtk_root_get_focus(GTK_ROOT(native)));
	// a gesture of its own on the view's root, with a made-up handle (this tap's address)
	static const char handle;
	gesture.root = g_object_ref(root);
	gesture.native = native;
	gesture.native_input = false;
	gesture.claimed = false;
	struct atl_touch event;
	atl_pointers_down(&gesture.pointers, &handle, point.x, point.y, atl_uptime_ms(), &event);
	WrapperWidget *held = g_object_ref(root);
	bool claimed = deliver(held, &event);
	// Java may have ended the gesture meanwhile (its root left)
	if (atl_pointers_up(&gesture.pointers, &handle, point.x, point.y, atl_uptime_ms(), &event)) {
		end_gesture();
		deliver(held, &event);
	}
	g_object_unref(held);
	return claimed;
}

gboolean input_pointer(enum input_pointer what, GtkNative *native, GdkEventSequence *sequence, double x, double y)
{
	if (GTK_IS_ROOT(native))
		edit_flush_report(gtk_root_get_focus(GTK_ROOT(native)));
	if (what == INPUT_POINTER_DOWN)
		return pointer_down(native, sequence, x, y);

	int index = atl_pointers_find(&gesture.pointers, sequence);
	if (index < 0 || native != gesture.native)
		return FALSE;
	// the pointer stays where it was when the widgets don't relate
	float root_x = gesture.pointers.touch.pointers[index].x, root_y = gesture.pointers.touch.pointers[index].y;
	root_point(x, y, &root_x, &root_y);
	struct atl_touch event;
	int64_t now = atl_uptime_ms();
	switch (what) {
		case INPUT_POINTER_MOVE:
			atl_pointers_move(&gesture.pointers, sequence, root_x, root_y, now, &event);
			break;
		case INPUT_POINTER_UP:
			atl_pointers_up(&gesture.pointers, sequence, root_x, root_y, now, &event);
			break;
		default: // Android cancels the whole gesture
			atl_pointers_cancel(&gesture.pointers, now, &event);
			break;
	}
	gboolean claimed = gesture.claimed && !gesture.native_input;
	bool native_input = gesture.native_input;
	WrapperWidget *root = g_object_ref(gesture.root);
	bool ended = !gesture.pointers.touch.count;
	if (ended)
		end_gesture();
	if (native_input) {
		if (ended)
			view_flush_hover_exits();
	} else {
		deliver(root, &event);
	}
	g_object_unref(root);
	return claimed;
}

// the GdkEvent's part: what it is, which pointer, where in its native
static gboolean on_pointer_event(GtkEventControllerLegacy *controller, GdkEvent *event, gpointer data)
{
	enum input_pointer what;
	switch (gdk_event_get_event_type(event)) {
		case GDK_BUTTON_PRESS:
			if (gdk_button_event_get_button(event) != GDK_BUTTON_PRIMARY)
				return FALSE;
			// fallthrough
		case GDK_TOUCH_BEGIN:
			what = INPUT_POINTER_DOWN;
			break;
		case GDK_MOTION_NOTIFY:
			if (!(gdk_event_get_modifier_state(event) & GDK_BUTTON1_MASK))
				return FALSE;
			// fallthrough
		case GDK_TOUCH_UPDATE:
			what = INPUT_POINTER_MOVE;
			break;
		case GDK_BUTTON_RELEASE:
			if (gdk_button_event_get_button(event) != GDK_BUTTON_PRIMARY)
				return FALSE;
			// fallthrough
		case GDK_TOUCH_END:
			what = INPUT_POINTER_UP;
			break;
		case GDK_TOUCH_CANCEL:
			what = INPUT_POINTER_CANCEL;
			break;
		default:
			return FALSE;
	}
	GtkNative *native = gtk_native_get_for_surface(gdk_event_get_surface(event));
	if (!native)
		return FALSE;
	double x, y;
	native_point(event, native, &x, &y);
	return input_pointer(what, native, gdk_event_get_event_sequence(event), x, y);
}

/* Android's code of the hardware key: the keyval it has without modifiers in the first layout, so a
 * press and its release agree whatever the modifiers do to the event's keyval */
static int map_key_code(GtkWindow *window, guint keycode)
{
	guint keyval;
	if (!gdk_display_translate_key(gtk_widget_get_display(GTK_WIDGET(window)), keycode, 0, 0, &keyval, NULL, NULL, NULL))
		return ATL_KEYCODE_UNKNOWN;
	if (keyval >= GDK_KEY_0 && keyval <= GDK_KEY_9)
		return keyval - GDK_KEY_0 + ATL_KEYCODE_0;
	if (keyval >= GDK_KEY_a && keyval <= GDK_KEY_z)
		return keyval - GDK_KEY_a + ATL_KEYCODE_A;
	if (keyval >= GDK_KEY_F1 && keyval <= GDK_KEY_F12)
		return keyval - GDK_KEY_F1 + ATL_KEYCODE_F1;
	if (keyval >= GDK_KEY_KP_0 && keyval <= GDK_KEY_KP_9)
		return keyval - GDK_KEY_KP_0 + ATL_KEYCODE_NUMPAD_0;
	switch (keyval) {
		case GDK_KEY_Up:
			return ATL_KEYCODE_DPAD_UP;
		case GDK_KEY_Down:
			return ATL_KEYCODE_DPAD_DOWN;
		case GDK_KEY_Left:
			return ATL_KEYCODE_DPAD_LEFT;
		case GDK_KEY_Right:
			return ATL_KEYCODE_DPAD_RIGHT;
		case GDK_KEY_comma:
			return ATL_KEYCODE_COMMA;
		case GDK_KEY_period:
			return ATL_KEYCODE_PERIOD;
		case GDK_KEY_Tab:
			return ATL_KEYCODE_TAB;
		case GDK_KEY_space:
			return ATL_KEYCODE_SPACE;
		case GDK_KEY_Return:
		case GDK_KEY_ISO_Enter:
			return ATL_KEYCODE_ENTER;
		case GDK_KEY_BackSpace:
			return ATL_KEYCODE_DEL;
		case GDK_KEY_grave:
			return ATL_KEYCODE_GRAVE;
		case GDK_KEY_minus:
			return ATL_KEYCODE_MINUS;
		case GDK_KEY_equal:
			return ATL_KEYCODE_EQUALS;
		case GDK_KEY_bracketleft:
			return ATL_KEYCODE_LEFT_BRACKET;
		case GDK_KEY_bracketright:
			return ATL_KEYCODE_RIGHT_BRACKET;
		case GDK_KEY_backslash:
			return ATL_KEYCODE_BACKSLASH;
		case GDK_KEY_semicolon:
			return ATL_KEYCODE_SEMICOLON;
		case GDK_KEY_apostrophe:
			return ATL_KEYCODE_APOSTROPHE;
		case GDK_KEY_slash:
			return ATL_KEYCODE_SLASH;
		case GDK_KEY_at:
			return ATL_KEYCODE_AT;
		case GDK_KEY_plus:
			return ATL_KEYCODE_PLUS;
		case GDK_KEY_Page_Up:
			return ATL_KEYCODE_PAGE_UP;
		case GDK_KEY_Page_Down:
			return ATL_KEYCODE_PAGE_DOWN;
		case GDK_KEY_Escape:
			return ATL_KEYCODE_ESCAPE;
		case GDK_KEY_Delete:
			return ATL_KEYCODE_FORWARD_DEL;
		case GDK_KEY_Home:
			return ATL_KEYCODE_MOVE_HOME;
		case GDK_KEY_End:
			return ATL_KEYCODE_MOVE_END;
		case GDK_KEY_Insert:
			return ATL_KEYCODE_INSERT;
		case GDK_KEY_Shift_L:
			return ATL_KEYCODE_SHIFT_LEFT;
		case GDK_KEY_Shift_R:
			return ATL_KEYCODE_SHIFT_RIGHT;
		case GDK_KEY_Control_L:
			return ATL_KEYCODE_CTRL_LEFT;
		case GDK_KEY_Control_R:
			return ATL_KEYCODE_CTRL_RIGHT;
		case GDK_KEY_Alt_L:
			return ATL_KEYCODE_ALT_LEFT;
		case GDK_KEY_Alt_R:
		case GDK_KEY_ISO_Level3_Shift:
			return ATL_KEYCODE_ALT_RIGHT;
		case GDK_KEY_Super_L:
		case GDK_KEY_Meta_L:
			return ATL_KEYCODE_META_LEFT;
		case GDK_KEY_Super_R:
		case GDK_KEY_Meta_R:
			return ATL_KEYCODE_META_RIGHT;
		case GDK_KEY_Caps_Lock:
			return ATL_KEYCODE_CAPS_LOCK;
		// the keypad: its keys without NumLock are the same NUMPAD codes
		case GDK_KEY_KP_Insert:
			return ATL_KEYCODE_NUMPAD_0;
		case GDK_KEY_KP_End:
			return ATL_KEYCODE_NUMPAD_0 + 1;
		case GDK_KEY_KP_Down:
			return ATL_KEYCODE_NUMPAD_0 + 2;
		case GDK_KEY_KP_Page_Down:
			return ATL_KEYCODE_NUMPAD_0 + 3;
		case GDK_KEY_KP_Left:
			return ATL_KEYCODE_NUMPAD_0 + 4;
		case GDK_KEY_KP_Begin:
			return ATL_KEYCODE_NUMPAD_0 + 5;
		case GDK_KEY_KP_Right:
			return ATL_KEYCODE_NUMPAD_0 + 6;
		case GDK_KEY_KP_Home:
			return ATL_KEYCODE_NUMPAD_0 + 7;
		case GDK_KEY_KP_Up:
			return ATL_KEYCODE_NUMPAD_0 + 8;
		case GDK_KEY_KP_Page_Up:
			return ATL_KEYCODE_NUMPAD_0 + 9;
		case GDK_KEY_KP_Delete:
		case GDK_KEY_KP_Decimal:
			return ATL_KEYCODE_NUMPAD_DOT;
		case GDK_KEY_KP_Divide:
			return ATL_KEYCODE_NUMPAD_DIVIDE;
		case GDK_KEY_KP_Multiply:
			return ATL_KEYCODE_NUMPAD_MULTIPLY;
		case GDK_KEY_KP_Subtract:
			return ATL_KEYCODE_NUMPAD_SUBTRACT;
		case GDK_KEY_KP_Add:
			return ATL_KEYCODE_NUMPAD_ADD;
		case GDK_KEY_KP_Enter:
			return ATL_KEYCODE_NUMPAD_ENTER;
		default:
			return ATL_KEYCODE_UNKNOWN;
	}
}

static int map_meta_state(GdkModifierType state)
{
	int meta_state = 0;
	if (state & GDK_SHIFT_MASK)
		meta_state |= ATL_META_SHIFT_ON;
	if (state & GDK_CONTROL_MASK)
		meta_state |= ATL_META_CTRL_ON;
	if (state & GDK_META_MASK)
		meta_state |= ATL_META_META_ON;
	if (state & GDK_ALT_MASK)
		meta_state |= ATL_META_ALT_ON;
	if (state & GDK_LOCK_MASK)
		meta_state |= ATL_META_CAPS_LOCK_ON;
	return meta_state;
}


/* Keys by hardware keycode: a release's keyval follows the modifiers at release (Shift+a goes down
 * as A, up as a). The key held down, for repeats and down times. */
static guint held_keycode;
static int held_repeat;
static int64_t held_down_time;

/* The keys whose press went to Java. GTK passes on the release of a press a text widget took, also
 * when Java moved focus out of it meanwhile (a watcher after a pin's last digit): a release goes to
 * Java only after its press. A window going inactive gets no releases: the table empties then. */
#define JAVA_KEYS_MAX 16
static guint java_keys[JAVA_KEYS_MAX];
static int n_java_keys;

static void java_key_pressed(guint keycode)
{
	for (int i = 0; i < n_java_keys; i++) {
		if (java_keys[i] == keycode)
			return;
	}
	if (n_java_keys < JAVA_KEYS_MAX)
		java_keys[n_java_keys++] = keycode;
}

static bool java_key_released(guint keycode)
{
	for (int i = 0; i < n_java_keys; i++) {
		if (java_keys[i] == keycode) {
			java_keys[i] = java_keys[--n_java_keys];
			return true;
		}
	}
	return false;
}

static void on_active_changed(GtkWindow *window, GParamSpec *pspec, gpointer data)
{
	if (!gtk_window_is_active(window)) {
		n_java_keys = 0;
		held_keycode = 0;
	}
}

gboolean input_key(GtkWindow *window, GtkPropagationPhase phase, guint keyval, guint keycode, GdkModifierType state, int action)
{
	GtkWidget *focus = gtk_root_get_focus(GTK_ROOT(window));
	bool text_focus = focus && (GTK_IS_EDITABLE(focus) || (GTK_IS_LABEL(focus) && gtk_label_get_selectable(GTK_LABEL(focus))));
	// an EditText takes a hardware Enter first, as Android's does; the GtkText gets what it leaves,
	// and an open preedit's Enter, which commits it
	if (text_focus && g_object_get_data(G_OBJECT(focus), "atl-edit") && !g_object_get_data(G_OBJECT(focus), "atl-preedit")
	    && (keyval == GDK_KEY_Return || keyval == GDK_KEY_KP_Enter || keyval == GDK_KEY_ISO_Enter))
		text_focus = false;
	// the capture controller leaves a text widget's keys to it; the bubble one takes what it left
	if ((phase == GTK_PHASE_CAPTURE) == text_focus)
		return FALSE;
	WrapperWidget *root = root_wrapper(GTK_NATIVE(window));
	if (!root)
		return FALSE;
	edit_flush_report(focus);
	if (action == ATL_KEY_DOWN)
		java_key_pressed(keycode);
	else if (!java_key_released(keycode))
		return FALSE;

	int64_t now = atl_uptime_ms();
	if (action == ATL_KEY_DOWN) {
		held_repeat = keycode == held_keycode ? held_repeat + 1 : 0;
		if (!held_repeat)
			held_down_time = now;
		held_keycode = keycode;
	} else if (keycode == held_keycode) {
		held_keycode = 0;
	}
	struct atl_key key = {
		.action = action,
		.code = map_key_code(window, keycode),
		.meta = map_meta_state(state),
		.unicode = gdk_keyval_to_unicode(keyval),
		.repeat = action == ATL_KEY_DOWN ? held_repeat : 0,
		.down_time = held_down_time,
		.time = now,
	};
	return atl_upcall_key(wrapper_widget_ref(root), &key);
}

// the GdkEvent's part: which window and phase the controller is for
static gboolean on_key(GtkEventControllerKey *controller, guint keyval, guint keycode, GdkModifierType state, int action)
{
	GtkWidget *window = gtk_event_controller_get_widget(GTK_EVENT_CONTROLLER(controller));
	return input_key(GTK_WINDOW(window), gtk_event_controller_get_propagation_phase(GTK_EVENT_CONTROLLER(controller)), keyval, keycode, state, action);
}

static gboolean on_key_pressed(GtkEventControllerKey *controller, guint keyval, guint keycode, GdkModifierType state, gpointer data)
{
	return on_key(controller, keyval, keycode, state, ATL_KEY_DOWN);
}

static void on_key_released(GtkEventControllerKey *controller, guint keyval, guint keycode, GdkModifierType state, gpointer data)
{
	on_key(controller, keyval, keycode, state, ATL_KEY_UP);
}

static void add_key_controller(GtkWidget *window, GtkPropagationPhase phase)
{
	GtkEventController *controller = gtk_event_controller_key_new();
	gtk_event_controller_set_propagation_phase(controller, phase);
	g_signal_connect(controller, "key-pressed", G_CALLBACK(on_key_pressed), NULL);
	g_signal_connect(controller, "key-released", G_CALLBACK(on_key_released), NULL);
	gtk_widget_add_controller(window, controller);
}

void input_attach_window(GtkWindow *window)
{
	if (g_object_get_data(G_OBJECT(window), "atl-input"))
		return;
	g_object_set_data(G_OBJECT(window), "atl-input", GINT_TO_POINTER(TRUE));

	GtkEventController *touch = gtk_event_controller_legacy_new();
	gtk_event_controller_set_propagation_phase(touch, GTK_PHASE_CAPTURE);
	g_signal_connect(touch, "event", G_CALLBACK(on_pointer_event), NULL);
	gtk_widget_add_controller(GTK_WIDGET(window), touch);

	add_key_controller(GTK_WIDGET(window), GTK_PHASE_CAPTURE);
	add_key_controller(GTK_WIDGET(window), GTK_PHASE_BUBBLE);
	g_signal_connect(window, "notify::is-active", G_CALLBACK(on_active_changed), NULL);
}
