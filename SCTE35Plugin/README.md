# SCTE-35 Plugin for Ant Media Server

This plugin enables SCTE-35 cue point detection from SRT streams and converts them to HLS markers for ad insertion and broadcast automation workflows.

## Overview

The SCTE-35 Plugin automatically detects SCTE-35 messages embedded in SRT streams and injects appropriate HLS tags into M3U8 manifests. This enables seamless integration with Server-Side Ad Insertion (SSAI) services and broadcast automation systems.

## Features

- **Automatic SCTE-35 Detection**: Monitors SRT streams for SCTE-35 data packets
- **Real-time Processing**: Modifies M3U8 manifests in real-time as cue points are detected

## Installation

### 1. Deploy the Plugin JAR

Copy the SCTE35Plugin JAR file to your Ant Media Server's application plugins directory:
```bash
cp SCTE35Plugin.jar /usr/local/ant-media-server/plugins/
```

### 2. Configure the Servlet Filter

The plugin requires the `SCTE35ManifestModifierFilter` to be registered in your application's `web.xml` configuration file. This filter intercepts M3U8 manifest requests and injects SCTE-35 tags on the fly.

**Add the following to your application's `/usr/local/ant-media-server/webapps/AppName/WEB-INF/web.xml`** (after the `HlsManifestModifierFilter` entry):

```xml
<filter>
    <filter-name>SCTE35ManifestModifierFilter</filter-name>
    <filter-class>io.antmedia.scte35.SCTE35ManifestModifierFilter</filter-class>
    <async-supported>true</async-supported>
</filter>
<filter-mapping>
    <filter-name>SCTE35ManifestModifierFilter</filter-name>
    <url-pattern>/streams/*</url-pattern>
</filter-mapping>
```

**Important:** The filter should be placed **after** the `HlsManifestModifierFilter` entry in the filter chain to ensure correct execution order.

### 3. Restart Ant Media Server

After deploying the plugin and updating the web.xml configuration:
```bash
sudo systemctl restart antmedia
```

### 4. Verify Installation

Check the application logs to confirm the plugin and filter are properly initialized:
```bash
tail -f /path/to/ant-media-server/log/ant-media-server.log
```

You should see:
- `SCTE-35 Plugin initialized successfully`
- `SCTE35ManifestModifierFilter is properly registered in web.xml`

If the filter is not registered, you'll see a detailed error message with configuration instructions.

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

## Testing with AWS Elemental MediaTailor

You can test SCTE-35 ad insertion using Amazon MediaTailor:

### 1. Configure MediaTailor

Create a new AWS Elemental MediaTailor configuration:

- **Video content source**: `http://ServerIP/WebRTCAppEE/`
  - ⚠️ **Important**: Port must be **80** (HTTP), MediaTailor does not support custom ports
- **Ad Decision Server (ADS) URL**: Use Google's test ad server or your own VAST/VMAP endpoint

**Example ADS URL (Google DoubleClick test ads):**
```
https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/ad_rule_samples&ciu_szs=640x480&ad_rule=1&impl=s&gdfp_req=1&env=vp&output=vmap&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ar%3Dpremidpost&cmsid=496&vid=short_onecue&correlator=[avail.random]
```

- **Alternatively**, you can create and serve your own VAST/VMAP XML with custom ad videos. [See this post for more details](https://aws.amazon.com/blogs/media/build-your-own-vast-3-0-response-xml-to-test-with-aws-elemental-mediatailor/)

### 2. Play the Stream

Once configured, access your stream through MediaTailor's playback URL:

```
https://<mediatailor-hls-playback-prefix>/streams/<streamName>_adaptive.m3u8
```

Replace:
- `<mediatailor-hls-playback-prefix>`: Your MediaTailor HLS playback prefix from the configuration
- `<streamName>`: Your Ant Media Server stream name

### 3. Verify Ad Insertion

When a SCTE-35 cue-out event is detected in your SRT stream:
1. The plugin injects SCTE-35 markers into the HLS manifest
2. MediaTailor detects these markers
3. MediaTailor automatically replaces content with ads from your ADS
4. After the ad break duration expires (cue-in), content resumes

## Changelog

### Version 1.0.0
- Initial release
- Basic SCTE-35 detection and HLS tag injection

