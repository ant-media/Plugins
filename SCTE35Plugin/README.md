# SCTE-35 Plugin for Ant Media Server

This plugin enables SCTE-35 cue point detection from SRT streams and converts them to HLS markers for ad insertion and broadcast automation workflows.

## Overview

The SCTE-35 Plugin automatically detects SCTE-35 messages embedded in SRT streams and injects appropriate HLS tags into M3U8 manifests. This enables seamless integration with Server-Side Ad Insertion (SSAI) services and broadcast automation systems.

## Features

- **Automatic SCTE-35 Detection**: Monitors SRT streams for SCTE-35 data packets
- **Real-time Processing**: Modifies M3U8 manifests in real-time as cue points are detected

## Usage

### SRT Stream Setup

Ensure your SRT streams include SCTE-35 data in the MPEG-TS payload. The plugin will automatically detect:

- SCTE-35 table ID (0xFC)
- Splice insert commands
- Time signal commands
- Cue-out and cue-in events

### Important Notes on Stream Sources

#### FFMPEG Limitations
During testing, FFMPEG was found to have limitations when transmitting SCTE-35 packets via SRT. While FFMPEG appears to process SCTE-35 data correctly, the packets may not be properly transmitted to the server side, resulting in failed detection.

#### Recommended Alternative Approach
For reliable SCTE-35 transmission, use alternative tools that can properly handle SCTE-35 data in SRT streams.

Download stream with scte35 here: https://drive.google.com/file/d/1fVERcuoeaZSz4VMJEZBjpiRN0_F8Ytpm/view?usp=drive_link

**Example using srt-live-transmit:**
```bash
# Stream a transport stream file with SCTE-35 data at controlled bitrate
cat scte35_spliceInsert_2hour_demo.ts | pv -L 19K | srt-live-transmit file://con "srt://your-server:4200?streamid=WebRTCAppEE/your_stream"
```

Replace the following parameters in the example above:
- `scte35_spliceInsert_2hour_demo.ts`: Your MPEG-TS file containing SCTE-35 data
- `19K`: Desired bitrate (adjust based on your content)
- `your-server`: Your Ant Media Server hostname or IP
- `4200`: SRT port (default is 4200)
- `your_stream`: Your target stream name

### HLS Output

The plugin will inject appropriate tags into your HLS manifests:

#### EXT-X-CUE-OUT/IN Example
```m3u8
#EXTINF:6.000,
segment001.ts
#EXT-X-DISCONTINUITY
#EXT-X-CUE-OUT:30.000
#EXTINF:6.000,
segment002.ts
```

## Changelog

### Version 0.1.0
- Initial release
- Basic SCTE-35 detection and HLS tag injection
