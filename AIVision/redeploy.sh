#!/bin/sh
AMS_DIR=${AMS_DIR:-"$HOME/softwares/ant-media-server"}

mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi

cp target/aivision.jar "$AMS_DIR/plugins/"
OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi

cp src/main/resources/aivision.html "$AMS_DIR/webapps/live/"
OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi

echo "AI Vision plugin deployed to $AMS_DIR/plugins/aivision.jar"
echo "AI Vision page deployed to $AMS_DIR/webapps/live/aivision.html"
