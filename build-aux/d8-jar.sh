#!/bin/sh
# Usage: d8-jar.sh <out.jar> <in.jar>
# Dex a class jar with d8 (--min-api 27, as dine-art's libcore). meson's install step (depfixer
# fix_jar) needs META-INF/MANIFEST.MF, which d8 doesn't write. The dex is stored and aligned, so ART
# maps it from the jar instead of inflating a copy.
set -e
out=$1
in=$2
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/dex" "$tmp/META-INF"
d8 --min-api 27 --output "$tmp/dex" "$in"
printf 'Manifest-Version: 1.0\n' > "$tmp/META-INF/MANIFEST.MF"
# manifest last: fix_jar takes it out and appends it again, which would move a dex behind it off
# its alignment
(cd "$tmp/dex" && zip -q0 "$tmp/unaligned.jar" classes*.dex)
(cd "$tmp" && zip -q "$tmp/unaligned.jar" META-INF/MANIFEST.MF)
zipalign -f 4 "$tmp/unaligned.jar" "$out"
