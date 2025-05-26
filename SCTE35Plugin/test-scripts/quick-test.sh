#!/bin/bash

# Quick SCTE-35 Plugin Test
# This script runs a quick test to verify basic plugin functionality

# Configuration
TEST_DURATION=60  # 1 minute test
STREAM_ID="quick-scte35-test"
SRT_PORT="8080"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}🚀 Quick SCTE-35 Plugin Test${NC}"
echo -e "${YELLOW}=============================${NC}"
echo ""

# Check if FFmpeg is available
if ! command -v ffmpeg &> /dev/null; then
    echo -e "${RED}❌ FFmpeg not found. Please install FFmpeg first.${NC}"
    exit 1
fi

echo -e "${YELLOW}📡 Starting test stream for ${TEST_DURATION} seconds...${NC}"
echo -e "Stream ID: ${STREAM_ID}"
echo -e "SRT Target: srt://127.0.0.1:${SRT_PORT}?streamid=${STREAM_ID}"
echo ""

# Create a simple test stream with metadata
ffmpeg -hide_banner -loglevel warning \
    -f lavfi -i testsrc2=duration=${TEST_DURATION}:size=640x480:rate=25 \
    -f lavfi -i sine=frequency=440:duration=${TEST_DURATION} \
    -c:v libx264 -preset ultrafast -crf 28 -g 25 \
    -c:a aac -b:a 64k \
    -f mpegts \
    -metadata service_name="SCTE35 Quick Test" \
    -metadata service_provider="Ant Media Test" \
    "srt://127.0.0.1:${SRT_PORT}?streamid=${STREAM_ID}&mode=caller" &

FFMPEG_PID=$!

echo -e "${GREEN}✅ Test stream started (PID: $FFMPEG_PID)${NC}"
echo ""
echo -e "${YELLOW}💡 While the stream is running, you can:${NC}"
echo -e "1. Check Ant Media Server logs:"
echo -e "   tail -f /path/to/antmedia/logs/antmedia.log | grep -i scte35"
echo ""
echo -e "2. Monitor the stream via REST API:"
echo -e "   curl http://localhost:5080/WebRTCAppEE/rest/v2/broadcasts/${STREAM_ID}"
echo ""
echo -e "3. Check HLS output (if available):"
echo -e "   curl http://localhost:5080/WebRTCAppEE/streams/${STREAM_ID}.m3u8"
echo ""

# Wait for the stream to complete
wait $FFMPEG_PID
FFMPEG_EXIT_CODE=$?

echo ""
if [ $FFMPEG_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ Test stream completed successfully!${NC}"
else
    echo -e "${RED}❌ Test stream failed with exit code: $FFMPEG_EXIT_CODE${NC}"
fi

echo ""
echo -e "${YELLOW}📋 Test Summary:${NC}"
echo -e "• Stream Duration: ${TEST_DURATION} seconds"
echo -e "• Stream ID: ${STREAM_ID}"
echo -e "• Exit Code: $FFMPEG_EXIT_CODE"
echo ""
echo -e "${YELLOW}🔍 To verify plugin functionality:${NC}"
echo -e "1. Check if the plugin detected the stream"
echo -e "2. Look for SCTE-35 related log messages"
echo -e "3. Verify HLS output contains expected tags"
echo "" 