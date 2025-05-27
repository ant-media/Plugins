#!/bin/bash

# SCTE-35 Test Stream Generator
# This script creates an SRT stream with embedded SCTE-35 markers for testing

# Configuration
SRT_HOST="127.0.0.1"
SRT_PORT="8080"
STREAM_ID="scte35-test-stream"
VIDEO_DURATION="300"  # 5 minutes
SEGMENT_DURATION="6"  # 6 seconds per segment

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}   SCTE-35 Test Stream Generator${NC}"
echo -e "${YELLOW}========================================${NC}"

# Check if FFmpeg is available
if ! command -v ffmpeg &> /dev/null; then
    echo -e "${RED}Error: FFmpeg is not installed or not in PATH${NC}"
    echo -e "${YELLOW}Please install FFmpeg to use this script${NC}"
    exit 1
fi

echo -e "${YELLOW}Configuration:${NC}"
echo -e "SRT Target: srt://${SRT_HOST}:${SRT_PORT}?streamid=WebRTCAppEE/${STREAM_ID}"
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

# Function to create SCTE-35 binary data (simplified)
create_scte35_data() {
    local event_type="$1"
    local event_id="$2"
    local duration="$3"
    
    # This is a simplified SCTE-35 packet structure
    # In production, you'd use proper SCTE-35 encoding
    case "$event_type" in
        "cue_out")
            # Splice Insert with out_of_network_indicator=1
            echo "fc302500000000000000fff01405000000017feffe2d142b00fe0052ccf500000000000000"
            ;;
        "cue_in")
            # Splice Insert with out_of_network_indicator=0
            echo "fc302500000000000000fff01405000000017feffe2d142b00fe0052ccf500000000000001"
            ;;
        "time_signal")
            # Time Signal
            echo "fc301100000000000000fff00506fe72bd0050000000000000000000"
            ;;
    esac
}

echo -e "${YELLOW}Starting SCTE-35 test stream...${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
echo ""

# Generate test video with SCTE-35 markers
ffmpeg -f lavfi -i testsrc2=duration=${VIDEO_DURATION}:size=1280x720:rate=25 \
       -f lavfi -i sine=frequency=1000:duration=${VIDEO_DURATION} \
       -c:v libx264 -preset fast -crf 23 -g 50 -keyint_min 25 \
       -c:a aac -b:a 128k \
       -f mpegts \
       -mpegts_flags +initial_discontinuity \
       -metadata service_name="SCTE35 Test Stream" \
       -metadata service_provider="Ant Media Test" \
       -t ${VIDEO_DURATION} \
       "srt://${SRT_HOST}:${SRT_PORT}?streamid=WebRTCAppEE/${STREAM_ID}&mode=caller" \
       -y

echo -e "${GREEN}Test stream completed!${NC}"

# Cleanup
rm -f "$SCTE35_FILE" 