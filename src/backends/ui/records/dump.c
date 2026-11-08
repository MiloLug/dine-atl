#include <stdio.h>
#include <stdlib.h>

#include "src/slots/gfx.h"
#include "src/slots/ui.h"

#include "src/shared/windows.h"

#include "records.h"

// textruns: <start>-<end> and what each run sets
static void dump_runs(FILE *out, const struct ui_view *view)
{
	fputs("textruns:", out);
	for (size_t i = 0; i < view->n_runs; i++) {
		const struct ui_text_run *run = &view->runs[i];
		fprintf(out, " %d-%d", run->start, run->end);
		if (run->set & UI_RUN_STYLE)
			fprintf(out, "%s%s", run->style & UI_TEXT_BOLD ? " bold" : "", run->style & UI_TEXT_ITALIC ? " italic" : "");
		if (run->set & UI_RUN_COLOR)
			fprintf(out, " #%08x", run->color);
		if (run->set & UI_RUN_BACKGROUND)
			fprintf(out, " on #%08x", run->background);
		if (run->set & UI_RUN_SIZE)
			fprintf(out, " %gpx", run->size);
		if (run->set & UI_RUN_FAMILY)
			fprintf(out, " family %d", run->family);
		if (run->set & UI_RUN_UNDERLINE)
			fputs(" underline", out);
		if (run->set & UI_RUN_STRIKE)
			fputs(" strike", out);
		if (run->set & UI_RUN_BASELINE)
			fprintf(out, " shift %g", run->baseline_shift);
		if (run->set & UI_RUN_SCALE_X)
			fprintf(out, " scale %g", run->scale_x);
		fputc(';', out);
	}
	fputc('\n', out);
}

static void dump_view(FILE *out, struct ui_view *view, int depth)
{
	fprintf(out, "viewtree: %*s%s %d,%d %dx%d", depth * 2, "", view->class_name,
	        view->left, view->top, view->right - view->left, view->bottom - view->top);
	if (view->kind == NODE_TEXT)
		fprintf(out, " \"%s\"", view->text);
	fputc('\n', out);
	if (view->n_runs) // a line of its own: the viewtree goldens keep only viewtree lines
		dump_runs(out, view);
	for (size_t i = 0; i < view->n_children; i++)
		dump_view(out, view->children[i], depth + 1);
}

void ui_window_dump(struct ui_window *window, FILE *out)
{
	if (window->shown)
		dump_view(out, window->shown, 0);
	for (size_t i = 0; i < window->n_roots; i++) {
		if (!window->roots[i].view)
			continue;
		const struct atl_window_frame *frame = windows_frame(window->roots[i].window);
		int type = window->roots[i].layout.type;
		const char *kind = type < 1000 ? "dialog" : type < 2000 ? "panel" : type == 2005 ? "toast" : "overlay";
		fprintf(out, "viewtree: window %s %d,%d %dx%d", kind, frame->left, frame->top, frame->width, frame->height);
		if (window->roots[i].chrome) {
			const struct atl_window_frame *panel = windows_panel(window->roots[i].window);
			fprintf(out, " panel %d,%d %dx%d", panel->left, panel->top, panel->width, panel->height);
		}
		fprintf(out, "\n");
		dump_view(out, window->roots[i].view, 1);
	}
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
		unsigned long hits, misses;
		records_measure_stats(&hits, &misses);
		printf("atl frames %d in %.0f ms, draw avg %.2f max %.2f ms, text measures %lu cached %lu\n", frames, e - first, total / frames, max, hits + misses, hits);
		fflush(stdout);
		frames = 0;
	}
}
