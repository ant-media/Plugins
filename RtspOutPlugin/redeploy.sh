#!/bin/sh
AMS_DIR=~/softwares/ant-media-server
MEDIAMTX=src/main/native/linux-x86_64/mediamtx

if [ ! -x $MEDIAMTX ]; then
	echo "$MEDIAMTX missing, run ./fetch-mediamtx.sh first" >&2
	exit 1
fi

mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true \
	-Dassembly.skipAssembly=true -s mvn-settings.xml
if [ $? -ne 0 ]; then exit 1; fi

rm -f $AMS_DIR/plugins/RtspOutPlugin*
cp target/RtspOutPlugin.jar $AMS_DIR/plugins/

# The plugin resolves the binary from its own jar directory, so it runs from here.
cp $MEDIAMTX $AMS_DIR/plugins/
chmod +x $AMS_DIR/plugins/mediamtx
