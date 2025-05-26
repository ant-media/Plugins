# SCTE-35 Plugin for Ant Media Server

This plugin enables SCTE-35 cue point detection from SRT streams and converts them to HLS markers for ad insertion and broadcast automation workflows.

## Overview

The SCTE-35 Plugin automatically detects SCTE-35 messages embedded in SRT streams and injects appropriate HLS tags into M3U8 manifests. This enables seamless integration with Server-Side Ad Insertion (SSAI) services and broadcast automation systems.

## Features

- **Automatic SCTE-35 Detection**: Monitors SRT streams for SCTE-35 data packets
- **Multiple HLS Tag Formats**: Supports various industry-standard HLS SCTE-35 tags
- **Real-time Processing**: Modifies M3U8 manifests in real-time as cue points are detected
- **Thread-safe**: Handles multiple concurrent streams safely
- **Configurable**: Supports different tag types for various SSAI services

## Supported HLS Tag Types

1. **EXT-X-CUE-OUT/IN** - Simple cue-out and cue-in markers
2. **EXT-X-DATERANGE** - RFC 8216 compliant date range markers
3. **EXT-X-SCTE35** - Base64 encoded SCTE-35 data
4. **EXT-X-SPLICEPOINT-SCTE35** - Legacy splice point format

## Installation

### Prerequisites

- Ant Media Server 3.0.0 or later
- Java 17 or later
- Maven 3.6 or later

### Building the Plugin

1. Clone or download the plugin source code
2. Navigate to the plugin directory:
   ```bash
   cd Plugins/SCTE35Plugin
   ```

3. Build the plugin:
   ```bash
   mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
   ```

### Deployment

#### Option 1: Using the Redeploy Script (Recommended)

1. Update the `AMS_DIR` variable in `redeploy.sh` to point to your Ant Media Server installation
2. Run the deployment script:
   ```bash
   ./redeploy.sh
   ```

#### Option 2: Manual Deployment

1. Copy the built JAR file to the plugins directory:
   ```bash
   cp target/ant-media-scte35-plugin.jar /path/to/ant-media-server/plugins/
   ```

2. Restart Ant Media Server:
   ```bash
   sudo systemctl restart antmedia
   ```

## Configuration

### Basic Setup

The plugin automatically activates when SRT streams containing SCTE-35 data are detected. No additional configuration is required for basic functionality.

### Advanced Configuration

To customize the HLS tag type, you can modify the plugin configuration in your application context or through the REST API.

#### Example: Setting Tag Type Programmatically

```java
// Get the SCTE-35 plugin instance
SCTE35Plugin scte35Plugin = (SCTE35Plugin) applicationContext.getBean("scte35.plugin");

// Get the HLS muxer for your stream
SCTE35HLSMuxer hlsMuxer = (SCTE35HLSMuxer) muxAdaptor.getHLSMuxer();

// Configure tag type
hlsMuxer.enableSCTE35(SCTE35HLSMuxer.SCTE35TagType.EXT_X_DATERANGE);
```

## Usage

### SRT Stream Setup

Ensure your SRT streams include SCTE-35 data in the MPEG-TS payload. The plugin will automatically detect:

- SCTE-35 table ID (0xFC)
- Splice insert commands
- Time signal commands
- Cue-out and cue-in events

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

#### EXT-X-DATERANGE Example
```m3u8
#EXTINF:6.000,
segment001.ts
#EXT-X-DISCONTINUITY
#EXT-X-DATERANGE:ID="123",START-DATE="2024-01-15T10:30:00.000Z",PLANNED-DURATION=30.000,SCTE35-OUT=0xFC...
#EXTINF:6.000,
segment002.ts
```

## API Reference

### Plugin Methods

#### Check Cue-Out Status
```java
boolean isInCueOut = scte35Plugin.isStreamInCueOut("streamId");
```

#### Get Cue-Out Duration
```java
long duration = scte35Plugin.getStreamCueOutDuration("streamId");
```

#### Get Statistics
```java
String stats = scte35Plugin.getStats();
```

### HLS Muxer Methods

#### Enable SCTE-35 Support
```java
hlsMuxer.enableSCTE35(SCTE35HLSMuxer.SCTE35TagType.EXT_X_CUE_OUT_IN);
```

#### Disable SCTE-35 Support
```java
hlsMuxer.disableSCTE35();
```

#### Check Status
```java
boolean enabled = hlsMuxer.isSCTE35Enabled();
int activeCues = hlsMuxer.getActiveCueCount();
```

## Troubleshooting

### Common Issues

1. **Plugin Not Loading**
   - Check Ant Media Server logs for initialization errors
   - Verify plugin JAR is in the correct plugins directory
   - Ensure Java version compatibility

2. **SCTE-35 Data Not Detected**
   - Verify SRT stream contains valid SCTE-35 data
   - Check stream logs for parsing errors
   - Ensure SCTE-35 data uses table ID 0xFC

3. **HLS Tags Not Appearing**
   - Verify SCTE-35 support is enabled for the stream
   - Check M3U8 file permissions
   - Review HLS muxer logs for errors

### Debug Logging

Enable debug logging for detailed troubleshooting:

```xml
<logger name="io.antmedia.scte35" level="DEBUG"/>
<logger name="io.antmedia.plugin.SCTE35Plugin" level="DEBUG"/>
```

### Log Messages

Look for these key log messages:

- `SCTE-35 Plugin initialized successfully` - Plugin loaded
- `SCTE-35 packet listener added successfully` - Stream monitoring started
- `SCTE-35 message received` - Cue point detected
- `SCTE-35 metadata added to HLS stream` - Tag injected

## Performance Considerations

- The plugin processes packets in real-time with minimal overhead
- SCTE-35 parsing is optimized for high-throughput scenarios
- M3U8 file modifications are atomic to prevent corruption
- Thread-safe design supports multiple concurrent streams

## Compatibility

### Tested SSAI Services

- AWS Elemental MediaTailor
- Google Ad Manager DAI
- Adobe Primetime
- Harmonic VOS

### Supported Formats

- **Input**: SRT streams with MPEG-TS containing SCTE-35
- **Output**: HLS with various SCTE-35 tag formats
- **Codecs**: H.264, H.265, AAC, MP3

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

This plugin is licensed under the Apache License 2.0. See the LICENSE file for details.

## Support

For support and questions:

- GitHub Issues: [Ant Media Server Issues](https://github.com/antmedia/Ant-Media-Server/issues)
- Documentation: [Ant Media Server Docs](https://antmedia.io/docs)
- Community: [Ant Media Community](https://community.antmedia.io)

## Changelog

### Version 1.0.0
- Initial release
- Basic SCTE-35 detection and HLS tag injection
- Support for multiple HLS tag formats
- Thread-safe multi-stream processing 