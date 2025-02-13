#!/bin/sh
AMS_DIR=/home/usama/tem/ant-media-server/
mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi

cd ./src/main/python/
python3 setup.py build_ext --inplace
cd ../../../

cp ./src/main/python/libpythonWrapper.so $AMS_DIR/lib/native/

rm -r $AMS_DIR/plugins/PythonPlugin.jar
cp ./target/PythonPlugin.jar $AMS_DIR/plugins/

OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi
cd $AMS_DIR
./start-debug.sh
