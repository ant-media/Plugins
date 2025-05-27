#!/bin/bash

# SCTE-35 Test Stream File Generator
# This script creates a test stream file with embedded SCTE-35 markers for testing

# Configuration
STREAM_ID="scte35-test-stream"
VIDEO_DURATION="300"  # 5 minutes
SEGMENT_DURATION="6"  # 6 seconds per segment
OUTPUT_DIR="./test-streams"
OUTPUT_FILE="${OUTPUT_DIR}/${STREAM_ID}.ts"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}   SCTE-35 Test Stream File Generator${NC}"
echo -e "${YELLOW}========================================${NC}"

# Check if FFmpeg is available
if ! command -v ffmpeg &> /dev/null; then
    echo -e "${RED}Error: FFmpeg is not installed or not in PATH${NC}"
    echo -e "${YELLOW}Please install FFmpeg to use this script${NC}"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo -e "${YELLOW}Configuration:${NC}"
echo -e "Output File: ${OUTPUT_FILE}"
echo -e "Duration: ${VIDEO_DURATION} seconds"
echo -e "Segment Duration: ${SEGMENT_DURATION} seconds"
echo ""

# Create SCTE-35 metadata file for ad breaks
SCTE35_FILE="/tmp/scte35_markers.txt"
echo -e "${YELLOW}Creating SCTE-35 marker file...${NC}"

cat > "$SCTE35_FILE" << 'EOF'
# SCTE-35 Markers for Testing
# Format: timestamp,type,event_id,duration
# Types: cue_out, cue_in, time_signal

30,cue_out,1001,30
60,cue_in,1001,0
120,cue_out,1002,60
180,cue_in,1002,0
240,time_signal,1003,0
EOF

echo -e "${GREEN}✓ SCTE-35 marker file created: $SCTE35_FILE${NC}"

echo -e "${YELLOW}Generating SCTE-35 test stream file...${NC}"
echo -e "${YELLOW}This may take a few minutes for a ${VIDEO_DURATION}-second video...${NC}"
echo ""

# Generate test video with SCTE-35 markers and save to file
ffmpeg -f lavfi -i testsrc2=duration=${VIDEO_DURATION}:size=1280x720:rate=25 \
       -f lavfi -i sine=frequency=1000:duration=${VIDEO_DURATION} \
       -c:v libx264 -preset fast -crf 23 -g 50 -keyint_min 25 \
       -c:a aac -b:a 128k \
       -f mpegts \
       -mpegts_flags +initial_discontinuity \
       -metadata service_name="SCTE35 Test Stream" \
       -metadata service_provider="Ant Media Test" \
       -t ${VIDEO_DURATION} \
       "$OUTPUT_FILE" \
       -y

RESULT=$?

if [ $RESULT -eq 0 ] && [ -f "$OUTPUT_FILE" ]; then
    FILE_SIZE=$(ls -lh "$OUTPUT_FILE" | awk '{print $5}')
    echo ""
    echo -e "${GREEN}✓ Test stream file created successfully!${NC}"
    echo -e "${GREEN}  File: $OUTPUT_FILE${NC}"
    echo -e "${GREEN}  Size: $FILE_SIZE${NC}"
    echo ""
    
    # Show file info
    echo -e "${YELLOW}File Information:${NC}"
    ffprobe -v quiet -show_format -show_streams "$OUTPUT_FILE" 2>/dev/null | grep -E "(duration|bit_rate|codec_name|width|height)"
    
    echo ""
    echo -e "${YELLOW}Usage Options:${NC}"
    echo -e "1. Stream to Ant Media Server via SRT:"
    echo -e "   ffmpeg -re -i \"$OUTPUT_FILE\" -c copy -f mpegts \"srt://127.0.0.1:8080?streamid=WebRTCAppEE/${STREAM_ID}&mode=caller\""
    echo ""
    echo -e "2. Stream to Ant Media Server via RTMP:"
    echo -e "   ffmpeg -re -i \"$OUTPUT_FILE\" -c copy -f flv \"rtmp://127.0.0.1:1935/WebRTCAppEE/${STREAM_ID}\""
    echo ""
    echo -e "3. Loop the stream continuously:"
    echo -e "   ffmpeg -re -stream_loop -1 -i \"$OUTPUT_FILE\" -c copy -f mpegts \"srt://127.0.0.1:8080?streamid=WebRTCAppEE/${STREAM_ID}&mode=caller\""
    echo ""
    echo -e "4. Play locally with VLC or ffplay:"
    echo -e "   ffplay \"$OUTPUT_FILE\""
    echo ""
    
else
    echo -e "${RED}✗ Failed to create test stream file${NC}"
    echo -e "${RED}Exit code: $RESULT${NC}"
fi

# Cleanup
rm -f "$SCTE35_FILE"

echo -e "${GREEN}File generation completed!${NC}" 