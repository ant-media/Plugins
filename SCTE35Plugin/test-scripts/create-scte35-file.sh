#!/bin/bash

# Create TS file with SCTE-35 cues
# Usage: ./create-scte35-file.sh [output_file.ts]

OUTPUT_FILE="${1:-scte35-stream.ts}"
BASE_FILE="base-stream.ts"

echo "Creating SCTE-35 stream file: $OUTPUT_FILE"

# Step 1: Create base TS file
echo "1. Creating base stream..."
ffmpeg -f lavfi -i testsrc2=duration=300:size=1280x720:rate=25 \
       -f lavfi -i sine=frequency=1000:duration=300 \
       -c:v libx264 -preset fast -crf 23 -g 50 \
       -c:a aac -b:a 128k \
       -f mpegts \
       -t 300 \
       "$BASE_FILE" \
       -y

# Step 2: Add SCTE-35 cues
echo "2. Adding SCTE-35 cues..."
./add-scte35-cues.sh "$BASE_FILE" "$OUTPUT_FILE"

# Cleanup
rm -f "$BASE_FILE"

echo "✓ SCTE-35 file created: $OUTPUT_FILE" 