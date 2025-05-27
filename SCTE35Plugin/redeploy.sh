#!/bin/bash

# SCTE-35 Plugin Redeploy Script for Ant Media Server
# This script builds and deploys the SCTE-35 plugin to your local Ant Media Server installation

# Configuration - Update these paths according to your setup
AMS_DIR=/usr/local/antmedia
PLUGIN_NAME="ant-media-scte35-plugin"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  SCTE-35 Plugin Deployment Script${NC}"
echo -e "${YELLOW}========================================${NC}"

# Check if AMS_DIR exists
if [ ! -d "$AMS_DIR" ]; then
    echo -e "${RED}Error: Ant Media Server directory not found at: $AMS_DIR${NC}"
    echo -e "${YELLOW}Please update the AMS_DIR variable in this script to point to your Ant Media Server installation${NC}"
    exit 1
fi

echo -e "${YELLOW}Building SCTE-35 Plugin...${NC}"

# Clean and build the plugin
mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
BUILD_RESULT=$?

if [ $BUILD_RESULT -ne 0 ]; then
    echo -e "${RED}Build failed! Please check the error messages above.${NC}"
    exit $BUILD_RESULT
fi

echo -e "${GREEN}Build successful!${NC}"

# Check if plugins directory exists
if [ ! -d "$AMS_DIR/plugins" ]; then
    echo -e "${RED}Error: Plugins directory not found at: $AMS_DIR/plugins${NC}"
    exit 1
fi

echo -e "${YELLOW}Removing old SCTE-35 plugin files...${NC}"

# Remove old plugin files
sudo rm -f $AMS_DIR/plugins/${PLUGIN_NAME}*
REMOVE_RESULT=$?

if [ $REMOVE_RESULT -ne 0 ]; then
    echo -e "${RED}Failed to remove old plugin files. Please check permissions.${NC}"
    exit $REMOVE_RESULT
fi

echo -e "${YELLOW}Copying new plugin to Ant Media Server...${NC}"

# Copy new plugin
cp target/${PLUGIN_NAME}.jar $AMS_DIR/plugins/
COPY_RESULT=$?

if [ $COPY_RESULT -ne 0 ]; then
    echo -e "${RED}Failed to copy plugin file. Please check permissions.${NC}"
    exit $COPY_RESULT
fi

echo -e "${GREEN}Plugin deployed successfully!${NC}"

# Check if the jar file was copied correctly
if [ -f "$AMS_DIR/plugins/${PLUGIN_NAME}.jar" ]; then
    echo -e "${GREEN}✓ Plugin file found at: $AMS_DIR/plugins/${PLUGIN_NAME}.jar${NC}"
    
    # Show file size
    FILE_SIZE=$(ls -lh "$AMS_DIR/plugins/${PLUGIN_NAME}.jar" | awk '{print $5}')
    echo -e "${GREEN}✓ Plugin size: $FILE_SIZE${NC}"
else
    echo -e "${RED}✗ Plugin file not found after copy operation${NC}"
    exit 1
fi

echo -e "${YELLOW}========================================${NC}"
echo -e "${GREEN}SCTE-35 Plugin deployment completed!${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo -e "1. Restart Ant Media Server to load the new plugin"
echo -e "2. Check the server logs for plugin initialization messages"
echo -e "3. Configure your SRT streams to include SCTE-35 data"
echo -e "4. Verify HLS manifests include SCTE-35 markers"
echo ""
echo -e "${YELLOW}Plugin Features:${NC}"
echo -e "• Automatic SCTE-35 detection from SRT streams"
echo -e "• Multiple HLS tag format support (EXT-X-CUE-OUT/IN, EXT-X-DATERANGE, etc.)"
echo -e "• Real-time M3U8 manifest modification"
echo -e "• Thread-safe multi-stream processing"
echo ""
echo -e "${YELLOW}For debugging, you can start Ant Media Server with:${NC}"
echo -e "./start-debug.sh"
echo ""

# Optional: Ask if user wants to restart AMS
read -p "Do you want to restart Ant Media Server now? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Restarting Ant Media Server...${NC}"
    
    # Stop AMS
    if [ -f "$AMS_DIR/stop.sh" ]; then
        cd "$AMS_DIR"
        sudo ./stop.sh
        sleep 3
        
        # Start AMS
        sudo ./start.sh
        echo -e "${GREEN}Ant Media Server restarted!${NC}"
    else
        echo -e "${RED}Could not find stop.sh script in $AMS_DIR${NC}"
        echo -e "${YELLOW}Please restart Ant Media Server manually${NC}"
    fi
fi

echo -e "${GREEN}Deployment script completed!${NC}" 