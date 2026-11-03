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
	flockfile(stdout); // whole, beside other apps' main threads
	printf("displaylist: %s %dx%d\n", view->class_name, view->drawn_width, view->drawn_height);
	gfx_canvas_dump(view->list, stdout);
	printf("displaylist: end\n");
	fflush(stdout);
	funlockfile(stdout);
}

static double ms(const struct timespec *t)
{
	return t->tv_sec * 1e3 + t->tv_nsec / 1e6;
}

void records_test_frame_drawn(const struct timespec *start, const struct timespec *end)
{
	static __thread double first, last, total, max; // the window's: one per app's main thread
	static __thread int frames;
	double s = ms(start), e = ms(end);
	if (!frames || s - last > 500) {
		first = s;
		frames = 0;
		total = max = 0;
	}
	frames++;
	total += e - s;
	max = e - s > max ? e - s : max;
	last = e;
	if (e - first >= 5000) {
		printf("atl frames %d in %.0f ms, draw avg %.2f max %.2f ms\n", frames, e - first, total / frames, max);
		fflush(stdout);
		frames = 0;
	}
}
