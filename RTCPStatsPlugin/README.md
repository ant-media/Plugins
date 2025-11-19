# RTCP Stats Plugin

This plugin extracts metrics from [RTCP Sender Reports](https://datatracker.ietf.org/doc/html/rfc3550#section-6.4.1) and provides real-time streaming statistics with **frame-level NTP timestamp interpolation**.

## Features

- **Frame-level timing interpolation**: Provides precise NTP timestamps for every video/audio frame by interpolating between RTCP Sender Reports
- **Automatic clock rate detection**: Dynamically detects RTP clock rates from RTCP data

## Important Notes

- The plugin requires at least **two RTCP Sender Reports** before interpolation begins. This may cause some delay from the start of the stream until timing data becomes available 
- Sender Reports must be spaced at least **1.0 seconds** apart for reliable clock rate detection
- `ntpTime` values may appear negative due to Java's signed long representation - handle as unsigned 64-bit values in frontend

## Requirements

- Ant Media Server **Enterprise** edition
- Custom FFmpeg build (included in resources)
- **Important**: Time between RTCP Sender Reports must be at least **1.0 second** for accurate interpolation

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

Once installed, for every SRT broadcast, viewers receive interpolated timing data via data channel. The plugin waits for at least **two RTCP Sender Reports** (spaced 1+ seconds apart) to detect clock rates, then provides frame-level interpolated NTP timestamps for every packet:

```json
{
  "EVENT_TYPE": "rtcpSr",
  "trackIndex": 0,
  "pts": 2457581,
  "ntpTime": -1441529631683400689,
  "srReceptionTime": 1381854143,
  "srNtpTime": -1441529631683400689
}
```

**Field descriptions:**
- `EVENT_TYPE`: Always "rtcpSr"
- `trackIndex`: Stream index (typically but not guaranted, 0 = video, 1+ = audio)  
- `pts`: Presentation timestamp of the current packet
- `ntpTime`: **Interpolated** NTP timestamp for this specific frame/packet
- `srReceptionTime`: RTP timestamp from the most recent RTCP Sender Report
- `srNtpTime`: Original NTP timestamp from the most recent RTCP Sender Report

### Usage with JS SDK
To access this data in JS-SDK, follow [example](https://github.com/ant-media/StreamApp/blob/93aba178622b72475d6be414eb71d09462149398/src/main/webapp/samples/publish_webrtc.html#L683), and handle events on WebRTCAdatptor callback.

```javascript
// NTP timestamp parser function
function simpleNtpParse(ntpTimeString) {
    let ntpTime = BigInt(ntpTimeString);
    if (ntpTime < 0n) {
        ntpTime = ntpTime + (1n << 64n); 
    }
    
    return {
        seconds: Number(ntpTime >> 32n),
        fraction: Number(ntpTime & 0xFFFFFFFFn)
    };
}

new WebRTCAdaptor({
   .........
   .........
   dataChannelEnabled: true,
   callback: (info, obj) => {
      if (info === "data_received") {
         try {
            let notificationEvent = JSON.parse(data);
            if(notificationEvent != null && typeof(notificationEvent) == "object") {
                let eventType = notificationEvent.EVENT_TYPE;
                if (eventType == "rtcpSr") {
                    // We have interpolated frame timing data here!
                    let ntpParts = simpleNtpParse(notificationEvent.ntpTime);
                    console.log(`Track ${notificationEvent.trackIndex}: PTS=${notificationEvent.pts}, NTP=${notificationEvent.ntpTime}`);
                    console.log(`NTP Seconds: ${ntpParts.seconds}, Fraction: ${ntpParts.fraction}`);
                }
            }
         } catch (exception) {
            $("#all-messages").append("Received: " + data + "<br>");
         }
      }
   }
```








