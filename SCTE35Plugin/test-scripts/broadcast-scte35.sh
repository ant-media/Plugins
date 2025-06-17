#!/bin/bash

# Broadcast SCTE-35 stream to Ant Media Server using srt-live-transmit
# Usage: ./broadcast-scte35.sh [stream_file.ts] [stream_id] [bitrate]
# Note: Uses srt-live-transmit instead of ffmpeg for proper SCTE-35 packet transmission

STREAM_FILE="${1:-scte35-stream.ts}"
STREAM_ID="${2:-scte35-test}"
BITRATE="${3:-19K}"
SRT_URL="srt://127.0.0.1:8080?streamid=WebRTCAppEE/${STREAM_ID}"

echo "Broadcasting SCTE-35 stream to Ant Media Server"
echo "File: $STREAM_FILE"
echo "Stream ID: $STREAM_ID"
echo "Bitrate: $BITRATE"
echo "URL: $SRT_URL"
echo ""

# Check if file exists
if [ ! -f "$STREAM_FILE" ]; then
    echo "Error: Stream file '$STREAM_FILE' not found"
    echo "Create one first with: ./create-scte35-file.sh"
    exit 1
fi

# Check if required tools are available
if ! command -v pv &> /dev/null; then
    echo "Error: 'pv' (pipe viewer) is required but not installed"
    echo "Install with: brew install pv (macOS) or apt-get install pv (Ubuntu)"
    exit 1
fi

if ! command -v srt-live-transmit &> /dev/null; then
    echo "Error: 'srt-live-transmit' is required but not installed"
    echo "Install SRT tools from: https://github.com/Haivision/srt"
    exit 1
fi

echo "Starting broadcast... (Press Ctrl+C to stop)"
echo "Using srt-live-transmit for proper SCTE-35 packet transmission"
echo ""

# Broadcast the stream using the recommended approach
cat "$STREAM_FILE" | pv -L "$BITRATE" | srt-live-transmit file://con "$SRT_URL" 