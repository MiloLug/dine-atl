#include <stdio.h>

#include "src/shared/nav.h"
#include "src/slots/host.h"

bool host_task_to_back(void)
{
	return true;
}

void host_task_moved(bool front, bool by_host)
{
	printf("host: task %s %s\n", front ? "front" : "back", by_host ? "host" : "app");
	fflush(stdout);
}

void host_keep_screen_on(bool on)
{
	printf("host: keep screen on %d\n", on);
	fflush(stdout);
}

static void print_spans(const struct atl_span *spans, int count)
{
	if (!count)
		printf("-");
	for (int i = 0; i < count; i++)
		printf("%s%.0f-%.0f", i ? "," : "", spans[i].top, spans[i].bottom);
}

void host_gesture_exclusion_changed(const struct atl_gesture_exclusion *exclusion)
{
	printf("host: exclusion left ");
	print_spans(exclusion->left, exclusion->left_count);
	printf(" right ");
	print_spans(exclusion->right, exclusion->right_count);
	printf("\n");
	fflush(stdout);
}
