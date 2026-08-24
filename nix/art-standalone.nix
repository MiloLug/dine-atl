# art_standalone, built from upstream master (doc/Build.md
# "art_standalone"). Unlike nixpkgs' package, the wolfSSL JSSE provider is
# kept and linked against our JNI-enabled wolfSSL (see wolfssl-jni.nix).
{
  lib,
  stdenv,
  fetchFromGitLab,
  bionic-translation,
  wolfssl-jni,
  python3,
  which,
  jdk17,
  zip,
  xz,
  icu,
  zlib,
  libcap,
  expat,
  openssl,
  libbsd,
  lz4,
  runtimeShell,
  libpng,
  makeWrapper,
  binutils,
}:

stdenv.mkDerivation (finalAttrs: {
  pname = "art-standalone";
  version = "0-unstable-2026-08-03";

  src = fetchFromGitLab {
    owner = "android_translation_layer";
    repo = "art_standalone";
    rev = "66a5d9079b159e9c5819bfeef4a6fff6a5f32719";
    hash = "sha256-4dRKghsPP8XLCcCe6bQDzJVlggBdFWe3l2/ge35HIXA=";
  };

  patches = [
    # Do not hardcode addr2line binary path
    ./patches/art-no-hardcode-path-addr2line.patch
    # fake_log_device.c uses `bool` and a C23 if-initializer without
    # including <stdbool.h>; make it valid C17 so any GCC version builds it.
    ./patches/art-fake-log-device-c23-to-c17.patch
  ];

  postPatch = ''
    chmod +x dalvik/dx/etc/{dx,dexmerger}
    patchShebangs .
    substituteInPlace build/core/config.mk build/core/main.mk \
      --replace-fail "/bin/bash" "${runtimeShell}"
  '';

  enableParallelBuilding = true;

  strictDeps = true;

  nativeBuildInputs = [
    jdk17
    makeWrapper
    python3
    which
    zip
  ];

  buildInputs = [
    bionic-translation
    expat
    icu
    libbsd
    libcap
    libpng
    lz4
    openssl
    xz
    zlib
    wolfssl-jni
  ];

  makeFlags = [
    "____LIBDIR=lib"
    "____PREFIX=${placeholder "out"}"
    "____INSTALL_ETC=${placeholder "out"}/etc"
  ];

  postFixup = ''
    wrapProgram $out/bin/dx \
      --prefix LD_LIBRARY_PATH : $out/lib \
      --prefix PATH : ${lib.makeBinPath [ binutils ]}
  '';

  meta = {
    description = "Art and dependencies with modifications to make it work on Linux";
    homepage = "https://gitlab.com/android_translation_layer/art_standalone";
    # upstream hasn't declared a license yet
    license = lib.licenses.unfree;
    platforms = [ "x86_64-linux" ];
  };
})
