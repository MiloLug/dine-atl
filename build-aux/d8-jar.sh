#!/bin/sh
# Usage: d8-jar.sh <out.jar> <in.jar>
# Dex a class jar with d8 (--min-api 27, as dine-art's libcore) and add a minimal manifest:
# meson's install step (depfixer fix_jar) reads META-INF/MANIFEST.MF from every installed .jar,
# and d8, unlike dx, doesn't write one.
set -e
out=$1
in=$2
d8 --min-api 27 --output "$out" "$in"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/META-INF"
printf 'Manifest-Version: 1.0\n' > "$tmp/META-INF/MANIFEST.MF"
(cd "$tmp" && zip -q "$out" META-INF/MANIFEST.MF)
