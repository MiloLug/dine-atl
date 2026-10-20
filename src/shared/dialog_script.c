#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "src/slots/ui.h"

#include "upcall.h"

static bool loaded, scripted;
static char *rest; // the answers not given yet

static bool match(const struct ui_alert *alert, const char *answer, int *which)
{
	if (!strcmp(answer, "!cancel")) {
		*which = UI_DIALOG_CANCEL;
		return true;
	}
	for (int i = 0; i < 3; i++) {
		if (alert->buttons[i] && !strcmp(alert->buttons[i], answer)) {
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

bool atl_dialog_scripted(const struct ui_alert *alert, int *which)
{
	if (!loaded) {
		loaded = true;
		const char *answers = getenv("ATL_DIALOG_ANSWERS");
		scripted = answers != NULL;
		rest = answers ? strdup(answers) : NULL;
	}
	if (!scripted)
		return false;
	char *answer = rest ? strsep(&rest, ";") : NULL;
	if (answer && match(alert, answer, which))
		return true;
	// a test that waits for a click would hang until its timeout
	fflush(stdout);
	fprintf(stderr, "dialog: no scripted answer for \"%s\" (next answer: %s)\n", alert->title ?: "", answer ?: "none");
	_exit(1);
}
