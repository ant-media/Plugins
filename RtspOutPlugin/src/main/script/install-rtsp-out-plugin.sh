#!/bin/sh
set -eu

AMS_DIR=${AMS_DIR:-/usr/local/antmedia}
SERVICE_NAME=${SERVICE_NAME:-antmedia}
SRC_DIR=$(cd "$(dirname "$0")" && pwd)

if [ ! -d "$AMS_DIR/plugins" ]; then
	echo "Missing Ant Media Server plugins directory: $AMS_DIR/plugins" >&2
	exit 1
fi

for FILE in RtspOutPlugin.jar antmedia-mediamtx antmedia-mediamtx-LICENSE; do
	if [ ! -f "$SRC_DIR/$FILE" ]; then
		echo "$FILE is missing next to this script" >&2
		exit 1
	fi
done

# a new jar only loads on a restart, and stopping first also frees the running mediamtx binary
if command -v systemctl >/dev/null 2>&1; then
	systemctl stop "$SERVICE_NAME" || true
fi

# unlink rather than overwrite, copying onto a running binary gives "Text file busy"
# the last two are the pre-rename names, so upgrading does not strand a 55MB orphan
rm -f "$AMS_DIR/plugins/RtspOutPlugin.jar" "$AMS_DIR/plugins/antmedia-mediamtx" "$AMS_DIR/plugins/antmedia-mediamtx-LICENSE" \
      "$AMS_DIR/plugins/mediamtx" "$AMS_DIR/plugins/mediamtx-LICENSE"

install -m 0644 "$SRC_DIR/RtspOutPlugin.jar" "$AMS_DIR/plugins/RtspOutPlugin.jar"
install -m 0755 "$SRC_DIR/antmedia-mediamtx" "$AMS_DIR/plugins/antmedia-mediamtx"
install -m 0644 "$SRC_DIR/antmedia-mediamtx-LICENSE" "$AMS_DIR/plugins/antmedia-mediamtx-LICENSE"

echo "RTSP out plugin installed to $AMS_DIR/plugins"

if command -v systemctl >/dev/null 2>&1; then
	systemctl start "$SERVICE_NAME"
	echo "Started service: $SERVICE_NAME"
else
	echo "systemctl was not found, restart Ant Media Server manually" >&2
fi
