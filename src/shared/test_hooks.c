#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "src/slots/ui.h"

#include "test_hooks.h"

// per Android main thread: each app answers its dialogs from the list's start
static __thread bool loaded, scripted;
static __thread char *rest; // the answers not given yet

static bool match(const struct ui_alert *alert, const char *answer, int *which)
{
	if (!strcmp(answer, "!cancel")) {
		*which = UI_DIALOG_CANCEL;
		return true;
	}
	for (int i = 0; i < 3; i++) {
		if (alert->buttons[i] && !alert->disabled[i] && !strcmp(alert->buttons[i], answer)) {
			*which = -1 - i;
			return true;
		}
	}
	for (int i = 0; i < alert->n_items; i++) {
		if (alert->items[i] && !strcmp(alert->items[i], answer)) {
			*which = i;
			return true;
		}
	}
	return false;
}

// the next answer, NULL at the end; scripted: whether there are answers at all
static char *next_answer(void)
{
	if (!loaded) {
		loaded = true;
		const char *answers = getenv("ATL_DIALOG_ANSWERS");
		scripted = answers != NULL;
		rest = answers ? strdup(answers) : NULL;
	}
	return scripted && rest ? strsep(&rest, ";") : NULL;
}

// a test that waits for a click would hang until its timeout
static void no_answer(const char *what, const char *title, const char *answer)
{
	fflush(stdout);
	fprintf(stderr, "%s: no scripted answer for \"%s\" (next answer: %s)\n", what, title, answer ?: "none");
	_exit(1);
}

bool atl_dialog_scripted(const struct ui_alert *alert, int *which)
{
	char *answer = next_answer();
	if (!scripted)
		return false;
	if (answer && match(alert, answer, which))
		return true;
	no_answer("dialog", alert->title ?: "", answer);
	return false;
}

bool atl_menu_scripted(const struct ui_menu_item *items, int n, int *index)
{
	char *answer = next_answer();
	if (!scripted)
		return false;
	if (answer && !strcmp(answer, "!cancel")) {
		*index = UI_MENU_DISMISSED;
		return true;
	}
	for (int i = 0; answer && i < n; i++) {
		if (items[i].enabled && !items[i].has_submenu && !strcmp(items[i].title, answer)) {
			*index = i;
			return true;
		}
	}
	no_answer("menu", n ? items[0].title : "", answer);
	return false;
}

void atl_test_window_ready(struct ui_window *window)
{
	if (getenv("ATL_DUMP_VIEWTREE")) {
		flockfile(stdout); // whole, beside other apps' main threads
		ui_window_dump(window, stdout);
		funlockfile(stdout);
	}
}

static void print_freed(const char *line)
{
	if (!getenv("ATL_DUMP_VIEWTREE"))
		return;
	puts(line);
	fflush(stdout);
}

void atl_test_dialog_freed(void)
{
	print_freed("dialogfreed:");
}

void atl_test_drawable_freed(const char *kind)
{
	char line[128];
	snprintf(line, sizeof(line), "drawablefreed: %s", kind);
	print_freed(line);
}
