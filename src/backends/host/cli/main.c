#include <getopt.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "src/api-impl-jni/app/android_app_Activity.h"
#include "src/api-impl-jni/defines.h"
#include "src/api-impl-jni/handle_cache.h"
#include "src/backends/ui/headless/input.h"
#include "src/shared/app.h"
#include "src/shared/host_linux.h"
#include "src/shared/nav.h"
#include "src/shared/startup.h"
#include "src/slots/loop.h"
#include "src/slots/ui.h"

/* Plain C entry for builds without a desktop toolkit: apps in one VM, each with its main activity
 * or an instrumentation; or an install or uninstall into the prefix. */

#define USAGE "usage: %s [-c] [-d DPI] [-X JVM_OPTION]... [-l ACTIVITY | --instrument CLASS] APK... [{-l ACTIVITY | --instrument CLASS} APK...]...\n" \
              "       %s [-c] [-d DPI] [-X JVM_OPTION]... [-l ACTIVITY | --instrument CLASS] --package PACKAGE\n"                                \
              "       %s --install [--allow-fake-signature] [-X JVM_OPTION]... APK...\n"                                                      \
              "       %s --uninstall PACKAGE [-X JVM_OPTION]...\n"

// each app runs on its own thread, with the process main thread's stack size (host_linux.c)
#define APP_THREAD_STACK (8 * 1024 * 1024)

// "insets" and 12 dp: system gestures, mandatory gestures, navigation bars, each left top right bottom
static bool parse_insets(const char *line, struct atl_window_insets *insets)
{
	struct atl_edges *e[] = {&insets->system_gestures, &insets->mandatory_gestures, &insets->navigation_bars};
	return sscanf(line, "insets %f %f %f %f %f %f %f %f %f %f %f %f",
	              &e[0]->left, &e[0]->top, &e[0]->right, &e[0]->bottom, &e[1]->left, &e[1]->top, &e[1]->right, &e[1]->bottom,
	              &e[2]->left, &e[2]->top, &e[2]->right, &e[2]->bottom) == 12;
}

// "touch down|move|up|cancel <id> <x> <y>", px in the window
static bool parse_touch(struct ui_window *window, const char *line)
{
	static const char *const kinds[] = {[HEADLESS_TOUCH_DOWN] = "down", [HEADLESS_TOUCH_MOVE] = "move", [HEADLESS_TOUCH_UP] = "up", [HEADLESS_TOUCH_CANCEL] = "cancel"};
	char kind[8];
	int id;
	float x, y;
	if (sscanf(line, "touch %7s %d %f %f", kind, &id, &x, &y) != 4)
		return false;
	for (size_t i = 0; i < sizeof(kinds) / sizeof(*kinds); i++) {
		if (!strcmp(kind, kinds[i])) {
			headless_touch(window, i, id, x, y);
			return true;
		}
	}
	return false;
}

// "key down|up <keycode> [<meta> [<unicode>]]": KeyEvent's code and META_* flags, decimal
static bool parse_key(struct ui_window *window, const char *line)
{
	char action[8];
	int code, meta = 0, unicode = 0;
	if (sscanf(line, "key %7s %d %d %d", action, &code, &meta, &unicode) < 2)
		return false;
	if (strcmp(action, "down") && strcmp(action, "up"))
		return false;
	headless_key(window, !strcmp(action, "down"), code, meta, unicode);
	return true;
}

// "\n" a newline, "\\" a backslash
static char *unescape(const char *text)
{
	char *out = malloc(strlen(text) + 1), *o = out;
	for (; *text; text++) {
		if (*text == '\\' && (text[1] == 'n' || text[1] == '\\'))
			*o++ = *++text == 'n' ? '\n' : '\\';
		else
			*o++ = *text;
	}
	*o = '\0';
	return out;
}

/* "text commit|compose <text>", "text delete <before> <after>", "text select <start> <end>",
 * "text tap <x> <y>", "text action|dismiss|hold|release": the keyboard's field, UTF-16 positions */
static bool parse_text(struct ui_window *window, const char *line)
{
	static const struct {
		const char *name;
		enum headless_text_what what;
	} kinds[] = {
		{"commit ", HEADLESS_TEXT_COMMIT}, {"compose ", HEADLESS_TEXT_COMPOSE}, {"delete ", HEADLESS_TEXT_DELETE}, {"select ", HEADLESS_TEXT_SELECT},
		{"tap ", HEADLESS_TEXT_TAP}, {"action", HEADLESS_TEXT_ACTION}, {"dismiss", HEADLESS_TEXT_DISMISS}, {"hold", HEADLESS_TEXT_HOLD},
		{"release", HEADLESS_TEXT_RELEASE},
	};
	if (strncmp(line, "text ", 5))
		return false;
	line += 5;
	for (size_t i = 0; i < sizeof(kinds) / sizeof(*kinds); i++) {
		size_t length = strlen(kinds[i].name);
		if (strncmp(line, kinds[i].name, length) || (kinds[i].name[length - 1] != ' ' && line[length]))
			continue;
		const char *rest = line + length;
		struct headless_text text = {.what = kinds[i].what, .text = ""};
		if (text.what == HEADLESS_TEXT_DELETE || text.what == HEADLESS_TEXT_SELECT) {
			if (sscanf(rest, "%d %d", &text.a, &text.b) != 2)
				return false;
		} else if (text.what == HEADLESS_TEXT_TAP) {
			if (sscanf(rest, "%f %f", &text.x, &text.y) != 2)
				return false;
		}
		char *unescaped = text.what == HEADLESS_TEXT_COMMIT || text.what == HEADLESS_TEXT_COMPOSE ? unescape(rest) : NULL;
		if (unescaped)
			text.text = unescaped;
		headless_text(window, &text);
		free(unescaped);
		return true;
	}
	return false;
}

// -c: system navigation and input from stdin for the app (data), one command per line; the loop
// slot has no fd watch
static void *read_commands(void *data)
{
	struct atl_app *app = data;
	char *line = NULL;
	size_t size = 0;
	while (getline(&line, &size, stdin) != -1) {
		line[strcspn(line, "\n")] = '\0';
		struct atl_window_insets insets;
		if (!strcmp(line, "back"))
			atl_nav_back(app);
		else if (!strcmp(line, "home"))
			atl_nav_home(app);
		else if (!strcmp(line, "front"))
			atl_nav_front(app);
		else if (parse_insets(line, &insets))
			atl_nav_set_insets(app, &insets);
		else if (!strcmp(line, "dump"))
			headless_dump(app->window);
		else if (!strncmp(line, "tap ", 4))
			headless_tap(app->window, line + 4);
		else if (!strncmp(line, "answer ", 7))
			headless_answer(app, line + 7);
		else if (!parse_touch(app->window, line) && !parse_key(app->window, line) && !parse_text(app->window, line))
			fprintf(stderr, "commands: back, home, front, insets <12 dp>, dump, tap <text>, answer <label>, touch down|move|up|cancel <id> <x> <y>, "
			                "key down|up <code> [<meta> [<unicode>]], text commit|compose <text>, text delete <before> <after>, "
			                "text select <start> <end>, text tap <x> <y>, text action|dismiss|hold|release; not \"%s\"\n",
			        line);
	}
	free(line);
	return NULL;
}

static void start_instrumentation(JNIEnv *env, struct atl_app *app, const char *class_name)
{
	jmethodID create = _STATIC_METHOD(handle_cache.instrumentation.class, "create", "(Landroid/atl/ATLLoadedApp;Ljava/lang/String;Landroid/content/Intent;)Landroid/app/Instrumentation;");
	(*env)->CallStaticObjectMethod(env, handle_cache.instrumentation.class, create, app->record, _JSTRING(class_name), NULL);
	if ((*env)->ExceptionCheck(env)) {
		// create handles Exceptions itself; after an Error no runner thread would ever exit
		(*env)->ExceptionDescribe(env);
		exit(1);
	}
}

/* One app of the command line: an -l or --instrument opens the next one, and the APKs after it are
 * its splits (APKs before the first opener belong to the first app). */
struct cli_app {
	char **apks; // the base first
	int napks;
	const char *activity;        // NULL: the main activity
	const char *instrumentation; // NULL: none
	struct atl_app *app;
	pthread_t thread;
};

static JavaVM *vm;
static bool commands;

static void *run_app(void *data)
{
	struct cli_app *cli = data;
	struct atl_app *app = cli->app;
	JNIEnv *env;
	JavaVMAttachArgs args = {.version = JNI_VERSION_1_6, .name = "main"};
	if ((*vm)->AttachCurrentThread(vm, (void **)&env, &args) != JNI_OK) {
		fprintf(stderr, "error: can't attach the thread of %s\n", cli->apks ? cli->apks[0] : "the app");
		exit(1);
	}
	atl_app_create(env, app, ui_window_new(app, NULL));
	if (commands) {
		pthread_t reader;
		pthread_create(&reader, NULL, read_commands, app);
		pthread_detach(reader);
	}
	if (cli->instrumentation) {
		start_instrumentation(env, app, cli->instrumentation);
	} else {
		jobject main_activity = atl_activity_create_main(env, app, cli->activity, NULL);
		activity_start(env, app, main_activity);
		(*env)->DeleteLocalRef(env, main_activity);
	}
	loop_main_run(app->loop);
	// ART aborts the process when a thread it knows exits attached
	(*vm)->DetachCurrentThread(vm);
	return NULL;
}

int main(int argc, char **argv)
{
	atl_linux_host_init();

	struct atl_window_config window = {ATL_WINDOW_WIDTH_DEFAULT, ATL_WINDOW_HEIGHT_DEFAULT, ATL_DENSITY_DPI_DEFAULT, UI_DISPLAY_UNKNOWN};
	char **jvm_options = calloc(argc, sizeof(*jvm_options));
	int n_jvm_options = 0;
	bool install = false;
	bool allow_fake_signature = false;
	const char *uninstall = NULL;
	const char *package = NULL;
	struct cli_app *apps = calloc(argc, sizeof(*apps));
	int n_apps = 0;
	bool usage = false;
	static const struct option long_options[] = {
		{"instrument", required_argument, NULL, 'I'},
		{"install", no_argument, NULL, 'i'},
		{"allow-fake-signature", no_argument, NULL, 'F'},
		{"uninstall", required_argument, NULL, 'U'},
		{"package", required_argument, NULL, 'P'},
		{0},
	};
	int opt;
	// "-": APKs come in order (as 1), so each belongs to the app opened before it
	while ((opt = getopt_long(argc, argv, "-cd:l:X:", long_options, NULL)) != -1) {
		struct cli_app *current = n_apps ? &apps[n_apps - 1] : NULL;
		switch (opt) {
			case 1:
				if (!current)
					current = &apps[n_apps++];
				if (!current->apks)
					current->apks = calloc(argc, sizeof(*current->apks));
				current->apks[current->napks++] = optarg;
				break;
			case 'I':
			case 'l':
				if (!current || current->activity || current->instrumentation)
					current = &apps[n_apps++];
				*(opt == 'I' ? &current->instrumentation : &current->activity) = optarg;
				break;
			case 'c':
				commands = true;
				break;
			case 'i':
				install = true;
				break;
			case 'F':
				allow_fake_signature = true;
				break;
			case 'U':
				uninstall = optarg;
				break;
			case 'P':
				package = optarg;
				break;
			case 'd':
				window.density_dpi = atoi(optarg);
				usage |= window.density_dpi <= 0;
				break;
			case 'X':
				jvm_options[n_jvm_options++] = optarg;
				break;
			default:
				usage = true;
		}
	}
	bool app = !install && !uninstall;
	bool openers = false, apkless = false;
	for (int i = 0; i < n_apps; i++) {
		openers |= apps[i].activity || apps[i].instrumentation;
		apkless |= !apps[i].napks;
	}
	if (package) // its activity or instrumentation, if any, opened an app without APKs
		usage |= n_apps > 1 || (n_apps && apps[0].napks);
	else
		usage |= apkless;
	usage |= (n_apps > 0 && !package) + (package != NULL) + (uninstall != NULL) != 1 || (install && !n_apps) ||
	         (!app && (commands || openers)) || (install && n_apps > 1) || (allow_fake_signature && !install) ||
	         (commands && n_apps > 1);
	if (usage) {
		fprintf(stderr, USAGE, argv[0], argv[0], argv[0], argv[0]);
		return 1;
	}

	struct atl_paths paths;
	if (!app) {
		atl_prepare(&paths, false);
		JNIEnv *env = atl_vm_start(&paths, jvm_options, &window);
		atl_exit(env, install ? atl_install(env, apps[0].apks, apps[0].napks, allow_fake_signature) : atl_uninstall(env, uninstall));
	}
	if (!n_apps) // --package alone
		n_apps = 1;
	bool instrument = false;
	for (int i = 0; i < n_apps; i++)
		instrument |= apps[i].instrumentation != NULL;
	atl_prepare(&paths, instrument);
	JNIEnv *env = atl_vm_start(&paths, jvm_options, &window);
	(*env)->GetJavaVM(env, &vm);
	for (int i = 0; i < n_apps; i++) {
		apps[i].app = atl_app_new();
		if (atl_app_prepare(env, apps[i].app, apps[i].apks, apps[i].napks, package) < 0)
			atl_exit(env, 1);
	}
	// design.md: groupless attaches have the host's app only while it runs one
	if (n_apps == 1)
		atl_app_take_attaches(env, apps[0].app);

	pthread_attr_t attr;
	pthread_attr_init(&attr);
	pthread_attr_setstacksize(&attr, APP_THREAD_STACK);
	for (int i = 0; i < n_apps; i++) {
		if (pthread_create(&apps[i].thread, &attr, run_app, &apps[i])) {
			fprintf(stderr, "error: can't start an app's thread\n");
			atl_exit(env, 1);
		}
	}
	pthread_attr_destroy(&attr);
	for (int i = 0; i < n_apps; i++)
		pthread_join(apps[i].thread, NULL);

	atl_exit(env, 0);
	return 0;
}
