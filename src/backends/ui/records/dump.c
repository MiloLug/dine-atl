#include <stdio.h>

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
