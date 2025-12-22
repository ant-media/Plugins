#!/bin/bash

# Simple SCTE-35 Cue Injection Script
# Takes an existing TS file and adds SCTE-35 cue points

if [ $# -eq 0 ]; then
    echo "Usage: $0 <input.ts> [output.ts]"
    echo "Example: $0 input.ts output_with_cues.ts"
    exit 1
fi

INPUT_FILE="$1"
OUTPUT_FILE="${2:-output_with_cues.ts}"

# Check if input file exists
if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: Input file '$INPUT_FILE' not found"
    exit 1
fi

# Check if TSDuck is available
if ! command -v tsp &> /dev/null; then
    echo "Error: TSDuck is not installed"
    echo "Install with: brew install tsduck"
    exit 1
fi

echo "Adding SCTE-35 cues to: $INPUT_FILE"
echo "Output file: $OUTPUT_FILE"

# Run the TSDuck command exactly as specified
tsp -I file "$INPUT_FILE" \
    -P pmt --service 1 \
          --add-programinfo-id 0x43554549 \
          --add-pid 0x258/0x86 \
    -P spliceinject --service 1 \
                    --files "../cue-*.xml" \
    -O file "$OUTPUT_FILE"

if [ $? -eq 0 ]; then
    echo "✓ SCTE-35 cues added successfully to: $OUTPUT_FILE"
else
    echo "✗ Failed to add SCTE-35 cues"
    exit 1
fi 