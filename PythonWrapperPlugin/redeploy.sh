#!/bin/sh
AMS_DIR=~/softwares/ant-media-server
mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi

rm -r $AMS_DIR/plugins/PythonWrapperApp*
cp target/PythonWrapperApp.jar $AMS_DIR/plugins/

OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi
sh ~/softwares/start.sh
