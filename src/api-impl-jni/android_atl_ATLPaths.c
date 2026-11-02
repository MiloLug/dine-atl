#include "defines.h"
#include "util.h"

#include "generated_headers/android_atl_ATLPaths.h"

JNIEXPORT jstring JNICALL Java_android_atl_ATLPaths_native_1prefix_1dir(JNIEnv *env, jclass this)
{
	return _JSTRING(get_prefix_dir());
}
