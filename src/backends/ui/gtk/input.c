#include <gtk/gtk.h>

#include "src/api-impl-jni/widgets/WrapperWidget.h"
#include "src/shared/input.h"

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
	struct atl_touch touch;
	GdkEventSequence *sequences[ATL_POINTERS_MAX]; // per pointer index; NULL: the mouse
} gesture;

void input_take_native(GtkWidget *widget)
{
	g_object_set_data(G_OBJECT(widget), NATIVE_INPUT, GINT_TO_POINTER(TRUE));
}

bool input_pointers_up(void)
{
	return gesture.touch.count == 0;
}

// the root view a native shows: the app window's shown activity, a dialog's or a popover's content
static WrapperWidget *root_wrapper(GtkNative *native)
{
	GtkWidget *child = NULL;
	if (GTK_IS_POPOVER(native)) {
		child = gtk_popover_get_child(GTK_POPOVER(native));
	} else if (GTK_IS_WINDOW(native)) {
		child = gtk_window_get_child(GTK_WINDOW(native));
		if (GTK_IS_STACK(child))
			child = gtk_stack_get_visible_child(GTK_STACK(child));
	}
	return child && WRAPPER_IS_WIDGET(child) ? WRAPPER_WIDGET(child) : NULL;
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

static bool set_pointer(int index, GdkEvent *event)
{
	double x, y;
	native_point(event, gesture.native, &x, &y);
	graphene_point_t point;
	if (!gtk_widget_compute_point(GTK_WIDGET(gesture.native), GTK_WIDGET(gesture.root), &GRAPHENE_POINT_INIT(x, y), &point))
		return false;
	gesture.touch.pointers[index].x = point.x;
	gesture.touch.pointers[index].y = point.y;
	return true;
}

static int pointer_index(GdkEventSequence *sequence)
{
	for (int i = 0; i < gesture.touch.count; i++) {
		if (gesture.sequences[i] == sequence)
			return i;
	}
	return -1;
}

// Android's: the lowest id no pointer has
static int free_pointer_id(void)
{
	for (int id = 0;; id++) {
		bool taken = false;
		for (int i = 0; i < gesture.touch.count; i++)
			taken |= gesture.touch.pointers[i].id == id;
		if (!taken)
			return id;
	}
}

static bool deliver(int action)
{
	gesture.touch.action = action;
	gesture.touch.time = atl_uptime_ms();
	return atl_upcall_touch((struct atl_ref *)gesture.root->jobj, &gesture.touch);
}

static void end_gesture(void)
{
	gesture.touch.count = 0;
	g_clear_object(&gesture.root);
	gesture.native = NULL;
	view_flush_hover_exits();
}

static void cancel_gesture(void)
{
	if (!gesture.native_input)
		deliver(ATL_ACTION_CANCEL);
	end_gesture();
}

static gboolean pointer_down(GdkEvent *event, GtkNative *native, GdkEventSequence *sequence)
{
	if (!gesture.touch.count) {
		WrapperWidget *root = native ? root_wrapper(native) : NULL;
		if (!root)
			return FALSE;
		double x, y;
		native_point(event, native, &x, &y);
		graphene_point_t point;
		if (!gtk_widget_compute_point(GTK_WIDGET(native), GTK_WIDGET(root), &GRAPHENE_POINT_INIT(x, y), &point)
		    || point.x < 0 || point.y < 0 || point.x >= gtk_widget_get_width(GTK_WIDGET(root)) || point.y >= gtk_widget_get_height(GTK_WIDGET(root)))
			return FALSE; // the header bar's
		gesture.root = g_object_ref(root);
		gesture.native = native;
		gesture.native_input = picks_native_input(native, x, y);
		gesture.claimed = false;
		gesture.touch.down_time = atl_uptime_ms();
	} else if (native != gesture.native || pointer_index(sequence) >= 0) {
		// its surface went before its UP (a closed dialog or popover), or its pointer came down
		// again: Android starts over at a DOWN
		cancel_gesture();
		return pointer_down(event, native, sequence);
	} else if (gesture.touch.count == ATL_POINTERS_MAX) {
		return gesture.claimed;
	}
	int index = gesture.touch.count++;
	gesture.sequences[index] = sequence;
	gesture.touch.pointers[index].id = free_pointer_id();
	set_pointer(index, event);
	if (gesture.native_input)
		return FALSE;
	bool handled = deliver(index ? ATL_ACTION_POINTER_DOWN | index << ATL_ACTION_INDEX_SHIFT : ATL_ACTION_DOWN);
	if (!index)
		gesture.claimed = handled;
	return gesture.claimed;
}

static gboolean on_pointer_event(GtkEventControllerLegacy *controller, GdkEvent *event, gpointer data)
{
	int action;
	switch (gdk_event_get_event_type(event)) {
		case GDK_BUTTON_PRESS:
			if (gdk_button_event_get_button(event) != GDK_BUTTON_PRIMARY)
				return FALSE;
			// fallthrough
		case GDK_TOUCH_BEGIN:
			action = ATL_ACTION_DOWN;
			break;
		case GDK_MOTION_NOTIFY:
			if (!(gdk_event_get_modifier_state(event) & GDK_BUTTON1_MASK))
				return FALSE;
			// fallthrough
		case GDK_TOUCH_UPDATE:
			action = ATL_ACTION_MOVE;
			break;
		case GDK_BUTTON_RELEASE:
			if (gdk_button_event_get_button(event) != GDK_BUTTON_PRIMARY)
				return FALSE;
			// fallthrough
		case GDK_TOUCH_END:
			action = ATL_ACTION_UP;
			break;
		case GDK_TOUCH_CANCEL:
			action = ATL_ACTION_CANCEL;
			break;
		default:
			return FALSE;
	}
	GdkEventSequence *sequence = gdk_event_get_event_sequence(event);
	GtkNative *native = gtk_native_get_for_surface(gdk_event_get_surface(event));
	if (action == ATL_ACTION_DOWN)
		return pointer_down(event, native, sequence);

	int index = pointer_index(sequence);
	if (index < 0 || native != gesture.native)
		return FALSE;
	set_pointer(index, event);
	gboolean claimed = gesture.claimed && !gesture.native_input;
	if (action == ATL_ACTION_UP && gesture.touch.count > 1)
		action = ATL_ACTION_POINTER_UP | index << ATL_ACTION_INDEX_SHIFT;
	if (!gesture.native_input)
		deliver(action);
	if (action == ATL_ACTION_CANCEL) {
		end_gesture(); // Android cancels the whole gesture
	} else if (action != ATL_ACTION_MOVE) {
		memmove(&gesture.touch.pointers[index], &gesture.touch.pointers[index + 1], (gesture.touch.count - index - 1) * sizeof(*gesture.touch.pointers));
		memmove(&gesture.sequences[index], &gesture.sequences[index + 1], (gesture.touch.count - index - 1) * sizeof(*gesture.sequences));
		if (!--gesture.touch.count)
			end_gesture();
	}
	return claimed;
}

#define KEYCODE_0             7 /* KEYCODE_[1-9] = [8-16] */
#define KEYCODE_DPAD_UP       19
#define KEYCODE_DPAD_DOWN     20
#define KEYCODE_DPAD_LEFT     21
#define KEYCODE_DPAD_RIGHT    22
#define KEYCODE_A             29 /* KEYCODE_[B-Z] = [30-54] */
#define KEYCODE_COMMA         55
#define KEYCODE_PERIOD        56
#define KEYCODE_TAB           61
#define KEYCODE_SPACE         62
#define KEYCODE_ENTER         66
#define KEYCODE_DEL           67
#define KEYCODE_GRAVE         68
#define KEYCODE_MINUS         69
#define KEYCODE_EQUALS        70
#define KEYCODE_LEFT_BRACKET  71
#define KEYCODE_RIGHT_BRACKET 72
#define KEYCODE_BACKSLASH     73
#define KEYCODE_SEMICOLON     74
#define KEYCODE_APOSTROPHE    75
#define KEYCODE_SLASH         76
#define KEYCODE_AT            77
#define KEYCODE_PLUS          81
#define KEYCODE_PAGE_UP       92
#define KEYCODE_PAGE_DOWN     93
#define KEYCODE_ESCAPE        111
#define KEYCODE_FORWARD_DEL   112
#define KEYCODE_MOVE_HOME     122
#define KEYCODE_MOVE_END      123
#define KEYCODE_INSERT        124
#define KEYCODE_F1            131 /* KEYCODE_F[2-12] = [132-142] */
#define KEYCODE_NUMPAD_0      144 /* KEYCODE_NUMPAD_[1-9] = [145-153] */

static int map_key_code(int key_code)
{
	if (key_code >= GDK_KEY_0 && key_code <= GDK_KEY_9)
		return key_code - GDK_KEY_0 + KEYCODE_0;
	if (key_code >= GDK_KEY_a && key_code <= GDK_KEY_z)
		return key_code - GDK_KEY_a + KEYCODE_A;
	else if (key_code >= GDK_KEY_A && key_code <= GDK_KEY_Z)
		return key_code - GDK_KEY_A + KEYCODE_A;
	else if (key_code >= GDK_KEY_F1 && key_code <= GDK_KEY_F12)
		return key_code - GDK_KEY_F1 + KEYCODE_F1;
	else if (key_code >= GDK_KEY_KP_0 && key_code <= GDK_KEY_KP_9)
		return key_code - GDK_KEY_KP_0 + KEYCODE_NUMPAD_0;
	switch (key_code) {
		case GDK_KEY_Up:
			return KEYCODE_DPAD_UP;
		case GDK_KEY_Down:
			return KEYCODE_DPAD_DOWN;
		case GDK_KEY_Left:
			return KEYCODE_DPAD_LEFT;
		case GDK_KEY_Right:
			return KEYCODE_DPAD_RIGHT;
		case GDK_KEY_comma:
			return KEYCODE_COMMA;
		case GDK_KEY_period:
			return KEYCODE_PERIOD;
		case GDK_KEY_Tab:
			return KEYCODE_TAB;
		case GDK_KEY_space:
			return KEYCODE_SPACE;
		case GDK_KEY_Return:
			return KEYCODE_ENTER;
		case GDK_KEY_BackSpace:
			return KEYCODE_DEL;
		case GDK_KEY_grave:
			return KEYCODE_GRAVE;
		case GDK_KEY_minus:
			return KEYCODE_MINUS;
		case GDK_KEY_equal:
			return KEYCODE_EQUALS;
		case GDK_KEY_bracketleft:
			return KEYCODE_LEFT_BRACKET;
		case GDK_KEY_bracketright:
			return KEYCODE_RIGHT_BRACKET;
		case GDK_KEY_backslash:
			return KEYCODE_BACKSLASH;
		case GDK_KEY_semicolon:
			return KEYCODE_SEMICOLON;
		case GDK_KEY_apostrophe:
			return KEYCODE_APOSTROPHE;
		case GDK_KEY_slash:
			return KEYCODE_SLASH;
		case GDK_KEY_at:
			return KEYCODE_AT;
		case GDK_KEY_plus:
			return KEYCODE_PLUS;
		case GDK_KEY_Page_Up:
			return KEYCODE_PAGE_UP;
		case GDK_KEY_Page_Down:
			return KEYCODE_PAGE_DOWN;
		case GDK_KEY_Escape:
			return KEYCODE_ESCAPE;
		case GDK_KEY_Delete:
			return KEYCODE_FORWARD_DEL;
		case GDK_KEY_Home:
			return KEYCODE_MOVE_HOME;
		case GDK_KEY_End:
			return KEYCODE_MOVE_END;
		case GDK_KEY_Insert:
			return KEYCODE_INSERT;
		default:
			return key_code;
	}
}

#define META_SHIFT_ON     (1 << 0)
#define META_ALT_ON       (1 << 1)
#define META_CTRL_ON      (1 << 12)
#define META_META_ON      (1 << 16)
#define META_CAPS_LOCK_ON (1 << 20)

static int map_meta_state(GdkModifierType state)
{
	int meta_state = 0;
	if (state & GDK_SHIFT_MASK)
		meta_state |= META_SHIFT_ON;
	if (state & GDK_CONTROL_MASK)
		meta_state |= META_CTRL_ON;
	if (state & GDK_META_MASK)
		meta_state |= META_META_ON;
	if (state & GDK_ALT_MASK)
		meta_state |= META_ALT_ON;
	if (state & GDK_LOCK_MASK)
		meta_state |= META_CAPS_LOCK_ON;
	return meta_state;
}


#define ACTION_DOWN 0
#define ACTION_UP   1

// the key held down, for repeats and down times
static guint held_keyval;
static int held_repeat;
static int64_t held_down_time;

static gboolean on_key(GtkEventControllerKey *controller, guint keyval, GdkModifierType state, int action)
{
	GtkWidget *window = gtk_event_controller_get_widget(GTK_EVENT_CONTROLLER(controller));
	GtkWidget *focus = gtk_root_get_focus(GTK_ROOT(window));
	bool text_focus = focus && GTK_IS_EDITABLE(focus);
	// the capture controller leaves a text widget's keys to it; the bubble one takes what it left
	if ((gtk_event_controller_get_propagation_phase(GTK_EVENT_CONTROLLER(controller)) == GTK_PHASE_CAPTURE) == text_focus)
		return FALSE;
	WrapperWidget *root = root_wrapper(GTK_NATIVE(window));
	if (!root)
		return FALSE;

	int64_t now = atl_uptime_ms();
	if (action == ACTION_DOWN) {
		held_repeat = keyval == held_keyval ? held_repeat + 1 : 0;
		if (!held_repeat)
			held_down_time = now;
		held_keyval = keyval;
	} else if (keyval == held_keyval) {
		held_keyval = 0;
	}
	struct atl_key key = {
		.action = action,
		.code = map_key_code(keyval),
		.meta = map_meta_state(state),
		.unicode = gdk_keyval_to_unicode(keyval),
		.repeat = action == ACTION_DOWN ? held_repeat : 0,
		.down_time = held_down_time,
		.time = now,
	};
	return atl_upcall_key((struct atl_ref *)root->jobj, &key);
}

static gboolean on_key_pressed(GtkEventControllerKey *controller, guint keyval, guint keycode, GdkModifierType state, gpointer data)
{
	return on_key(controller, keyval, state, ACTION_DOWN);
}

static void on_key_released(GtkEventControllerKey *controller, guint keyval, guint keycode, GdkModifierType state, gpointer data)
{
	on_key(controller, keyval, state, ACTION_UP);
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
}
