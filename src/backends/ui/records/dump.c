#include <stdio.h>
#include <stdlib.h>

#include "src/slots/gfx.h"
#include "src/slots/ui.h"

#include "records.h"

static void dump_view(FILE *out, struct ui_view *view, int depth)
{
	fprintf(out, "viewtree: %*s%s %d,%d %dx%d", depth * 2, "", view->class_name,
	        view->left, view->top, view->right - view->left, view->bottom - view->top);
	if (view->kind == NODE_TEXT)
		fprintf(out, " \"%s\"", view->text);
	fputc('\n', out);
	for (size_t i = 0; i < view->n_children; i++)
		dump_view(out, view->children[i], depth + 1);
}

void ui_window_dump(struct ui_window *window, FILE *out)
{
	if (window->shown)
		dump_view(out, window->shown, 0);
	fprintf(out, "viewtree: end\n");
	fflush(out);
}

void records_test_view_freed(const struct ui_view *view)
{
	if (!getenv("ATL_DUMP_VIEWTREE"))
		return;
	printf("viewfreed: %s", view->class_name);
	if (view->kind == NODE_TEXT)
		printf(" \"%s\"", view->text);
	putchar('\n');
	fflush(stdout);
}

void records_test_view_drawn(const struct ui_view *view)
{
	if (!getenv("ATL_DUMP_DISPLAYLIST"))
		return;
	printf("displaylist: %s %dx%d\n", view->class_name, view->drawn_width, view->drawn_height);
	gfx_canvas_dump(view->list, stdout);
	printf("displaylist: end\n");
	fflush(stdout);
}
