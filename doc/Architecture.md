#### directory structure
`doc/` - documentation
`src/api-impl/` - Java code implementing the android APIs
`src/api-impl-jni/` - C code implementing things which it doesn't make sense to do in Java (ideally this would be most things); natives not yet behind a slot, which build only with ui=gtk
`src/slots/` - the C interfaces of the backend slots: loop, host, ui, gfx
`src/backends/<slot>/<name>/` - one implementation of a slot each; meson options pick one per slot
`src/shared/` - C code every build shares: startup phases, activities, windows, upcalls
`src/libandroid/` - C code implementing `libandroid.so` (this is needed by most JNI libs which come with android apps)
`src/main-executable` - code for the main executable, which sets stuff up (including the JVM) and launches the activity specified on the cmdline
`src/test-runner/` - Java code for test-runner.jar, which contains helpers for instrumentation / testing; taken from AOSP with some changes
`res/framework-res/` - sources for framework-res.apk (taken from AOSP, heavily pruned to remove anything not used by apps)
`res/fonts.xml` - fonts.xml taken from AOSP

#### philosophy

companion infographic for this section:
`doc/android_translation_architecture.svg`

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

#### current control flow (in broad strokes)

The gtk host is `src/main-executable/main.c`. The cli (`src/backends/host/cli/`) and embedded
(`src/backends/host/embedded/`) hosts run the same phases (`atl_*`, `src/shared/startup.c`) with
their own loop and window.

1. the user executes the main executable (`android-translation-layer`)

2. `main` sets up a GtkApplication (a run with `--package` takes the package as its id) and calls
   `g_application_run`, whose `open` (APKs given) or `activate` (`--package`) handler calls `start_app`

3. `start_app`:
	1. `atl_prepare`: opens the prefix and finds api-impl.jar, framework-res.apk and, for `--instrument`, the test runner's jar
	2. `atl_vm_start`: creates the VM with those on the classpath (the app's APKs aren't on it), sets the screen's size and density, loads `libtranslation_layer_main.so` with the framework's class loader (so the classes that declare the native methods can use them) and fills the JNI handle cache
	3. `atl_app_new` and `atl_app_prepare`: `ATLInstaller.prepareApp` installs the given APKs unless the registry holds them already, or looks `--package` up, and returns the app's record (`ATLLoadedApp`, with the app's own class loader); the app's data dir is made
	4. builds the GtkApplicationWindow with its stack of activities and extracts the app's native libraries
	5. `atl_app_create`: joins the calling thread to the app, makes its main Looper, applies a Home, front or night mode that was set before the start, and calls `ATLLoadedApp.start`, which makes the Application, creates the manifest's content providers and calls `Application.onCreate`
	6. `atl_activity_create_main`: `Activity.createMainActivity` creates the `-l` activity or, without one, the manifest's launcher activity (with `--instrument`, the Instrumentation is created instead and starts the activities itself)
	7. presents the window and calls `activity_start`, which runs the activity's lifecycle (only `onCreate` while the task is in the back)
	8. returns to the glib main loop - at this point whatever happens is event-driven
