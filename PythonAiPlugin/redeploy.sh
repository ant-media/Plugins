#!/bin/sh
sudo systemctl stop antmedia
set -e
AMS_DIR=/usr/local/antmedia
mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi

cp ./target/PythonAIPlugin.jar $AMS_DIR/plugins/

[ -f $AMS_DIR/lib/jep-*.jar ] || cp ./target/lib/jep-*.jar $AMS_DIR/lib
[ -f $AMS_DIR/lib/jna-*.jar ] || cp ./target/lib/jna-*.jar $AMS_DIR/lib
cp ./target/lib/sqlite-jdbc-*.jar $AMS_DIR/lib/

cp ./src/main/java/io/antmedia/app/*.py $AMS_DIR/
mkdir -p "$AMS_DIR/webapps/LiveApp/viewer"
cp ./web/viewer/* "$AMS_DIR/webapps/LiveApp/viewer/"
cp ./web/samples/* $AMS_DIR/webapps/LiveApp/samples/



OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi
cd $AMS_DIR
./start-debug.sh
