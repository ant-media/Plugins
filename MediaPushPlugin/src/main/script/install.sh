#!/bin/bash
# V2 install.sh for Media Push Plugin — installs Google Chrome (headless browser
# needed for pushing web pages as streams). AMS handles plugin.jar placement.
#
# AMS provides: AMS_HOME, AMS_PLUGINS_DIR, AMS_WEBAPPS_DIR,
#               PLUGIN_NAME, PLUGIN_VERSION, PLUGIN_JAR, PLUGIN_ID

set -e

SUDO="sudo"
if ! [ -x "$(command -v sudo)" ]; then SUDO=""; fi
if [ -f /.dockerenv ]; then SUDO=""; fi

# Skip if Chrome already installed
if command -v google-chrome >/dev/null 2>&1; then
    echo "Google Chrome is already installed — skipping"
    exit 0
fi

install_chrome_debian() {
    $SUDO apt-get update -y
    $SUDO apt-get install -y wget gnupg2
    wget -q https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb
    $SUDO dpkg -i google-chrome-stable_current_amd64.deb || true
    $SUDO apt-get install -f -y
    rm -f google-chrome-stable_current_amd64.deb
}

install_chrome_redhat() {
    cat <<EOF | $SUDO tee /etc/yum.repos.d/google-chrome.repo
[google-chrome]
name=google-chrome
baseurl=http://dl.google.com/linux/chrome/rpm/stable/\$basearch
enabled=1
gpgcheck=1
gpgkey=https://dl.google.com/linux/linux_signing_key.pub
EOF
    $SUDO yum install -y google-chrome-stable
}

if [ -f /etc/os-release ]; then
    . /etc/os-release
    case "$ID" in
        debian|ubuntu) install_chrome_debian ;;
        centos|fedora|rhel|almalinux|rockylinux) install_chrome_redhat ;;
        *) echo "Unsupported Linux distribution: $ID — skipping Chrome install" ;;
    esac
else
    echo "Cannot detect Linux distribution — skipping Chrome install"
fi

echo "Media Push Plugin install complete"
exit 0
