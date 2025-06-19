# RTCP Stats Plugin

This plugin extracts metrics from [RTCP Sender Reports](https://datatracker.ietf.org/doc/html/rfc3550#section-6.4.1) and provides real-time streaming statistics.

## Requirements

- Ant Media Server **Enterprise** eddition
- Custom FFmpeg build (included in resources)

## Installation

1. **Build the project**:
   ```bash
   ./redeploy.sh
   ```

2. **Deploy the plugin**:
   ```bash
   cp ./target/rtcp-stats-plugin-*.jar /path/to/antmedia/plugins/
   ```

3. **Install custom FFmpeg build**:
   ```bash
   # Delete existing FFmpeg JARs from plugins (excluding platform JAR)
   rm /path/to/antmedia/plugins/ffmpeg-*-linux-x86_64.jar /path/to/antmedia/plugins/ffmpeg-[0-9]*.jar
   # Copy new FFmpeg binaries
   cp ./src/main/resources/ffmpeg/build/* /path/to/antmedia/plugins/
   ```

## Usage

Once installed, for every SRT broadcast, viewers receive status reports via data channel. Updates are sent on every status report received:

```json
{
 
 "command": "notification",
 
 "definition": "rtcpSr",
 
 "packetCount": 2080, # Packets count from SR
 
 "ntpTime": -1441529631683400689, # NTP timestamp from SR
  
 "receptionTime": 1381854143,  # Local timestamp (ms) at the moment the SR was received
 
 "trackIndex": 0, # Track index (SSRC field in SR)
 
 "pts": 2457581 # Presentation time of the packet on which SR was send 
}
```