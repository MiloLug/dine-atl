#### Directory structure

- `doc/` - Documentation
- `src/api-impl/` - Java code implementing the Android APIs
- `src/api-impl-jni/` - C code implementing things which it doesn't make sense to do in Java (ideally this would be most things)
- `src/libandroid/` - C code implementing `libandroid.so` (this is needed by most JNI libs which come with Android apps)
- `src/main-executable` - Code for the main executable, which sets stuff up (including the JVM) and launches the activity specified on the commandline
- `test-apks/` - All APKs known to somewhat work, except paid proprietary ones which we sadly can't really include
- `res/` - Default resources taken from AOSP Android 6 release
- `./com.google.android.gms.apk` - [microG](https://microg.org/) is a temporary solution to run apps with Google Play Services framework dependencies (FWIW, the actual solution will likely also use microG)

#### Philosophy

Companion infographic for this section: `doc/android_translation_architecture.svg`

We believe that the cleanest approach for supporting Android apps on Linux desktop platforms is to make a chirurgical cut on the Android platform stack as close to the apps themselves as possible, and sew on a new implementation of whatever we have cut off.

If you glance at the companion infographic, you can see that the place where
we chose to make the cut is directly between the Apps and the Java APIs
provided by the Android frameworks. (additionally, for apps with native
components, we also keep just the respective `.so` files from those apps
and provide implementations/shims for the system libraries that they are linked
against)

#### Current control flow

1. The user executes the main executable (`android_translation_layer`)
2. The executable is compiled from `src/main-executable/main.c`:
	1. `int main(int argc, char **argv)` sets up a `GtkApplication`, command line handling, and calls `g_application_run`
	2. `GtkApplication` glue parses the cmdline and calls `static void open(GtkApplication *app, GFile** files, gint nfiles, const gchar* hint, struct jni_callback_data *d)`
	3. `static void open(GtkApplication *app, GFile** files, gint nfiles, const gchar* hint, struct jni_callback_data *d)`:
		1. Constructs the classpath from the following:
			- The path to `api-impl.jar` (contains all the implementations of Android framework functions)
			- The path to the app's APK (passed to us on cmdline), making the bytecode within (and `resources.arsc`, which is currently the only other file read straight from the APK) available in classpath
			- The path to a microG apk, needed for apps with a dependency on GSF; this is specified after the app's APK so that the the app's APK is the first ZIP file in the classpath (needed for getting the right `resources.arsc`, TODO: ask for the classloader which loaded the activity that was specified on the command line)
		2. Contructs other options (mainly library path) for and launches the Dalvik virtual machine
		3. Load `libtrasnlation_layer_main.so` using the internal version of `Runtime.loadLibrary` which lets us register this with the right classloader so the Java code which declares the native methods is the Java code that can use them
		4. Setup a JNI handle cache
		5. Sets display size to be passed to apps according to optionally specified window size (some apps handle runtime resizing poorly, and our EGL code currently doesn't handle it at all)
		6. Creates a GTK Window with said size and shows it on screen
		7. Calls the `OnCreate` method of the class specified with the `-l` option

3. The Activity specfified with `-l` calls various android APIs, which we hopefully implement; 
typically, it will set up some Android widgets, which will appear in the GTK window as GTK widgets

4. You, the user, interact with the GTK widgets, which the app might have registered callbacks for; 
as long as our implementation of Android APIs is good enough for what the app needs, you can use the app as you would on Android
