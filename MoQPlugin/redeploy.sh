#!/bin/sh
AMS_DIR=~/softwares/ant-media-server

mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true -s mvn-settings.xml
if [ $? -ne 0 ]; then exit 1; fi

# Deploy the plugin itself
rm -f $AMS_DIR/plugins/MoQPlugin*
cp target/MoQPlugin.jar $AMS_DIR/plugins/
