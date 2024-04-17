#!/bin/bash

# Function to install Google Chrome on Debian-based systems
install_chrome_debian() {
    sudo apt-get update -y
    sudo apt-get install -y wget gnupg2
    wget https://mirror.cs.uchicago.edu/google-chrome/pool/main/g/google-chrome-stable/google-chrome-stable_120.0.6099.199-1_amd64.deb
    sudo dpkg -i google-chrome-stable_current_amd64.deb
    sudo apt-get install -f -y
    rm google-chrome-stable_current_amd64.deb
}

# Function to install Google Chrome on Red Hat-based systems
install_chrome_redhat() {
    cat <<EOF | sudo tee /etc/yum.repos.d/google-chrome.repo
[google-chrome]
name=google-chrome
baseurl=http://dl.google.com/linux/chrome/rpm/stable/\$basearch
enabled=1
gpgcheck=1
gpgkey=https://dl.google.com/linux/linux_signing_key.pub
EOF
    sudo yum install -y google-chrome-stable
}

# Detect the Linux distribution
if [ -f /etc/os-release ]; then
    . /etc/os-release
    if [ "$ID" = "debian" ] || [ "$ID" = "ubuntu" ]; then
        install_chrome_debian
    elif [ "$ID" = "centos" ] || [ "$ID" = "fedora" ]; then
        install_chrome_redhat
    else
        echo "Unsupported Linux distribution: $ID"
        exit 1
    fi
else
    echo "Cannot detect the Linux distribution."
    exit 1
fi

echo "Google Chrome installation is complete."

# Release URL
releaseUrl="https://oss.sonatype.org/service/local/repositories/releases/content/io/antmedia/plugin/media-push/maven-metadata.xml"
# Snapshot URL
snapshotUrl="https://oss.sonatype.org/service/local/repositories/snapshots/content/io/antmedia/plugin/media-push/maven-metadata.xml"

REDIRECT="releases"
# Attempt to download from the release URL
wget -O maven-metadata.xml $releaseUrl -q

# Check if wget failed (e.g., 404 error)
if [ $? -ne 0 ]; then
    echo "Release URL failed (404). Trying the snapshot URL..."
    wget -O maven-metadata.xml $snapshotUrl
    REDIRECT="snapshots"
fi

export LATEST_VERSION=$(cat maven-metadata.xml | grep "<version>" | tail -n 1 |  xargs | cut -c 10-23)



wget -O media-push.jar "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=${REDIRECT}&g=io.antmedia.plugin&a=media-push&v=${LATEST_VERSION}&e=jar" -q

sudo cp media-push.jar /usr/local/antmedia/plugins/

# Check if the copy command was successful
if [ $? -eq 0 ]; then
    echo "Media Push Plugin is installed successfully. Restart the service to make it effective" 
    echo "sudo service antmedia restart"
else
    echo "Media Push Plugin cannot be installed. Check the error above."
fi
