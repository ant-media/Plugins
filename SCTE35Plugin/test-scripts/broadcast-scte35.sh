#!/bin/bash

# Broadcast SCTE-35 stream to Ant Media Server
# Usage: ./broadcast-scte35.sh [stream_file.ts] [stream_id]

STREAM_FILE="${1:-scte35-stream.ts}"
STREAM_ID="${2:-scte35-test}"
SRT_URL="srt://127.0.0.1:8080?streamid=WebRTCAppEE/${STREAM_ID}&mode=caller"

echo "Broadcasting SCTE-35 stream to Ant Media Server"
echo "File: $STREAM_FILE"
echo "Stream ID: $STREAM_ID"
echo "URL: $SRT_URL"
echo ""

# Check if file exists
if [ ! -f "$STREAM_FILE" ]; then
    echo "Error: Stream file '$STREAM_FILE' not found"
    echo "Create one first with: ./create-scte35-file.sh"
    exit 1
fi

echo "Starting broadcast... (Press Ctrl+C to stop)"

# Broadcast the stream
ffmpeg -re -i "$STREAM_FILE" \
       -c copy \
       -f mpegts \
       "$SRT_URL" 