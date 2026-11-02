A translation layer that allows running Android apps on a Linux system

![Angry Birds 3.2.0, Worms 2 Armageddon, and Gravity Defied running side by side by side](https://gitlab.com/android_translation_layer/android_translation_layer/-/raw/master/screenshot.png)
![Oculus Quest version of BeatSaber running on an aarch64 laptop](https://gitlab.com/android_translation_layer/android_translation_layer/-/raw/master/screenshot_2.png)

### Build
see [build documentation](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/master/doc/Build.md)

### Run in builddir
```sh
cd builddir
```
For an example of a full game working that can be distributed along this:
```sh
RUN_FROM_BUILDDIR= LD_LIBRARY_PATH=./ ./android-translation-layer /path/to/test_apks/org.happysanta.gd_29.apk -l org/happysanta/gd/GDActivity
```
Or for a sample app using OpenGL from native code to do it's rendering:
```sh
RUN_FROM_BUILDDIR= LD_LIBRARY_PATH=./ ./android-translation-layer path/to/test_apks/gles3jni.apk -l com/android/gles3jni/GLES3JNIActivity
```
Note: the test apks are available at https://gitlab.com/android_translation_layer/atl_test_apks.

### Run after installation
```sh
cd builddir
meson install
```

To run an apk (it is installed first, unless the prefix already holds that exact file):
```sh
android-translation-layer [path to apk] [-l activity to launch]
```
To run an installed package:
```sh
android-translation-layer --package [package name] [-l activity to launch]
```
The prefix is `~/.dine`, or `DINE_PREFIX`:
```sh
DINE_PREFIX=[dir] android-translation-layer [path to apk]
```

### The prefix
Everything an install keeps sits in the prefix:
 - `registry.sqlite`: one row per installed package, which every app's `PackageManager` reads. It
starts with stand-ins for `com.google.android.gms`, `gsf` and `vending`; installing a package of
the same name (microG) replaces one.
 - `apps/[package]/`: the installed apks, byte for byte.
 - `data/[package]/`: the app's data. We pass this directory to the app where AOSP would pass
`/data/data/[package]`, and extract native libs in `lib/`. We also pass it where AOSP would pass
`/storage/emulated/0` (OBBs end up under `Android/obb/[package]/`, and whatever the app would dump
on the "sdcard" on AOSP ends up here as well). It is also an additional resource directory for
`libandroidfw`: put something in `assets/file.txt`, and if the app loads `assets/file.txt` from its
apk, this file wins (some apps read their apk by themselves, so this doesn't always work, and you
may find out by the app crashing). Some other files from the apk are extracted here for our
purposes.

### Installing and uninstalling
```sh
android-translation-layer --install [path to apk]...           # also a .desktop file, through the xdp portal
android-translation-layer --install-internal [path to apk]...  # no .desktop file
android-translation-layer --uninstall [package name]           # its apks, its data and its .desktop file
```
Several apks are one package, the base apk first. An apk that asks for
`android.permission.FAKE_PACKAGE_SIGNATURE` (microG) is reported with the signature in its
`fake-signature` meta-data only with `--allow-fake-signature`; an app that installs one asks the
user instead. The .desktop file runs `--package [package name]`.

### Tweaks
##### Resolution Changes
Some apps don't like runtime changes to resolution. To sidestep this, we allow for specifying the initial resolution.
example with custom width/height:
```sh
android-translation-layer path/to/org.happysanta.gd_29.apk -l org/happysanta/gd/GDActivity -w 540 -h 960
```

#### Potential issues
- On X11, Gtk might decide to use GLX, which completely messes up our EGL-dependent code.
Use GDK_DEBUG=gl-egl to force the use of EGL.
- On Apple Silicon, the page size is non-standard. Upstream ART is only recently getting patches
to support such non-standard page size, so the version we use obviously doesn't have any. While there
will probably still be some issues with native libraries, you can work around the issue of AOT-compiled
code not working by adding `-X '-Xnoimage-dex2oat' -X '-Xusejit:false'` to the atl cmdline, which will
force the use of an interpreter. Make sure to clear `~/.cache/art/` since AOT-compiled oat files will
still be used if they were generated previously.

### Contribute
If you are trying to launch a random app, chances are that we are missing implementations for some stuff that it needs, and we also don't have (sufficiently real looking) stubs for the stuff it says it needs but doesn't really.

The workflow is basically to see where it fails (usually a Class or Method was not found) and to create stubs which sufficiently satisfy the app so that it continues trying to launch.

Once the app launches, you may find that some functionality (UI elements, ...) is missing. To enable such functionality, you need to convert the relevant stubs to actual implementation. You can look at simple widgets (e.g. TextView, or ImageView) to see how to implement a widget such that it shows up as a Gtk Widget.

For more specific instructions, see [doc/QuickHelp.md](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/master/doc/QuickHelp.md).  
For general description of the architecure, see [doc/Architecture.md](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/master/doc/Architecture.md).

If you want to contribute, and find the codebase overwhelming, don't hesitate to open an issue so we can help you out and possibly write more documentation.

### Roadmap

- fix issues mentioned above

- fix ugly hacks

- implement more stuff (there is a lot of it, and it won't get done if nobody helps... ideally pick a simple-ish application and stub/implement stuff until it works)

- explore using bubblewrap to enforce the security policies that google helpfully forces apps to comply with (and our own security policies, like no internet access for apps which really shouldn't need it and are not scummy enough to refuse to launch without it)

### Tips

- the correct format for changing verbosity of messages going through android's logging library is `ANDROID_LOG_TAGS=*:v` (where `*` is "all tags" and `v` is "verbosity `verbose` or lesser"  
(note that specifying anything other than `*` as the tag will not work with the host version of liblog)
