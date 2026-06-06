#!/bin/sh
AMS_DIR=~/softwares/ant-media-server
ENTERPRISE_DIR=~/antmedia/Ant-Media-Enterprise

mvn -f "$ENTERPRISE_DIR/pom.xml" clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true -Djarsigner.skip=true
OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi

mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi

rm -r $AMS_DIR/plugins/MutedStreamReplicator*
cp target/MutedStreamReplicator.jar $AMS_DIR/plugins/

OUT=$?

if [ $OUT -ne 0 ]; then
    exit $OUT
fi
#./start-debug.sh
