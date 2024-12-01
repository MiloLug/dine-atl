#include <androidfw/androidfw_c_api.h>

#include "../generated_headers/android_content_res_StringBlock.h"

#include "../defines.h"

static void doThrow(JNIEnv* env, const char* exc)
{
    jclass npeClazz;

    npeClazz = (*env)->FindClass(env, exc);

    (*env)->ThrowNew(env, npeClazz, NULL);
}

JNIEXPORT jlong JNICALL Java_android_content_res_StringBlock_nativeCreate(JNIEnv* env, jobject clazz, jbyteArray bArray, jint off, jint len){
	if (bArray == NULL) {
		doThrow(env, "java/lang/NullPointerException");
		return 0;
    	}
	
	jsize bLen = (*env)->GetArrayLength(env, bArray);
	if (off < 0 || off >= bLen || len < 0 || len > bLen || (off+len) > bLen) {
		doThrow(env, "java/lang/IndexOutOfBoundsException");
		return 0;
	}
	jbyte* b = (*env)->GetByteArrayElements(env, bArray, NULL);

	struct ResStringPool* osb = ResStringPool_new(b + off, len, 1);
	if (osb == NULL || ResStringPool_getErrors(osb) != 0) {
        	doThrow(env, "java/lang/IllegalArgumentException");
        	return 0;
	}


	(*env)->ReleaseByteArrayElements(env, bArray, b, 0);

	return (jint)osb;
};

JNIEXPORT jint JNICALL Java_android_content_res_StringBlock_nativeGetSize(JNIEnv* env, jobject clazz,jlong token){
	struct ResStringPool* osb = (struct ResStringPool*)_PTR(token);

	if (osb == NULL) {
		doThrow(env, "java/lang/NullPointerException");
		return 0;
	}

	return ResStringPool_getSize(osb);
};

JNIEXPORT jstring JNICALL Java_android_content_res_StringBlock_nativeGetString(JNIEnv* env, jobject clazz, jlong token, jint idx){
	struct ResStringPool* osb = (struct ResStringPool*)_PTR(token);

	if (osb == NULL) {
		doThrow(env, "java/lang/NullPointerException");
		return 0;
	}

	size_t len;
	const char* str8 = ResStringPool_string8At(osb, idx, &len);
	if (str8 != NULL) {
		return (*env)->NewStringUTF(env, str8);
	}

	const char16_t* str = ResStringPool_stringAt(osb, idx, &len);
	if (str == NULL) {
        	doThrow(env, "java/lang/IndexOutOfBoundsException");
        	return 0;
    	}
	return (*env)->NewString(env, (const jchar*)str, len);
};

JNIEXPORT void JNICALL Java_android_content_res_StringBlock_nativeDestroy(JNIEnv* env, jobject clazz, jlong token){
	struct ResStringPool* osb = (struct ResStringPool*)_PTR(token);

	if (osb == NULL) {
		doThrow(env, "java/lang/NullPointerException");
		return;
	}


	ResStringPool_delete(osb);
};