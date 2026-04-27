#!/bin/sh
sudo systemctl stop antmedia
set -e
AMS_DIR=/usr/local/antmedia

SITE_PACKAGES="$("$AMS_DIR/pythonAIPlugin/bin/python3" -c 'import sysconfig; print(sysconfig.get_path("purelib"))')"
export PYTHONPATH="$SITE_PACKAGES"

mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi

cp ./target/PythonAIPlugin.jar $AMS_DIR/plugins/

[ -f $AMS_DIR/lib/jep-*.jar ] || cp ./target/lib/jep-*.jar $AMS_DIR/lib
[ -f $AMS_DIR/lib/jna-*.jar ] || cp ./target/lib/jna-*.jar $AMS_DIR/lib
cp ./target/lib/sqlite-jdbc-*.jar $AMS_DIR/lib/

./copy_files.sh

OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi
cd $AMS_DIR
./start-debug.sh
