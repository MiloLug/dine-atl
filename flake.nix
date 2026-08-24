{
  description = "Android Translation Layer — run Android apps on a Linux system";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      # art_standalone's build system only targets x86_64 hosts
      systems = [ "x86_64-linux" ];
      # art_standalone and bionic_translation are marked unfree in nixpkgs
      # (upstream hasn't declared a license yet); they are this project's core
      # dependencies, so allow exactly those two.
      forAllSystems = nixpkgs.lib.genAttrs systems;

      makePkgs = system:
        import nixpkgs {
          inherit system;
          config.allowUnfreePredicate =
            pkg: builtins.elem (nixpkgs.lib.getName pkg) [
              "art-standalone"
              "bionic-translation"
            ];
        };
    in
    {
      overlays.default = final: prev: {
        android-translation-layer = self.packages.${prev.system}.default;
      };

      packages = forAllSystems (
        system:
        let
          pkgs = makePkgs system;
        in
        rec {
          # Everything doc/Build.md lists under "Additional Dependencies" is
          # built from upstream sources here, so development does not depend
          # on possibly-stale nixpkgs packaging. nixpkgs only provides the
          # toolchain and system libraries.

          # wolfSSL was dropped from nixpkgs; build it exactly as
          # doc/Build.md instructs (v5.8.2-stable, JNI enabled).
          wolfssl-jni = pkgs.callPackage ./nix/wolfssl-jni.nix { };

          bionic-translation = pkgs.callPackage ./nix/bionic-translation.nix { };

          # built with the wolfSSL JSSE provider enabled (nixpkgs' package
          # patches it out)
          art-standalone = pkgs.callPackage ./nix/art-standalone.nix {
            inherit bionic-translation wolfssl-jni;
          };

          # optional; not needed to build or run ATL itself, but some apps
          # expect libOpenSLES.so at runtime
          libOpenSLES = pkgs.callPackage ./nix/libopensles-standalone.nix { };

          android-translation-layer = pkgs.callPackage ./nix/package.nix {
            src = self;
            rev = self.shortRev or self.dirtyShortRev or "unknown";
            inherit art-standalone;
          };

          default = android-translation-layer;
        }
      );

      devShells = forAllSystems (
        system:
        let
          pkgs = makePkgs system;
        in
        {
          default = pkgs.mkShell {
            packages = with pkgs; [
              meson
              ninja
              pkg-config
              openjdk17
              wayland-scanner
            ];
            buildInputs =
              with pkgs;
              [
                alsa-lib
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
              ]
              ++ [
                # self-built; provides dx, dex2oat, c_bio/dl_bio and the
                # art-standalone.pc dependency
                self.packages.${system}.art-standalone
                self.packages.${system}.bionic-translation
              ];
          };
        }
      );
    };
}
