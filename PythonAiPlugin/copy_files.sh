#!/usr/bin/env bash
set -euo pipefail
AMS_DIR="${AMS_DIR:-/usr/local/antmedia}"

cp ./src/main/java/io/antmedia/app/*.py "$AMS_DIR/"

cp ./web/samples/* $AMS_DIR/webapps/LiveApp/samples
cp ./web/samples/* $AMS_DIR/webapps/WebRTCAppEE/samples
cp ./web/samples/* $AMS_DIR/webapps/live/samples

#./start.sh
