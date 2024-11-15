# Architecture

## Directory Structure

`doc/` - documentation
`src/api-impl/` - Java code implementing the android APIs
`src/api-impl-jni/` - C code implementing things which it doesn't make sense to do in Java (ideally this would be most things)
`src/libandroid/` - C code implementing `libandroid.so` (this is needed by most JNI libs which come with android apps)
`src/main-executable` - code for the main executable, which sets stuff up (including the JVM) and launches the activity specified on the cmdline
`test-apks/` - all apks known to somewhat work, except paid proprietary ones which we sadly can't really include
`res/` - default resources taken from AOSP Android 6 release

`./com.google.android.gms.apk`: microg; stopgap solution to run apps with GSF dependencies (fwiw, the actual solution will likely also use MicroG)

## Philosophy

![architecture](android_translation_architecture.svg)

We believe that the cleanest approach for supporting android apps on desktop
Linux platforms is to make a chirurgical cut on the android platform stack
as close to the apps themselves as possible, and sew on a new implementation
of whatever we have cut off.

If you glance at the companion infographic, you can see that the place where
we chose to make the cut is directly between the Apps and the Java APIs
provided by the android frameworks. (additionally, for apps with native
components, we also keep just the respective .so files from those apps
and provide implementations/shims for the system libraries they are linked
against)

## Control Flow

1. the user executes the main executable (`android_translation_layer`)  
2. the executable is compiled from `src/main-executable/main.c`:
    1. `int main(int argc, char **argv)` sets up a GtkApllication, cmdline handling, and calls `g_application_run`
    2. GtkApplication glue parses the cmdline and calls `static void open(GtkApplication *app, GFile** files, gint nfiles, const gchar* hint, struct jni_callback_data *d)`
    3. `static void open(GtkApplication *app, GFile** files, gint nfiles, const gchar* hint, struct jni_callback_data *d)`:
        1. constructs the classpath from the following:
            - the path to api-impl.jar (contains all the implementations of android framework functions)
            - the path to the app's apk (passed to us on cmdline), making the bytecode within (and resources.arsc, which is currently the only other file read straight from the apk) available in classpath
            - the path to a microG apk, needed for apps with a dependency on GSF; this is specified after the app's apk so that the the app's apk is the first zip file in the classpath (needed for getting the right resources.arsc, TODO: ask for the classloader which loaded the activity that was specified on the cmdline)
        2. constructs other options (mainly library path) for and launches the dalvik virtual machine
        3. load `libtrasnlation_layer_main.so` using the internal version of Runtime.loadLibrary which lets us register this with the right classloader so the java code which declares the native methods is the java code that can use them
        4. sets up a JNI handle cache
        5. sets display size to be passed to apps according to optionally specified window size (some apps handle runtime resizing poorly, and our EGL code currently doesn't handle it at all)
        6. creates a Gtk Window with said size and shows it on screen
        7. calls the OnCreate method of the class specified with the `-l` option
3. the Activity specfified with `-l` calls various android APIs, which we hopefully implement;
typically, it will set up some android widgets, which will appear in the Gtk window as Gtk widgets
4. you, the user, interact with the Gtk widgets, which the app might have registered callbacks for;
as long as our implementation of android APIs is good enough for what the app needs, you can use the app
as you would on android
