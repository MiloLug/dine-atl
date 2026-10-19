#include "defines.h"
#include "util.h"

#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

#include "generated_headers/android_os_MessageQueue.h"
#include "src/slots/loop.h"

struct native_message_queue {
	struct loop_worker *worker; // NULL on the main thread, whose loop belongs to the platform
};

void prepare_main_looper(JNIEnv *env)
{
	loop_main_prepare();

	(*env)->CallStaticVoidMethod(env, handle_cache.looper.class, handle_cache.looper.prepareMainLooper);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

JNIEXPORT jlong JNICALL Java_android_os_MessageQueue_nativeInit(JNIEnv *env, jclass this)
{
	struct native_message_queue *message_queue = malloc(sizeof(struct native_message_queue));

	message_queue->worker = loop_is_main_thread() ? NULL : loop_worker_new();

	return _INTPTR(message_queue);
}

JNIEXPORT void JNICALL Java_android_os_MessageQueue_nativeDestroy(JNIEnv *env, jclass this, jlong ptr)
{
	struct native_message_queue *message_queue = _PTR(ptr);

	if (message_queue->worker)
		loop_worker_free(message_queue->worker);
	free(message_queue);
}

JNIEXPORT jboolean JNICALL Java_android_os_MessageQueue_nativePollOnce(JNIEnv *env, jclass this, jlong ptr, jint timeout_millis)
{
	struct native_message_queue *message_queue = _PTR(ptr);

	if (!message_queue->worker) {
		if (!timeout_millis)
			return false;
		if (timeout_millis != -1) // -1 means no more messages
			loop_main_schedule(timeout_millis);
		return true; // indicate that java side should return to block in the platform loop
	}

	loop_worker_poll(message_queue->worker, timeout_millis);
	return false;
}

JNIEXPORT void JNICALL Java_android_os_MessageQueue_nativeWake(JNIEnv *env, jclass this, jlong ptr)
{
	struct native_message_queue *message_queue = _PTR(ptr);

	if (message_queue->worker)
		loop_worker_wake(message_queue->worker);
	else
		loop_main_wake();
}

JNIEXPORT jboolean JNICALL Java_android_os_MessageQueue_nativeIsIdling(JNIEnv *env, jclass this, jlong ptr)
{
	struct native_message_queue *message_queue = _PTR(ptr);

	return message_queue->worker && loop_worker_is_idling(message_queue->worker);
}
