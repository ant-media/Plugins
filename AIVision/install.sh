#!/bin/sh
set -eu

AMS_DIR=${AMS_DIR:-/usr/local/antmedia}
SERVICE_NAME=${SERVICE_NAME:-antmedia}
PACKAGE_URL=${AIVISION_PACKAGE_URL:-https://github.com/ant-media/Plugins/releases/download/aivision-latest/aivision.zip}
WORK_DIR=$(mktemp -d /tmp/aivision-install.XXXXXX)
PACKAGE_FILE="$WORK_DIR/aivision.zip"

cleanup() {
	rm -rf "$WORK_DIR"
}
trap cleanup EXIT

if [ ! -d "$AMS_DIR/plugins" ]; then
	echo "Missing Ant Media Server plugins directory: $AMS_DIR/plugins" >&2
	exit 1
fi

if [ ! -d "$AMS_DIR/webapps" ]; then
	echo "Missing Ant Media Server webapps directory: $AMS_DIR/webapps" >&2
	exit 1
fi

if ! command -v unzip >/dev/null 2>&1; then
	echo "unzip is required to install the AI Vision package." >&2
	exit 1
fi

if command -v curl >/dev/null 2>&1; then
	curl -fL "$PACKAGE_URL" -o "$PACKAGE_FILE"
elif command -v wget >/dev/null 2>&1; then
	wget -O "$PACKAGE_FILE" "$PACKAGE_URL"
else
	echo "curl or wget is required to download the AI Vision package." >&2
	exit 1
fi

unzip -q "$PACKAGE_FILE" -d "$WORK_DIR/package"

if [ ! -f "$WORK_DIR/package/aivision.jar" ]; then
	echo "Package does not contain aivision.jar" >&2
	exit 1
fi

if [ ! -f "$WORK_DIR/package/aivision.html" ]; then
	echo "Package does not contain aivision.html" >&2
	exit 1
fi

cp "$WORK_DIR/package/aivision.jar" "$AMS_DIR/plugins/aivision.jar"

FOUND_APP=0
for APP_DIR in "$AMS_DIR"/webapps/*; do
	if [ -d "$APP_DIR" ]; then
		cp "$WORK_DIR/package/aivision.html" "$APP_DIR/aivision.html"
		echo "AI Vision page installed to $APP_DIR/aivision.html"
		FOUND_APP=1
	fi
done

if [ "$FOUND_APP" -eq 0 ]; then
	echo "No application directories found under $AMS_DIR/webapps" >&2
	exit 1
fi

echo "AI Vision plugin installed to $AMS_DIR/plugins/aivision.jar"

if command -v systemctl >/dev/null 2>&1; then
	if systemctl restart "$SERVICE_NAME"; then
		echo "Restarted service: $SERVICE_NAME"
	else
		echo "Failed to restart service: $SERVICE_NAME" >&2
		exit 1
	fi
else
	echo "systemctl was not found. Restart Ant Media Server manually." >&2
fi
