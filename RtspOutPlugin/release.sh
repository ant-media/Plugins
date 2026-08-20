#!/bin/sh
# Builds target/RtspOutPlugin-release.zip with the tests run.
set -e

MEDIAMTX=src/main/native/linux-x86_64/antmedia-mediamtx
if [ ! -x "$MEDIAMTX" ]; then
	echo "$MEDIAMTX is missing or not executable, the zip needs it" >&2
	exit 1
fi

# NixOS has no system libva and javacpp preloads a broken one of its own before libavutil,
# so anything touching ffmpeg dies on an undefined symbol without this. No-op elsewhere.
ARGS=""
if [ -d /nix/store ]; then
	FF=$HOME/.javacpp/cache/ffmpeg-7.1-1.5.11-linux-x86_64.jar/org/bytedeco/ffmpeg/linux-x86_64
	JB=$HOME/.javacpp/cache/javacpp-1.5.11-linux-x86_64.jar/org/bytedeco/javacpp/linux-x86_64
	VA=$(ls -d /nix/store/*-libva-2*/lib 2>/dev/null | head -1)
	LD_LIBRARY_PATH="$VA:$FF:$JB:$LD_LIBRARY_PATH"
	export LD_LIBRARY_PATH
	ARGS=-DargLine=-Djavacpp.pathsFirst=true
fi

# skipTests is on in the Ant Media parent pom, so a release has to ask for them
mvn clean package -DskipTests=false $ARGS \
	-Dgpg.skip=true -Dmaven.javadoc.skip=true -s mvn-settings.xml

echo
echo "Release package:"
ls -lh target/RtspOutPlugin-release.zip
unzip -l target/RtspOutPlugin-release.zip
