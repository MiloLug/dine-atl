#include <fcntl.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "defines.h"
#include "util.h"

#include "generated_headers/android_atl_ATLInstaller.h"

#define CHUNK (64 * 1024)

static bool same_chunks(int a, int b, char *buf_a, char *buf_b)
{
	for (;;) {
		ssize_t n = read(a, buf_a, CHUNK);
		if (n < 0)
			return false;
		if (n == 0)
			return read(b, buf_b, 1) == 0;
		ssize_t got = 0;
		while (got < n) {
			ssize_t m = read(b, buf_b + got, n - got);
			if (m <= 0)
				return false;
			got += m;
		}
		if (memcmp(buf_a, buf_b, n))
			return false;
	}
}

static bool same_contents(int a, int b)
{
	char *buf = malloc(2 * CHUNK);
	bool same = buf && same_chunks(a, b, buf, buf + CHUNK);
	free(buf);
	return same;
}

// in C: a byte loop under -Xint would take seconds for an APK of a few MB
JNIEXPORT jboolean JNICALL Java_android_atl_ATLInstaller_native_1same_1file(JNIEnv *env, jclass this, jstring path_a, jstring path_b)
{
	const char *a_path = _CSTRING(path_a);
	const char *b_path = _CSTRING(path_b);
	int a = open(a_path, O_RDONLY | O_CLOEXEC);
	int b = open(b_path, O_RDONLY | O_CLOEXEC);
	struct stat sa, sb;
	bool same = a >= 0 && b >= 0 && !fstat(a, &sa) && !fstat(b, &sb) && sa.st_size == sb.st_size && same_contents(a, b);
	if (a >= 0)
		close(a);
	if (b >= 0)
		close(b);
	(*env)->ReleaseStringUTFChars(env, path_a, a_path);
	(*env)->ReleaseStringUTFChars(env, path_b, b_path);
	return same;
}
