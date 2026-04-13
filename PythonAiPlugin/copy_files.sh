#!/usr/bin/env bash
set -euo pipefail
AMS_DIR="${AMS_DIR:-/usr/local/antmedia}"

cp ./src/main/java/io/antmedia/app/*.py "$AMS_DIR/"

cp ./web/samples/* $AMS_DIR/webapps/LiveApp/samples
cp ./web/samples/* $AMS_DIR/webapps/WebRTCAppEE/samples
cp ./web/samples/* $AMS_DIR/webapps/live/samples

mkdir -p PythonPluginFiles 
cp -r ./src/main/java/io/antmedia/samples  "$AMS_DIR/PythonPluginFiles"
cp -r ./src/main/java/io/antmedia/samples/init_plugins.py  "$AMS_DIR/PythonPluginFiles"
cp ./src/main/java/io/antmedia/app/*.py   "$AMS_DIR/PythonPluginFiles"

cd $AMS_DIR

# ./start.sh
