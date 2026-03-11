#!/bin/bash


# To install latest snapshot version give --snapshot parameter
# sudo ./install_media-push-plugin.sh  --snapshot
# To install latest version just call directly
# sudo ./install_media-push-plugin.sh 



# Function to install Google Chrome on Debian-based systems
install_chrome_debian() {
    sudo apt-get update -y
    sudo apt-get install -y wget gnupg2
    wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb
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
releaseUrl="https://repo1.maven.org/maven2/io/antmedia/plugin/media-push/maven-metadata.xml"
# Snapshot metadata URL
snapshotBaseUrl="https://central.sonatype.com/repository/maven-snapshots/io/antmedia/plugin/media-push"
snapshotMetadataUrl="${snapshotBaseUrl}/maven-metadata.xml"

extract_first_tag_value() {
    local tag_name="$1"
    local file_path="$2"

    grep -o "<${tag_name}>[^<]*</${tag_name}>" "$file_path" | head -n 1 | sed "s#</\\?${tag_name}>##g"
}


REDIRECT="releases"

while [[ "$1" != "" ]]; do
    case $1 in
        --snapshot) REDIRECT="snapshots" ;;
        *) echo "Invalid option: $1" ; exit 1 ;;
    esac
    shift
done

if [ "$REDIRECT" = "snapshots" ]; then
    echo "Installing snapshot version..."
    wget -O maven-metadata.xml "$snapshotMetadataUrl"
    if [ $? -ne 0 ]; then
      echo "There is a problem in getting the version of the media push plugin."
      exit $?
    fi
else
    echo "Installing latest version..."
    # Attempt to download from the release URL
    wget -O maven-metadata.xml $releaseUrl -q
    # Check if wget failed (e.g., 404 error)
    if [ $? -ne 0 ]; then
        echo "Release URL failed (404). Trying the snapshot URL..."
        wget -O maven-metadata.xml "$snapshotMetadataUrl"
        if [ $? -ne 0 ]; then
            echo "There is a problem in getting the version of the media push plugin."
            exit $?
        fi
        REDIRECT="snapshots"
    fi
fi


if [ "$REDIRECT" = "snapshots" ]; then
    LATEST_VERSION=$(extract_first_tag_value "latest" "maven-metadata.xml")
    if [ -z "$LATEST_VERSION" ]; then
        LATEST_VERSION=$(extract_first_tag_value "version" "maven-metadata.xml")
    fi
    LATEST_VERSION="${LATEST_VERSION%-SNAPSHOT}"
else
    LATEST_VERSION=$(extract_first_tag_value "release" "maven-metadata.xml")
    if [ -z "$LATEST_VERSION" ]; then
        LATEST_VERSION=$(grep -o '<version>[^<]*</version>' maven-metadata.xml | tail -n 1 | sed 's/<\/\?version>//g')
    fi
fi

if [ -z "$LATEST_VERSION" ]; then
    echo "Latest media push plugin version could not be determined."
    exit 1
fi

#wget -O media-push.jar "https://repo1.maven.org/maven2/io/antmedia/plugin/media-push/${LATEST_VERSION}/media-push-${LATEST_VERSION}.jar"

if [ "$REDIRECT" = "snapshots" ]; then
    echo "Downloading snapshot build..."
    snapshotVersion="${LATEST_VERSION}-SNAPSHOT"
    snapshotVersionMetadataUrl="${snapshotBaseUrl}/${snapshotVersion}/maven-metadata.xml"

    wget -O snapshot-maven-metadata.xml "$snapshotVersionMetadataUrl"
    if [ $? -ne 0 ]; then
        echo "There is a problem in getting the snapshot artifact metadata of the media push plugin."
        exit $?
    fi

    SNAPSHOT_VALUE=$(grep -o '<value>[^<]*</value>' snapshot-maven-metadata.xml | tail -n 1 | sed 's/<\/\?value>//g')
    if [ -z "$SNAPSHOT_VALUE" ]; then
        echo "Snapshot artifact version could not be determined."
        exit 1
    fi

    wget -O media-push.jar "${snapshotBaseUrl}/${snapshotVersion}/media-push-${SNAPSHOT_VALUE}.jar"
else
    echo "Downloading stable release..."
    wget -O media-push.jar "https://repo1.maven.org/maven2/io/antmedia/plugin/media-push/${LATEST_VERSION}/media-push-${LATEST_VERSION}.jar"
fi

if [ $? -ne 0 ]; then
    echo "There is a problem in downloading the media push plugin. Please send the log of this console to support@antmedia.io"
    exit $?
fi

sudo mv media-push.jar /usr/local/antmedia/plugins/

# Check if the copy command was successful
if [ $? -eq 0 ]; then
	sudo chown antmedia:antmedia /usr/local/antmedia/plugins/media-push.jar
    echo "Media Push Plugin is installed successfully.Please restart the service to make it effective."
    echo "Run the command below to restart antmedia"
    echo "sudo service antmedia restart"
else
    echo "Media Push Plugin cannot be installed. Check the error above."
    exit $?;
fi
