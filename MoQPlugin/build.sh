#!/usr/bin/env bash
# Builds the player, then the plugin, and leaves the release zip in target/.
set -euo pipefail
cd "$(dirname "$0")"

PLAYER_SRC="src/main/js/player"
PLAYER_OUT="src/main/resources/moq-ams-player-build"
NATIVE_DIR="src/main/resources/native/linux-x86_64"
RELEASE_ZIP="target/MoQPlugin-release.zip"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[build]${NC} $*"; }
warn()  { echo -e "${YELLOW}[build]${NC} $*"; }
error() { echo -e "${RED}[build]${NC} $*" >&2; exit 1; }

skip_player=false
for arg in "$@"; do
    case "$arg" in
        --skip-player) skip_player=true ;;
        -h|--help)     echo "usage: $0 [--skip-player]"; exit 0 ;;
        *)             error "unknown option: $arg" ;;
    esac
done

require() {
    command -v "$1" >/dev/null || error "$1 not found. $2"
}

check_java() {
    require java "Install a JDK 17 or newer."
    local major
    major=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')
    if [ -n "$major" ] && [ "$major" -lt 17 ]; then
        error "java $major is too old, AMS needs 17 or newer"
    fi
}

check_node() {
    require npm "On nixos: nix-shell $PLAYER_SRC/shell.nix"
    local major
    major=$(node -v 2>/dev/null | sed 's/v\([0-9]*\).*/\1/')
    if [ -n "$major" ] && [ "$major" -lt 20 ]; then
        error "node $major is too old for vite, need 20 or newer"
    fi
}

# The AMS jar is a provided dependency at the parent pom's version. Maven can pull it
# from the snapshot repo, but locally it usually means you have to build AMS first.
check_ams_artifact() {
    local version
    version=$(sed -n '/<parent>/,/<\/parent>/s|.*<version>\(.*\)</version>.*|\1|p' pom.xml | head -1)
    if [ ! -d "$HOME/.m2/repository/io/antmedia/ant-media-server/$version" ]; then
        warn "io.antmedia:ant-media-server:$version is not in ~/.m2, maven will try to download it"
    fi
}

check_deps() {
    require mvn "Install maven 3.6 or newer."
    check_java
    check_ams_artifact
    [ -f mvn-settings.xml ] || error "mvn-settings.xml is missing, the build reads it with -s"

    for binary in moq moq-relay; do
        [ -f "$NATIVE_DIR/$binary" ] || error "missing native binary: $NATIVE_DIR/$binary"
    done

    if [ "$skip_player" = true ]; then
        [ -f "$PLAYER_OUT/play.html" ] || error "$PLAYER_OUT is missing or empty, drop --skip-player"
    else
        check_node
    fi
}

build_player() {
    info "Building the player"
    (cd "$PLAYER_SRC" && npm install && npm run build)

    # Asset names are hashed, so replace the directory instead of copying over it.
    rm -rf "$PLAYER_OUT"
    cp -r "$PLAYER_SRC/dist" "$PLAYER_OUT"
}

check_deps

if [ "$skip_player" = true ]; then
    info "Skipping the player, shipping whatever is in $PLAYER_OUT"
else
    build_player
fi

info "Building the plugin"
mvn clean package -Dmaven.test.skip=true -Dgpg.skip=true -s mvn-settings.xml

[ -f "$RELEASE_ZIP" ] || error "maven finished but $RELEASE_ZIP is not there"
info "Done: $RELEASE_ZIP ($(du -h "$RELEASE_ZIP" | cut -f1))"
