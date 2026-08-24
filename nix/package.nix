# android-translation-layer itself (meson build, see doc/Build.md).
{
  lib,
  stdenv,
  src,
  rev ? "unknown",
  meson,
  ninja,
  pkg-config,
  openjdk17,
  wayland-scanner,
  makeWrapper,
  bintools,
  alsa-lib,
  ffmpeg,
  fontconfig,
  glib,
  gtk4,
  libdrm,
  libglvnd,
  libgudev,
  libportal-gtk4,
  openxr-loader,
  sqlite,
  vulkan-loader,
  wayland,
  wayland-protocols,
  webkitgtk_6_0,
  bionic-translation,
  art-standalone,
}:

stdenv.mkDerivation (finalAttrs: {
  pname = "android-translation-layer";
  version = "0-unstable-${rev}";

  inherit src;

  patches = [
    # main.c derives the dex install dir from libart.so's location, which
    # assumes art and ATL share a prefix (/usr). In nix they live in separate
    # store paths, so point it at our own output instead.
    ./patches/main-c-dex-install-dir.patch
  ];

  postPatch = ''
    substituteInPlace src/main-executable/main.c \
      --replace-fail '"@out@/lib/java/dex"' "\"$out/lib/java/dex\""
  '';

  nativeBuildInputs = [
    makeWrapper
    meson
    ninja
    openjdk17
    pkg-config
    wayland-scanner
    # provides `dx`, used at build time to dex the jars
    art-standalone
  ];

  buildInputs = [
    alsa-lib
    bionic-translation
    ffmpeg
    fontconfig
    glib
    gtk4
    libdrm
    libglvnd
    libgudev
    libportal-gtk4
    openxr-loader
    sqlite
    vulkan-loader
    wayland
    wayland-protocols
    webkitgtk_6_0
    art-standalone
  ];

  postFixup = ''
    wrapProgram $out/bin/android-translation-layer \
      --prefix LD_LIBRARY_PATH : ${art-standalone}/lib/art \
      --prefix PATH : ${
        lib.makeBinPath [
          art-standalone # dex2oat
          bintools # addr2line
        ]
      }
  '';

  meta = {
    description = "Translation layer that allows running Android apps on a Linux system";
    homepage = "https://gitlab.com/android_translation_layer/android_translation_layer";
    license = lib.licenses.gpl3Plus;
    platforms = lib.platforms.linux;
    mainProgram = "android-translation-layer";
  };
})
