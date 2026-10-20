#include "src/slots/host.h"

/* A desktop window has no back: Back on the root activity finishes it. */

bool host_task_to_back(void)
{
	return false;
}

void host_task_moved(bool front, bool by_host) {}
