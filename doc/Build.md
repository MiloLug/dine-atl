## Dependencies

### Alpine Container

Containerization can be used for a consistent development environment across Linux distributions.

Devcontainers provide an ephemeral environment kept in sync with the repository. If you prefer a persistent environment, consider using Toolbox instead.

Either method will provide an Alpine environment ready for [building](#build).

#### Devcontainers

Install Docker Engine: https://docs.docker.com/engine/

Follow instructions for your preferred implementation:
- Visual Studio Code: https://code.visualstudio.com/docs/devcontainers/containers
- Intellij IDEA Ultimate: https://www.jetbrains.com/help/idea/connect-to-devcontainer.html
- DevPod: https://devpod.sh/
- Dev Container CLI: https://github.com/devcontainers/cli

#### Toolbox

Install Toolbox:

```sh
sudo dnf install toolbox
```

Build image using podman:
```sh
podman build -t atl_dev .devcontainer
```

Create toolbox:
```sh
toolbox create --image atl_dev atl_dev
```

#### Installing the ATL package

With this setup installing ATL becomes trivial:

```sh
sudo apk add android-translation-layer
```

#### Installing ATL from source

If you want to build ATL from source, you can skip the Additional Dependencies section and continue with the build steps from below.

### Debian
```sh
sudo apt install libasound2-dev libavcodec-dev libcap-dev  libdrm-dev libglib2.0-dev libgtk-4-dev libgudev-1.0-dev libopenxr-dev libportal-dev libsqlite3-dev
```

### Fedora
```sh
sudo dnf install java-17-openjdk-devel 'pkgconfig(gtk4)' 'pkgconfig(libbsd)' 'pkgconfig(libportal)' 'pkgconfig(sqlite3)' 'pkgconfig(libwebp)' 'pkgconfig(liblz4)' 'pkgconfig(openxr)'
```

## Additional Dependencies
### Skia
If you distro ships this already (e.g. `skia-sharp` on Alpine), you can just install the package and skip this step.

Install `gn` via your system's package manager.
```sh
git clone https://github.com/Mis012/skia.git -b with-patches-applied
cd skia
cp build/linux-self-hosted/DEPS DEPS
python3 tools/git-sync-deps
export arch=x64
gn gen "out/linux/$arch" --args='is_official_build=true skia_enable_tools=false target_os="linux" target_cpu="$arch" skia_use_icu=false skia_use_sfntly=false skia_use_piex=true skia_use_system_harfbuzz=true skia_use_system_expat=true skia_use_system_freetype2=true skia_use_system_libjpeg_turbo=true skia_use_system_libpng=true skia_use_system_libwebp=true skia_use_system_zlib=true skia_enable_gpu=true extra_cflags=[ "-DSKIA_C_DLL" ] linux_soname_version="99.9"'
ninja -C "out/linux/$arch" SkiaSharp
sudo cp out/linux/$arch/libSkiaSharp.so.99.9 /usr/local/lib64/
sudo ln -s /usr/local/lib64/libSkiaSharp.so.99.9 /usr/local/lib64/libSkiaSharp.so
```
Note:
- You can also use [this nuget package](https://www.nuget.org/api/v2/package/SkiaSharp.NativeAssets.Linux/2.88.5) if it's compatible with your distro. It's an older upstream version before the ABI break which is fixed in our skia repository.
- For `x86` or `aarch64`: set `arch` accordingly.
- On alpine: add `-fpermissive` to `--args`.

### wolfSSL
If you distro ships wolfSSL with JNI enabled already, you can just install the package and skip this step.
```sh
git clone https://github.com/wolfSSL/wolfssl.git
cd wolfssl
git checkout v5.7.4-stable
autoreconf -i
./configure --enable-shared --disable-opensslall --disable-opensslextra --enable-aescbc-length-checks --enable-curve25519 --enable-ed25519 --enable-ed25519-stream --enable-oldtls --enable-base64encode --enable-tlsx --enable-scrypt --disable-examples --enable-crl --enable-jni
make
sudo make install
```

### bionic_translation
If you distro ships this already (e.g. `bionic_translation` on Alpine), you can just install the package and skip this step.
```sh
git clone https://gitlab.com/android_translation_layer/bionic_translation.git
cd bionic_translation
meson setup builddir
cd builddir
meson compile
sudo meson install
```

### art_standalone
If you distro ships this already (e.g. `art_standalone` on Alpine), you can just install the package and skip this step.
```sh
git clone https://gitlab.com/android_translation_layer/art_standalone.git
cd art_standalone
make ____LIBDIR=lib
sudo make ____LIBDIR=lib install
```
Note: adjust `____LIBDIR` depending on your distro (e.g some distros use `lib32`,`lib` or `lib`,`lib64` for multilib).

### libOpenSLES
Optional (not required for Android Translation Layer itself but some apps depend on it).

If you distro ships this already (e.g. `libopensles-standalone` on Alpine), you can just install the package and skip this step.
```sh
git clone https://gitlab.com/android_translation_layer/libopensles-standalone.git
cd libopensles-standalone
meson setup builddir
cd builddir
meson compile
sudo meson install
```

## Build
```sh
git clone https://gitlab.com/android_translation_layer/android_translation_layer.git
cd android_translation_layer
meson setup builddir
cd builddir
meson compile
```

## Install
```sh
cd builddir
meson install
```
