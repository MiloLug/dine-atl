# Optional dependency (doc/Build.md "libOpenSLES"): not required to build or
# run ATL itself, but some Android apps expect libOpenSLES.so at runtime.
{
  lib,
  stdenv,
  fetchFromGitLab,
  meson,
  ninja,
  pkg-config,
  openjdk17,
  SDL2,
  libsndfile,
}:

stdenv.mkDerivation {
  pname = "libopensles-standalone";
  version = "0-unstable-2025-09-13";

  src = fetchFromGitLab {
    owner = "android_translation_layer";
    repo = "libopensles-standalone";
    rev = "bdb857a4baadbc8c036db85a5da26c5269a751c5";
    hash = "sha256-mPbT9ch3W5hqHLFPk45uz+sDPAuXCHcDmpHn37J4VBg=";
  };

  nativeBuildInputs = [
    meson
    ninja
    pkg-config
    openjdk17 # project declares the `java` language and depends on jni
  ];

  buildInputs = [
    SDL2
    libsndfile
  ];

  meta = {
    description = "Standalone implementation of OpenSL ES for desktop Linux";
    homepage = "https://gitlab.com/android_translation_layer/libopensles-standalone";
    # upstream hasn't declared a license yet
    license = lib.licenses.unfree;
    platforms = lib.platforms.linux;
  };
}
