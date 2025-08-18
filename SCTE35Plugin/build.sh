#!/bin/bash

# SCTE-35 Plugin Build Script
# This script only builds the plugin without deploying it

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}     SCTE-35 Plugin Build Script${NC}"
echo -e "${YELLOW}========================================${NC}"

echo -e "${YELLOW}Building SCTE-35 Plugin...${NC}"

# Clean and build the plugin
mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
BUILD_RESULT=$?

if [ $BUILD_RESULT -ne 0 ]; then
    echo -e "${RED}Build failed! Please check the error messages above.${NC}"
    exit $BUILD_RESULT
fi

echo -e "${GREEN}Build successful!${NC}"

# Check if the jar file was created
if [ -f "target/ant-media-scte35-plugin.jar" ]; then
    echo -e "${GREEN}✓ Plugin JAR created: target/ant-media-scte35-plugin.jar${NC}"
    
    # Show file size
    FILE_SIZE=$(ls -lh "target/ant-media-scte35-plugin.jar" | awk '{print $5}')
    echo -e "${GREEN}✓ Plugin size: $FILE_SIZE${NC}"
else
    echo -e "${RED}✗ Plugin JAR not found after build${NC}"
    exit 1
fi

echo -e "${YELLOW}========================================${NC}"
echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo -e "1. Use './redeploy.sh' to deploy to Ant Media Server"
echo -e "2. Or manually copy target/ant-media-scte35-plugin.jar to your AMS plugins directory"
echo "" 