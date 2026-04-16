#!/usr/bin/env bash
set -euo pipefail
AMS_DIR="${AMS_DIR:-/usr/local/antmedia}"

cp ./src/main/java/io/antmedia/app/*.py "$AMS_DIR/"

SITE_PACKAGES="$("$AMS_DIR/pythonAIPlugin/bin/python3" -c 'import sysconfig; print(sysconfig.get_path("purelib"))')"
export PYTHONPATH="$SITE_PACKAGES"

mkdir -p PythonPluginFiles 
cp -r ./src/main/java/io/antmedia/samples  "$AMS_DIR/PythonPluginFiles"
cp -r ./src/main/java/io/antmedia/samples/init_plugins.py  "$AMS_DIR/PythonPluginFiles"
cp ./src/main/java/io/antmedia/app/*.py   "$AMS_DIR/PythonPluginFiles"

cd $AMS_DIR

# ./start.sh
