#include <math.h>
#include <time.h>

#include "generated_headers/android_os_SystemClock.h"

// elapsedRealtime counts deep sleep, uptimeMillis doesn't; Darwin's CLOCK_MONOTONIC counts sleep
#ifdef __APPLE__
#define CLOCK_ELAPSED CLOCK_MONOTONIC
#define CLOCK_UPTIME  CLOCK_UPTIME_RAW
#else
#define CLOCK_ELAPSED CLOCK_BOOTTIME
#define CLOCK_UPTIME  CLOCK_MONOTONIC
#endif

JNIEXPORT jlong JNICALL Java_android_os_SystemClock_elapsedRealtime(JNIEnv *env, jclass this)
{
	struct timespec t;
	clock_gettime(CLOCK_ELAPSED, &t);
	jlong ret = t.tv_sec * 1000 + lround(t.tv_nsec / 1e6);
	return ret;
}

JNIEXPORT jlong JNICALL Java_android_os_SystemClock_uptimeMillis(JNIEnv *env, jclass this)
{
	struct timespec now;
	clock_gettime(CLOCK_UPTIME, &now);
	return now.tv_sec * 1000 + lround(now.tv_nsec / 1e6);
}

JNIEXPORT jlong JNICALL Java_android_os_SystemClock_elapsedRealtimeNanos(JNIEnv *env, jclass this)
{
	struct timespec t;
	clock_gettime(CLOCK_ELAPSED, &t);
	return t.tv_sec * 1000000000 + t.tv_nsec;
}

JNIEXPORT jlong JNICALL Java_android_os_SystemClock_currentThreadTimeMillis(JNIEnv *env, jclass this)
{
	struct timespec now;
	clock_gettime(CLOCK_THREAD_CPUTIME_ID, &now);
	return now.tv_sec * 1000 + lround(now.tv_nsec / 1e6);
}

JNIEXPORT jboolean JNICALL Java_android_os_SystemClock_setCurrentTimeMillis(JNIEnv *env, jclass this, jlong millis)
{
#ifdef __IPHONE_OS_VERSION_MIN_REQUIRED
	return JNI_FALSE; // iOS apps can't set the clock
#else
	struct timespec ts;
	ts.tv_sec = millis / 1000;
	ts.tv_nsec = (millis % 1000) * 1000000;
	return clock_settime(CLOCK_REALTIME, &ts) == 0;
#endif
}
