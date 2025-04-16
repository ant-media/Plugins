#!/bin/sh
sudo systemctl stop antmedia
set -e
AMS_DIR=/usr/local/antmedia
mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi

cd ./src/main/python/
python3 setup.py build_ext --inplace
cd ../../../

cp ./src/main/python/libpythonWrapper.so $AMS_DIR/lib/native/
cp ./target/PythonPlugin.jar $AMS_DIR/plugins/

[ -f $AMS_DIR/lib/jna-*.jar ] || cp ./target/lib/jna-*.jar $AMS_DIR/lib

OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi
cd $AMS_DIR
./start-debug.sh
