#!/bin/sh
# Fetches the pinned MediaMTX build into src/main/native/. The binary is not in git.
set -e

VERSION=v1.20.1
ARCH=linux_amd64
SHA256=dbf4c21f6378949f2cbdaadf4a8b10baa3ce022b570da1411eb64f2142b07b7b

DEST=src/main/native/linux-x86_64
URL=https://github.com/bluenviron/mediamtx/releases/download/$VERSION/mediamtx_${VERSION}_${ARCH}.tar.gz

mkdir -p $DEST
curl -sSL "$URL" | tar xz -C $DEST mediamtx LICENSE
chmod +x $DEST/mediamtx

echo "$SHA256  $DEST/mediamtx" | sha256sum -c -
echo "MediaMTX $VERSION ready in $DEST"
