#include <gtk/gtk.h>
#include <libportal/portal.h>

#include <jni.h>
#include <string.h>

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_app_Activity.h"
#include "android_app_Activity.h"

extern GtkWindow *window;

JNIEXPORT void JNICALL Java_android_app_Activity_nativeOpenURI(JNIEnv *env, jclass class, jstring uriString)
{
	static XdpPortal *portal = NULL;
	if (!portal) {
		portal = xdp_portal_new();
	}

	const char *uri = (*env)->GetStringUTFChars(env, uriString, NULL);
	xdp_portal_open_uri(portal, NULL, uri, XDP_OPEN_URI_FLAG_NONE, NULL, NULL, NULL);
	(*env)->ReleaseStringUTFChars(env, uriString, uri);
}

extern GtkWindow *window; // TODO: get this in a better way

struct filechooser_callback_data {
	jobject activity;
	jint request_code;
	jint action;
};

#define RESULT_OK       -1
#define RESULT_CANCELED 0

static void file_dialog_callback(GObject *source_object, GAsyncResult *res, gpointer data)
{
	struct filechooser_callback_data *d = data;
	GtkFileDialog *dialog = GTK_FILE_DIALOG(source_object);
	GFile *(*const finish_functions[])(GtkFileDialog *, GAsyncResult *, GError **) = {
		gtk_file_dialog_open_finish,
		gtk_file_dialog_save_finish,
		gtk_file_dialog_select_folder_finish,
	};

	GFile *file = finish_functions[d->action](dialog, res, NULL);
	JNIEnv *env = get_jni_env();
	jmethodID fileChooserResultCallback = _METHOD(handle_cache.activity.class, "fileChooserResultCallback", "(IIILjava/lang/String;)V");

	if (file) {
		char *uri = g_file_get_uri(file);

		(*env)->CallVoidMethod(env, d->activity, fileChooserResultCallback, d->request_code, RESULT_OK, d->action, _JSTRING(uri));
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);

		g_free(uri);
		g_object_unref(file);
	} else {
		(*env)->CallVoidMethod(env, d->activity, fileChooserResultCallback, d->request_code, RESULT_CANCELED, d->action, NULL);
	}
	free(d);
}

JNIEXPORT void JNICALL Java_android_app_Activity_nativeFileChooser(JNIEnv *env, jobject this, jint action, jstring type_jstring, jstring filename_jstring, jint request_code)
{
	const char *chooser_title = ((char *[]){"Open File", "Save File", "Select Folder"})[action];
	GtkFileDialog *dialog = gtk_file_dialog_new();
	gtk_file_dialog_set_title(GTK_FILE_DIALOG(dialog), chooser_title);

	const char *type = type_jstring ? (*env)->GetStringUTFChars(env, type_jstring, NULL) : NULL;
	if (type && !strchr(type, '*')) {
		GtkFileFilter *filter = gtk_file_filter_new();
		gtk_file_filter_add_mime_type(filter, type);
		gtk_file_filter_set_name(filter, type);
		gtk_file_dialog_set_default_filter(GTK_FILE_DIALOG(dialog), filter);
		(*env)->ReleaseStringUTFChars(env, type_jstring, type);
	}
	const char *filename = filename_jstring ? (*env)->GetStringUTFChars(env, filename_jstring, NULL) : NULL;
	if (filename) {
		gtk_file_dialog_set_initial_name(GTK_FILE_DIALOG(dialog), filename);
		(*env)->ReleaseStringUTFChars(env, filename_jstring, filename);
	}

	struct filechooser_callback_data *callback_data = malloc(sizeof(struct filechooser_callback_data));
	callback_data->activity = _REF(this);
	callback_data->request_code = request_code;
	callback_data->action = action;
	void (*const file_dialog_functions[])(GtkFileDialog *, GtkWindow *, GCancellable *, GAsyncReadyCallback, gpointer) = {
		gtk_file_dialog_open,
		gtk_file_dialog_save,
		gtk_file_dialog_select_folder,
	};
	file_dialog_functions[action](dialog, window, NULL, file_dialog_callback, callback_data);
}

JNIEXPORT jboolean JNICALL Java_android_app_Activity_isInMultiWindowMode(JNIEnv *env, jobject this)
{
	return !gtk_window_is_maximized(window);
}
