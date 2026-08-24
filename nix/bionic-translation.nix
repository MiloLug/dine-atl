# bionic_translation, built from upstream master (doc/Build.md
# "bionic_translation"). Provides libc_bio / dl_bio and the art-standalone
# build dependency.
{
  lib,
  stdenv,
  fetchFromGitLab,
  meson,
  ninja,
  pkg-config,
  libbsd,
  libelf,
  libglvnd,
  libunwind,
  wayland,
}:

stdenv.mkDerivation (finalAttrs: {
  pname = "bionic-translation";
  version = "0-unstable-2026-08-06";

  src = fetchFromGitLab {
    owner = "android_translation_layer";
    repo = "bionic_translation";
    rev = "ef588129f1b505e62de8750793e44aba98817a98";
    hash = "sha256-QrDjjovxwxMujjGpseifkoSnTMkXNv5OXigvfIJLR9I=";
  };

  nativeBuildInputs = [
    meson
    ninja
    pkg-config
  ];

  buildInputs = [
    libbsd
    libelf
    libglvnd
    libunwind
    wayland
  ];

  meta = {
    description = "Set of libraries for loading bionic-linked .so files on musl/glibc";
    homepage = "https://gitlab.com/android_translation_layer/bionic_translation";
    # upstream hasn't declared a license yet
    license = lib.licenses.unfree;
    platforms = lib.platforms.linux;
  };
})
