#include <stdint.h>
#include <stdlib.h>
#include <sys/resource.h>

#include "src/api-impl-jni/defines.h"

#include "host_linux.h"

void init__r_debug();

/*
 * The main thread's stack is initialized to 128KiB by Linux.
 * When the stack grows below the allocated mapping, the kernel
 * automagically maps in more memory (as long as there is no existing
 * mapping in the way).
 * However, ART puts in a guard page of it's own in order to catch stack
 * overflows (and if they happen in Java code, it handles them gracefully).
 * When ART uses pthread_getattr_np to get the main thread's stack size,
 * glibc reports RLIMIT_STACK (minus TLS and stuff), while musl reports
 * the current stack size. Therefore on musl we have to pre-grow the stack
 * to make the kernel actually grow the mapping before ART puts in it's guard
 * page and makes further growth impossible.
 */
static void pregrow_stack()
{
	/* set RLIMIT_STACK to 8 MiB, which should fit both our 6 MiB stack and whatever
	 * the libc stores above the top of stack */
	setrlimit(RLIMIT_STACK, &(struct rlimit){8 * MiB, 8 * MiB});
	/* accessing the first element of this array will grow the stack down 6MiB */
	volatile uint8_t dummy[6 * MiB];
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wuninitialized"
	/* noop, but should generate an access because volatile */
	dummy[0] = dummy[0];
#pragma GCC diagnostic pop
}

void atl_linux_host_init(void)
{
	pregrow_stack();

	/* this has to be done in the main executable */
	init__r_debug();

	// locale on android is always either C or C.UTF-8, and some apps might unbeknownst to them depend on that
	// for correct functionality
	setenv("LC_ALL", "C.UTF-8", 1);
}
