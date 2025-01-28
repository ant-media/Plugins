#!/bin/sh
AMS_DIR=/usr/local/antmedia/
AMS_DIR=/home/usama/tem/ant-media-server/

# rm  src/main/resources/*.js

cd src/main/js
npm install
npm run build
cd ../../..

mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi

rm -r $AMS_DIR/plugins/media-push-plugin*
cp target/media-push-plugin.jar $AMS_DIR/plugins/

OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi
cd $AMS_DIR
# ./start-debug.sh
