#include <stdio.h>

#include "src/slots/ui.h"

/* A Toast as "toast:" lines. */

void ui_toast_show(struct ui_window *window, const char *text, int gravity, int x, int y)
{
	printf("toast: show \"%s\" gravity=0x%x x=%d y=%d\n", text, gravity, x, y);
	fflush(stdout);
}

void ui_toast_hide(struct ui_window *window)
{
	printf("toast: hide\n");
	fflush(stdout);
}
