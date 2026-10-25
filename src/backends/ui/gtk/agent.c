#include <errno.h>
#include <gtk/gtk.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include "src/api-impl-jni/widgets/WrapperWidget.h"
#include "src/shared/nav.h"

#include "gtk_ui.h"

/* Debug agent behind `tools/dine dbg ui …` on the gtk build (ATL_DBG_AGENT), with the iOS agent's
 * wire format (native/dev/dbgagent/agent.mm): one request line, one JSON reply line per connection.
 *   ls               {"labels": [[isElement, label], …]}: the shown text of the app window, then of
 *                    the other toplevels; an element is one tap can act on
 *   tap N "LABEL"    acts on element N of the last walk if it still has LABEL: a view gets a tap at
 *                    its centre through Java's dispatch, an EditText GTK's focus, a dialog's own
 *                    button or row its activation; replies before the action runs
 *   type N ["LABEL", "TEXT"]  inserts TEXT at the cursor of element N's EditText, focusing it first;
 *                    also run after the reply
 *   back             Android's Back, also run after the reply
 *   settle MS        waits until the last action ran and the labels hold still, or MS pass (at most
 *                    10 s): {"labels", "settled", "ran", "result": the action's bool}
 * Anything else closes the connection unanswered. */

extern GtkWidget *window; // the gtk host's app window

// main thread only
static GPtrArray *items;  // the last walk's labelled widgets, held
static GString *labels;   // their [isElement, label] pairs, as JSON
static char *before;      // labels when the last action was asked for
static int result = -1;   // that action's result; -1 until it ran

static void json_string(GString *out, const char *s)
{
	g_string_append_c(out, '"');
	for (; *s; s++) {
		if (*s == '"' || *s == '\\')
			g_string_append_printf(out, "\\%c", *s);
		else if ((unsigned char)*s < 0x20)
			g_string_append_printf(out, "\\u%04x", *s);
		else
			g_string_append_c(out, *s);
	}
	g_string_append_c(out, '"');
}

// 4 hex digits at s
static bool hex4(const char *s, unsigned *value)
{
	for (int i = 0; i < 4; i++) {
		if (!g_ascii_isxdigit(s[i]))
			return false;
	}
	*value = g_ascii_xdigit_value(s[0]) << 12 | g_ascii_xdigit_value(s[1]) << 8 | g_ascii_xdigit_value(s[2]) << 4 | g_ascii_xdigit_value(s[3]);
	return true;
}

// a JSON string at *p, as Python's json.dumps writes them (\u escapes, surrogate pairs); NULL if malformed
static char *parse_json_string(const char **p)
{
	const char *s = *p;
	while (*s == ' ')
		s++;
	if (*s++ != '"')
		return NULL;
	GString *out = g_string_new(NULL);
	while (*s && *s != '"') {
		if (*s != '\\') {
			g_string_append_c(out, *s++);
			continue;
		}
		s++;
		const char *plain = strchr("\"\\/bfnrt", *s);
		if (plain && *s) {
			g_string_append_c(out, "\"\\/\b\f\n\r\t"[plain - "\"\\/bfnrt"]);
			s++;
			continue;
		}
		// a NUL would cut the C string, a lone surrogate isn't UTF-8
		unsigned unit, low;
		if (*s != 'u' || !hex4(s + 1, &unit) || !unit || (unit >= 0xdc00 && unit < 0xe000))
			goto bad;
		s += 5;
		if (unit >= 0xd800 && unit < 0xdc00) {
			if (s[0] != '\\' || s[1] != 'u' || !hex4(s + 2, &low) || low < 0xdc00 || low >= 0xe000)
				goto bad;
			unit = 0x10000 + ((unit - 0xd800) << 10) + (low - 0xdc00);
			s += 6;
		}
		g_string_append_unichar(out, unit);
	}
	if (*s != '"')
		goto bad;
	*p = s + 1;
	return g_string_free(out, FALSE);
bad:
	g_string_free(out, TRUE);
	return NULL;
}

static bool on_screen(GtkWidget *widget)
{
	GtkWidget *native = GTK_WIDGET(gtk_widget_get_native(widget));
	graphene_rect_t bounds;
	if (!native || !gtk_widget_compute_bounds(widget, native, &bounds))
		return false;
	return bounds.origin.x < gtk_widget_get_width(native) && bounds.origin.y < gtk_widget_get_height(native)
	       && bounds.origin.x + bounds.size.width > 0 && bounds.origin.y + bounds.size.height > 0;
}

static bool actionable(GtkWidget *widget)
{
	return GTK_IS_TEXT(widget) || gtk_widget_get_ancestor(widget, wrapper_widget_get_type())
	       || gtk_widget_get_ancestor(widget, GTK_TYPE_BUTTON) || gtk_widget_get_ancestor(widget, GTK_TYPE_LIST_BOX_ROW);
}

// an EditText's hint stays in its label once it holds text, so `tap name` still finds it
static char *label_of(GtkWidget *widget)
{
	if (GTK_IS_LABEL(widget))
		return g_strdup(gtk_label_get_text(GTK_LABEL(widget)));
	const char *text = gtk_editable_get_text(GTK_EDITABLE(widget));
	const char *hint = gtk_text_get_placeholder_text(GTK_TEXT(widget)) ?: "";
	return *text && *hint ? g_strdup_printf("%s: %s", hint, text) : g_strdup(*text ? text : hint);
}

static void add_item(GtkWidget *widget)
{
	char *label = label_of(widget);
	if (*label && on_screen(widget)) {
		g_ptr_array_add(items, g_object_ref(widget));
		g_string_append_printf(labels, "%s[%s,", items->len > 1 ? "," : "", actionable(widget) ? "true" : "false");
		json_string(labels, label);
		g_string_append_c(labels, ']');
	}
	g_free(label);
}

static void walk_widget(GtkWidget *widget)
{
	if (!gtk_widget_get_mapped(widget))
		return;
	// a GtkText's placeholder is a child label
	if (GTK_IS_TEXT(widget) || GTK_IS_LABEL(widget)) {
		add_item(widget);
		return;
	}
	for (GtkWidget *child = gtk_widget_get_first_child(widget); child; child = gtk_widget_get_next_sibling(child))
		walk_widget(child);
}

static void walk(void)
{
	if (items)
		g_ptr_array_unref(items);
	items = g_ptr_array_new_with_free_func(g_object_unref);
	if (labels)
		g_string_free(labels, TRUE);
	labels = g_string_new("[");
	walk_widget(window);
	GList *toplevels = gtk_window_list_toplevels();
	for (GList *l = toplevels; l; l = l->next) {
		if (l->data != window)
			walk_widget(l->data);
	}
	g_list_free(toplevels);
	g_string_append_c(labels, ']');
}

static bool moving(void)
{
	GtkWidget *child = gtk_window_get_child(GTK_WINDOW(window));
	return GTK_IS_STACK(child) && gtk_stack_get_transition_running(GTK_STACK(child));
}

// GTK keeps a real press on an EditText; a dialog's own buttons and rows are GTK's
static bool tap(GtkWidget *widget)
{
	if (GTK_IS_TEXT(widget))
		return gtk_widget_grab_focus(widget);
	GtkWidget *view = gtk_widget_get_ancestor(widget, wrapper_widget_get_type());
	if (view)
		return input_tap(view);
	GtkWidget *control = gtk_widget_get_ancestor(widget, GTK_TYPE_BUTTON) ?: gtk_widget_get_ancestor(widget, GTK_TYPE_LIST_BOX_ROW);
	return control && gtk_widget_activate(control);
}

static bool type(GtkWidget *widget, const char *text)
{
	if (!GTK_IS_TEXT(widget) || !(gtk_widget_has_focus(widget) || gtk_widget_grab_focus(widget)))
		return false;
	int position = gtk_editable_get_position(GTK_EDITABLE(widget));
	gtk_editable_insert_text(GTK_EDITABLE(widget), text, -1, &position);
	gtk_editable_set_position(GTK_EDITABLE(widget), position);
	return true;
}

struct call {
	void (*fn)(void *data);
	void *data;
	GMutex mutex;
	GCond cond;
	bool done;
};

static gboolean run_call(gpointer data)
{
	struct call *call = data;
	call->fn(call->data);
	g_mutex_lock(&call->mutex);
	call->done = true;
	g_cond_signal(&call->cond);
	g_mutex_unlock(&call->mutex);
	return G_SOURCE_REMOVE;
}

static void on_main(void (*fn)(void *data), void *data)
{
	struct call call = {.fn = fn, .data = data};
	g_mutex_init(&call.mutex);
	g_cond_init(&call.cond);
	// not g_main_context_invoke: the default context is this thread's too, and invoke runs the call
	// right here whenever the main thread lets go of it between iterations
	g_idle_add_full(G_PRIORITY_DEFAULT, run_call, &call, NULL);
	g_mutex_lock(&call.mutex);
	while (!call.done)
		g_cond_wait(&call.cond, &call.mutex);
	g_mutex_unlock(&call.mutex);
	g_mutex_clear(&call.mutex);
	g_cond_clear(&call.cond);
}

static void reply(int fd, const char *json)
{
	char *line = g_strconcat(json, "\n", NULL);
	for (size_t off = 0, len = strlen(line); off < len;) {
		ssize_t n = send(fd, line + off, len - off, MSG_NOSIGNAL);
		if (n <= 0)
			break;
		off += n;
	}
	g_free(line);
}

static void get_labels(void *data)
{
	walk();
	*(char **)data = g_strdup(labels->str);
}

struct action {
	enum { ACTION_TAP, ACTION_TYPE, ACTION_BACK } kind;
	int n;
	char *label; // the element's, as the caller saw it
	char *text;
	GtkWidget *widget;
};

static void action_free(struct action *action)
{
	g_clear_object(&action->widget);
	g_free(action->label);
	g_free(action->text);
	g_free(action);
}

static gboolean run_action(gpointer data)
{
	struct action *action = data;
	if (action->kind == ACTION_BACK)
		result = atl_nav_back();
	else if (action->kind == ACTION_TAP)
		result = tap(action->widget);
	else
		result = type(action->widget, action->text);
	action_free(action);
	return G_SOURCE_REMOVE;
}

static bool same_label(int n, const char *label)
{
	if (!items || n < 0 || n >= (int)items->len)
		return false;
	char *now = label_of(items->pdata[n]);
	bool same = !strcmp(now, label);
	g_free(now);
	return same;
}

struct ask {
	struct action *action; // the main thread's once ok
	bool ok;
};

static void ask_action(void *data)
{
	struct ask *ask = data;
	struct action *action = ask->action;
	if (action->kind == ACTION_BACK) {
		walk();
	} else if (!same_label(action->n, action->label)) {
		return;
	} else {
		action->widget = g_object_ref(items->pdata[action->n]);
	}
	ask->ok = true;
	g_free(before);
	before = g_strdup(labels->str);
	result = -1;
	g_idle_add_full(G_PRIORITY_DEFAULT, run_action, action, NULL);
}

static void ask(int fd, struct action *action)
{
	struct ask ask = {.action = action};
	on_main(ask_action, &ask);
	if (!ask.ok)
		action_free(action);
	reply(fd, ask.ok ? "{\"ok\":true}" : "{\"error\":\"the screen changed since the last ls\"}");
}

struct snapshot {
	char *labels, *before;
	int result;
	bool moving;
};

static void get_snapshot(void *data)
{
	struct snapshot *snap = data;
	walk();
	snap->labels = g_strdup(labels->str);
	snap->before = g_strdup(before ?: "");
	snap->result = result;
	snap->moving = moving();
}

// The minimum wait when the screen looks unchanged: a new activity can show a frame or two after
// the tap.
static void settle(int fd, int ms)
{
	gint64 start = g_get_monotonic_time();
	char *prev = NULL;
	struct snapshot snap = {0};
	bool settled = false;
	for (;;) {
		g_free(snap.labels);
		g_free(snap.before);
		on_main(get_snapshot, &snap);
		gint64 elapsed_ms = (g_get_monotonic_time() - start) / 1000;
		if (snap.result >= 0 && !snap.moving && prev && !strcmp(snap.labels, prev)
		    && (strcmp(snap.labels, snap.before) || elapsed_ms >= 300)) {
			settled = true;
			break;
		}
		if (elapsed_ms >= ms)
			break;
		g_free(prev);
		prev = g_strdup(snap.labels);
		g_usleep(100000);
	}
	char *json = g_strdup_printf("{\"labels\":%s,\"settled\":%s,\"ran\":%s,\"result\":%s}", snap.labels,
	                             settled ? "true" : "false", snap.result >= 0 ? "true" : "false",
	                             snap.result < 0 ? "null" : snap.result ? "true" : "false");
	reply(fd, json);
	g_free(json);
	g_free(prev);
	g_free(snap.labels);
	g_free(snap.before);
}

static char *read_line(int fd)
{
	char buf[4096];
	for (size_t n = 0; n < sizeof(buf); n++) {
		if (recv(fd, buf + n, 1, 0) != 1)
			return NULL;
		if (buf[n] == '\n')
			return g_strndup(buf, n);
	}
	return NULL;
}

// "N <json>" after a verb: N, and *json past it
static bool index_arg(const char **rest, int *n)
{
	char *end;
	long value = strtol(*rest, &end, 10);
	if (end == *rest || *end != ' ' || value < 0 || value > 99999)
		return false;
	*n = value;
	*rest = end + 1;
	return true;
}

static void handle(int fd)
{
	char *line = read_line(fd);
	if (!line)
		return;
	const char *rest;
	int n;
	if (!strcmp(line, "ls")) {
		char *json;
		on_main(get_labels, &json);
		char *out = g_strdup_printf("{\"labels\":%s}", json);
		reply(fd, out);
		g_free(out);
		g_free(json);
	} else if (!strcmp(line, "back")) {
		ask(fd, g_memdup2(&(struct action){.kind = ACTION_BACK}, sizeof(struct action)));
	} else if (g_str_has_prefix(line, "settle ") && (n = atoi(line + 7)) > 0) {
		settle(fd, MIN(n, 10000));
	} else if (g_str_has_prefix(line, "tap ") && (rest = line + 4) && index_arg(&rest, &n)) {
		char *label = parse_json_string(&rest);
		if (label)
			ask(fd, g_memdup2(&(struct action){.kind = ACTION_TAP, .n = n, .label = label}, sizeof(struct action)));
	} else if (g_str_has_prefix(line, "type ") && (rest = line + 5) && index_arg(&rest, &n)) {
		char *label = NULL, *text = NULL;
		if (*rest++ == '[' && (label = parse_json_string(&rest)) && *rest++ == ',' && (text = parse_json_string(&rest)) && *rest == ']')
			ask(fd, g_memdup2(&(struct action){.kind = ACTION_TYPE, .n = n, .label = label, .text = text}, sizeof(struct action)));
		else {
			g_free(label);
			g_free(text);
		}
	}
	g_free(line);
}

static gpointer serve(gpointer data)
{
	int listener = GPOINTER_TO_INT(data);
	for (;;) {
		int fd = accept(listener, NULL, NULL);
		if (fd < 0) {
			if (errno == EINTR)
				continue;
			fprintf(stderr, "dbg agent: accept: %s\n", strerror(errno));
			return NULL;
		}
		handle(fd);
		close(fd);
	}
}

void agent_start(void)
{
	static bool started;
	const char *path = getenv("ATL_DBG_AGENT");
	if (!path || started)
		return;
	started = true;
	// Yama lets only ancestors attach otherwise; the debug server's lldb is a sibling
	prctl(PR_SET_PTRACER, PR_SET_PTRACER_ANY);
	struct sockaddr_un addr = {.sun_family = AF_UNIX};
	int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
	if (strlen(path) >= sizeof(addr.sun_path)) {
		fprintf(stderr, "dbg agent: socket path too long: %s\n", path);
		close(fd);
		return;
	}
	strcpy(addr.sun_path, path);
	unlink(path);
	if (bind(fd, (struct sockaddr *)&addr, sizeof(addr)) || listen(fd, 8)) {
		fprintf(stderr, "dbg agent: can't listen on %s: %s\n", path, strerror(errno));
		close(fd);
		return;
	}
	g_thread_unref(g_thread_new("dbg agent", serve, GINT_TO_POINTER(fd)));
}
