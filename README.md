# Android Translation Layer

ATL is an compatiblity layer created in 2021 to run Android apps on Linux which translates the Android APIs to Linux APIs, it uses less CPU, RAM memory, storage space, input latency, has faster startup time, and better desktop integration (native file picker, native notifications, using OpenGL and VA-API drivers from the host system) than [Waydroid](https://waydro.id/) and other Android emulators.

### Screenshots

![Angry Birds 3.2.0, Worms 2 Armageddon, and Gravity Defied running side by side by side](https://gitlab.com/android_translation_layer/android_translation_layer/-/raw/master/screenshot.png)
![Oculus Quest version of BeatSaber running on an aarch64 laptop](https://gitlab.com/android_translation_layer/android_translation_layer/-/raw/master/screenshot_2.png)

### Features

- OpenGL/EGL APIs mapped to native Mesa3D drivers
- Android View system mapped to GTK widgets
- TouchEvent APIs emulated by mouse pointer and full multi-touch support for devices with touch screen
- MediaCodec APIs mapped to `libavcodec` with working VA-API hardware acceleration
- AudioTrack APIs mapped to `libalsa` which can use PulseAudio or PipeWire audio servers
- Android Widgets implementation (not feature complete yet)
- Graphic APIs implemented on top of GTK scene graph (not feature complete yet, but enough for some Jetpak Compose apps)
- Desktop integration using xdg-desktop-portal
  - Native file picker
  - Native desktop notifications
  - Automatic dark/light theme detection
  - URL/file opening
- Experimental ARM-to-x86 CPU translation (native bridge) using QEMU, it can be found on this link: https://gitlab.com/android_translation_layer/qemu-based-native-bridge/nb-qemu (Not available in distribution packages yet)

### Compatibility Status

A list of known working applications can be found on the following links:

- https://gitlab.com/android_translation_layer/android_translation_layer/-/wikis/known-working-proprietary-apps
- https://wiki.postmarketos.org/wiki/Android_Translation_Layer/known_working_open_source_applications.

Any application not listed there is unlikely to work out of the box.

### Chat

We have a Matrix room for development discussions and questions, you can join with this link: https://matrix.to/#/#android-translation-layer:matrix.org

### Distribution Packages

- [Flathub](https://github.com/flathub/io.gitlab.android_translation_layer.BaseApp)
- [Alpine Linux](https://pkgs.alpinelinux.org/package/edge/testing/x86_64/android-translation-layer)
- [AUR] https://aur.archlinux.org/packages/android_translation_layer
- [Nix (WIP)](https://github.com/NixOS/nixpkgs/pull/357220)
- [Alpine nightlies](https://gitlab.com/android_translation_layer/aports/-/artifacts)

### How to build from source

Read the [build instructions](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/master/doc/Build.md) document.

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

Note: the test APKs are available at https://gitlab.com/android_translation_layer/atl_test_apks.

### Run after installation

- Run the following commands to install ATL:

```sh
cd builddir
meson install
```

- Run the following command to use the default data directory at `~/.local/share/android_translation_layer/`:

```sh
android-translation-layer [path to apk] [-l activity to launch]
```

- Run the following command to use a custom data dir:

```sh
ANDROID_APP_DATA_DIR=[data dir] android-translation-layer [path to apk] [-l activity to launch]
```

### App data

As mentioned, the default data directory is at `~/.local/share/android_translation_layer/`. The data for each
app is then stored in `~/.local/share/android_translation_layer/[apk-name]_`. Read the following items to learn how it works:

- We pass this directory to the app where AOSP would pass `/data/data/[app-id]`, and extract native
libraries in `lib/`

- We pass this directory to the app where AOSP would pass `/storage/emulated/0` (this means OBBs
will be under `Android/obb/[app-id]/`, and various litter that the app would happily dump on the
"sdcard" on AOSP will end up here as well)

- We pass this directory as an additional resource directory to `libandroidfw`, so you can
for example put something in `assets/file.txt` and if the app tries to load `assets/file.txt` from
it's APK file, it will have preference (do note that some apps read files
from the APK by themselves, and some apps do weird things like only load the file in order to find
it's offset in the APK to then read it out by themselves, so not only will this not always work but
you will sometimes find out by having the app crash)

- We extract some additional files from the APK here for our purposes

The reason that we don't use `[app-id]` for the directory name is simply that we don't have access
to the app id at an early enough point. However, this also allows you to have multiple versions
of the same app not clash. Feel free to rename the apk to `[app-id]_[version].apk`, or simply
`[app-id].apk` if you wish to replace it with a different version later and reuse the data directory.

### APK Installation

You can pass `--install` on the cmdline to "install" an apk instead of launching it. This will copy
the APK to `_installed_apks_` in the data dir (`~/.local/share/android_translation_layer/` by default),
and use the XDG portal to install a `.desktop` file.

### Tweaks

#### Environment Variables

You can use environment variables to make some apps work, read the [doc/Envs.md](./doc/Envs.md) document to learn how to use them.

#### Resolution Changes

Some apps don't like runtime changes to resolution. To workaround this, we allow for specifying the initial resolution.
example with custom width/height:
```sh
android-translation-layer path/to/org.happysanta.gd_29.apk -l org/happysanta/gd/GDActivity -w 540 -h 960
```

#### Potential issues

- On X11, GTK might decide to use GLX, which completely messes up our EGL-dependent code.
Use `GDK_DEBUG=gl-egl` to force the use of EGL.

- On Apple Silicon, the page size is non-standard. Upstream ART is only recently getting patches
to support such non-standard page size, so the version that we use obviously doesn't have any. While there
will probably still be some issues with native libraries, you can work around the issue of AOT-compiled
code not working by adding `-X '-Xnoimage-dex2oat' -X '-Xusejit:false'` to the ATL commandline, which will
force the use of an interpreter. Make sure to clear `~/.cache/art/` since AOT-compiled `oat` files will
still be used if they were generated previously.

### How To Contribute

If you are trying to launch a random app, there's a high chance that we are missing implementations for some stuff that it needs, and we also don't have (sufficiently real looking) stubs for the stuff it says it needs, but doesn't really.

The workflow is basically to see where it fails (usually a `Class` or `Method` was not found) and to create stubs which sufficiently satisfy the app so that it continues trying to launch.

Once the app launches, you may find that some functionality (UI elements, etc) is missing. To enable such functionality, you need to convert the relevant stubs to an actual implementation. You can look at simple widgets (e.g. TextView, or ImageView) to see how to implement a widget such that it shows up as a GTK Widget.

- For more specific instructions, read the [doc/QuickHelp.md](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/master/doc/QuickHelp.md) document.  
- For general description of the architecure, read the [doc/Architecture.md](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/master/doc/Architecture.md) document.

If you want to contribute, and find the codebase overwhelming, don't hesitate to open an issue so we can help you out and possibly write more documentation.

### Roadmap

- [ ] Fix the issues mentioned above

- [ ] fix ugly hacks

- [ ] Implement more stuff (there is a lot of it, and it won't get done if nobody helps... ideally pick a simple-ish application and stub/implement stuff until it works)

- [ ] Explore using Bubblewrap to enforce the security policies that Google helpfully forces apps to comply with (and our own security policies, like no Internet access for apps which really shouldn't need it and are not scummy enough to refuse to launch without it)

### Tips

- The correct format for changing the verbosity of messages going through Android's logging library is `ANDROID_LOG_TAGS=*:v` (where `*` is "all tags" and `v` is "verbosity `verbose` or lesser")

(Note that specifying anything other than `*` as the tag will not work with the host version of liblog)
