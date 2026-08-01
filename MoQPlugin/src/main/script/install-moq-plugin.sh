#!/usr/bin/env bash
set -euo pipefail

# ─── Config ───────────────────────────────────────────────────────────────────
PLUGINS_DIR="${PLUGINS_DIR:-/usr/local/antmedia/plugins}"
PLUGIN_JAR="${PLUGIN_JAR:-MoQPlugin.jar}"
INSTALL_DIR="${INSTALL_DIR:-/usr/local/bin}"
BINARIES=("moq-cli" "moq-relay")

# ─── Helpers ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[moq]${NC} $*"; }
warn()  { echo -e "${YELLOW}[moq]${NC} $*"; }
error() { echo -e "${RED}[moq]${NC} $*" >&2; exit 1; }

require() { command -v "$1" &>/dev/null || error "Required tool not found: $1"; }

# ─── Detect arch ──────────────────────────────────────────────────────────────
detect_arch() {
    case "$(uname -m)" in
        x86_64) echo "linux-x86_64" ;;
        *)      error "Unsupported architecture: $(uname -m). Only x86_64 is supported." ;;
    esac
}

# ─── Detect OS ────────────────────────────────────────────────────────────────
detect_os() {
    [[ -f /etc/os-release ]] || error "/etc/os-release not found — cannot determine OS"
    source /etc/os-release
    case "$ID" in
        ubuntu|debian)             echo "debian" ;;
        rhel|centos|fedora|almalinux|rockylinux) echo "rhel" ;;
        *)  error "Unsupported OS: $ID" ;;
    esac
}

# ─── Install OS dependencies ──────────────────────────────────────────────────
install_os_deps() {
    local os="$1"
    info "Installing OS dependencies for $os..."
    case "$os" in
        debian)
            sudo apt-get update -q
            sudo apt-get install -y -q unzip
            ;;
        rhel)
            sudo dnf install -y unzip 2>/dev/null || sudo yum install -y unzip
            ;;
    esac
}

# ─── Extract binaries from JAR ────────────────────────────────────────────────
extract_binaries() {
    local jar="$1"
    local arch="$2"
    local tmpdir
    tmpdir=$(mktemp -d)
    trap "rm -rf $tmpdir" EXIT

    info "Extracting binaries for $arch from $jar..."
    for bin in "${BINARIES[@]}"; do
        local jar_path="native/$arch/$bin"
        if ! unzip -q "$jar" "$jar_path" -d "$tmpdir" 2>/dev/null; then
            error "Binary not found in JAR: $jar_path (architecture not supported?)"
        fi
        local dest="$INSTALL_DIR/$bin"
        sudo install -m 755 "$tmpdir/$jar_path" "$dest"
        info "  Installed: $dest"
    done
}

# ─── Verify installed binaries ────────────────────────────────────────────────
verify_binaries() {
    info "Verifying installed binaries..."
    for bin in "${BINARIES[@]}"; do
        if "$INSTALL_DIR/$bin" --version &>/dev/null || "$INSTALL_DIR/$bin" --help &>/dev/null; then
            info "  OK: $bin"
        else
            warn "  $bin exited with non-zero (may be normal — check manually)"
        fi
    done
}

# ─── Deploy plugin JAR to AMS plugins dir ─────────────────────────────────────
deploy_plugin_jar() {
    local jar="$1"
    local dest="$PLUGINS_DIR/$PLUGIN_JAR"
    sudo mkdir -p "$PLUGINS_DIR"
    sudo install -m 644 "$jar" "$dest"
    if id antmedia &>/dev/null; then
        sudo chown antmedia:antmedia "$dest"
        info "Plugin JAR installed: $dest (owner: antmedia:antmedia)"
    else
        info "Plugin JAR installed: $dest"
    fi
}

# ─── Main ─────────────────────────────────────────────────────────────────────
main() {
    [[ "$EUID" -ne 0 ]] && [[ -z "$(command -v sudo)" ]] && error "sudo is required"

    require unzip 2>/dev/null || true  # will be installed below if missing

    # Find the plugin JAR — in PLUGINS_DIR, next to this script, or current dir
    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local jar=""
    for candidate in "$PLUGINS_DIR/$PLUGIN_JAR" "$script_dir/$PLUGIN_JAR" "./$PLUGIN_JAR"; do
        [[ -f "$candidate" ]] && { jar="$candidate"; break; }
    done
    [[ -z "$jar" ]] && error "$PLUGIN_JAR not found. Expected at $PLUGINS_DIR/$PLUGIN_JAR"

    local arch os
    arch=$(detect_arch)
    os=$(detect_os)

    install_os_deps "$os"
    extract_binaries "$jar" "$arch"
    verify_binaries
    deploy_plugin_jar "$jar"

    info "Done. moq-cli and moq-relay installed to $INSTALL_DIR, MoQPlugin.jar deployed to $PLUGINS_DIR."
}

main "$@"
