# wolfSSL is no longer packaged in nixpkgs (dropped 2026-04), so we build it
# ourselves exactly as doc/Build.md instructs: pinned v5.8.2-stable with JNI
# enabled, so art_standalone's wolfSSL JSSE provider (libwolfssljni) can be
# compiled and linked against it.
{
  lib,
  stdenv,
  fetchFromGitHub,
  autoreconfHook,
}:

stdenv.mkDerivation (finalAttrs: {
  pname = "wolfssl-jni";
  version = "5.8.2";

  src = fetchFromGitHub {
    owner = "wolfSSL";
    repo = "wolfssl";
    tag = "v${finalAttrs.version}-stable";
    hash = "sha256-rWBfpI6tdpKvQA/XdazBvU5hzyai5PtKRBpM4iplZDU=";
  };

  # Build.md runs `autoreconf -i` before ./configure
  nativeBuildInputs = [ autoreconfHook ];

  configureFlags = [
    "--enable-shared"
    "--disable-opensslall"
    "--disable-opensslextra"
    "--enable-aescbc-length-checks"
    "--enable-curve25519"
    "--enable-ed25519"
    "--enable-ed25519-stream"
    "--enable-oldtls"
    "--enable-base64encode"
    "--enable-tlsx"
    "--enable-scrypt"
    "--disable-examples"
    "--enable-crl"
    "--enable-jni"
    "--enable-sessioncerts"
  ];

  # The test suite has known failures when JNI is enabled; correctness is
  # exercised downstream by art_standalone's wolfSSL JSSE provider.
  doCheck = false;


  meta = {
    description = "wolfSSL TLS library built with JNI support (for art_standalone)";
    homepage = "https://www.wolfssl.com/";
    changelog = "https://github.com/wolfSSL/wolfssl/releases/tag/v${finalAttrs.version}-stable";
    license = lib.licenses.gpl3Plus;
    platforms = lib.platforms.linux;
  };
})
