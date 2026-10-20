#include <stdio.h>

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
