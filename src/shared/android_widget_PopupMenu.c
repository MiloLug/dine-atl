#include <stdlib.h>

#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/generated_headers/android_widget_PopupMenu.h"
#include "src/api-impl-jni/util.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

#include "app.h"
#include "menu.h"
#include "upcall.h"

struct atl_menu {
	struct atl_app *app;
	jobject popup;  // the Java PopupMenu, held while the menu is up
	void *backend;  // ui_menu_show's
	bool answered;  // Java got its result: an item, or the dismissal
};

struct posted_answer {
	struct atl_menu *menu;
	int index;
};

static void tell_java(JNIEnv *env, struct atl_menu *menu, int index)
{
	menu->answered = true;
	jmethodID result = _METHOD(_CLASS(menu->popup), "atlMenuResult", "(I)V");
	(*env)->CallVoidMethod(env, menu->popup, result, index);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
}

// the backend's one answer: after a Java dismissal it only frees
static void answer_posted(void *data)
{
	struct posted_answer *answer = data;
	struct atl_menu *menu = answer->menu;
	JNIEnv *env = get_jni_env();
	if (!menu->answered)
		tell_java(env, menu, answer->index);
	ui_menu_free(menu->backend);
	_UNREF(menu->popup);
	free(menu);
	free(answer);
}

void atl_menu_answer(struct atl_menu *menu, int index)
{
	struct posted_answer *answer = malloc(sizeof(*answer));
	*answer = (struct posted_answer){menu, index};
	if (!loop_main_post(atl_app_loop(menu->app), answer_posted, answer))
		free(answer); // the app ended: its menus stay allocated
}

// titles, parents and flags (1 enabled, 2 checkable, 4 checked, 8 has a submenu) per item, depth first
JNIEXPORT jlong JNICALL Java_android_widget_PopupMenu_nativeShow(JNIEnv *env, jobject this, jlong anchor, jobjectArray titles, jintArray parents, jintArray flags)
{
	int n = (*env)->GetArrayLength(env, titles);
	struct ui_menu_item *items = calloc(n ? n : 1, sizeof(*items));
	jint *parent = (*env)->GetIntArrayElements(env, parents, NULL);
	jint *flag = (*env)->GetIntArrayElements(env, flags, NULL);
	for (int i = 0; i < n; i++) {
		jstring title = (*env)->GetObjectArrayElement(env, titles, i);
		items[i] = (struct ui_menu_item){
			.title = jstring_to_utf8(env, title),
			.parent = parent[i],
			.enabled = flag[i] & 1,
			.checkable = flag[i] & 2,
			.checked = flag[i] & 4,
			.has_submenu = flag[i] & 8,
		};
		(*env)->DeleteLocalRef(env, title);
	}
	(*env)->ReleaseIntArrayElements(env, parents, parent, JNI_ABORT);
	(*env)->ReleaseIntArrayElements(env, flags, flag, JNI_ABORT);
	struct atl_menu *menu = calloc(1, sizeof(*menu));
	menu->app = atl_app_current();
	menu->popup = _REF(this);
	menu->backend = ui_menu_show(atl_thread_app_window(), _PTR(anchor), items, n, menu);
	for (int i = 0; i < n; i++)
		free((char *)items[i].title);
	free(items);
	return _INTPTR(menu);
}

// before the backend's answer: Java hears of its dismissal at once, the backend's answer then frees
JNIEXPORT void JNICALL Java_android_widget_PopupMenu_nativeClose(JNIEnv *env, jobject this, jlong ptr)
{
	struct atl_menu *menu = _PTR(ptr);
	if (menu->answered)
		return;
	ui_menu_close(menu->backend);
	tell_java(env, menu, UI_MENU_DISMISSED);
}
