# SCTE-35 Plugin Testing Guide

This guide provides multiple approaches to test the SCTE-35 plugin functionality with Ant Media Server.

## 🎯 Testing Overview

The SCTE-35 plugin needs streams containing embedded SCTE-35 data to function. This guide covers:

1. **Simple FFmpeg Test Streams** - Basic testing without real SCTE-35 data
2. **Advanced Python Generator** - Creates proper SCTE-35 binary data
3. **Professional Tools** - Industry-standard SCTE-35 injection tools
4. **Real-world Streams** - Testing with actual broadcast content

## 🚀 Quick Start Testing

### Prerequisites

```bash
# Install required tools
sudo apt update
sudo apt install ffmpeg python3 python3-pip

# Make scripts executable
chmod +x test-scripts/*.sh
chmod +x test-scripts/*.py
```

### Method 1: Basic FFmpeg Test Stream

This creates a simple test stream that the plugin can monitor:

```bash
# Start the basic test stream
./test-scripts/create-scte35-test-stream.sh
```

**What it does:**
- Creates a 5-minute test video with audio
- Sends via SRT to `srt://127.0.0.1:8080?streamid=scte35-test-stream`
- Includes metadata that simulates SCTE-35 timing

### Method 2: Advanced Python Generator

For more realistic testing with actual SCTE-35 binary data:

```bash
# Generate stream with real SCTE-35 packets
python3 test-scripts/advanced-scte35-test.py --duration 300
```

**Features:**
- Generates proper SCTE-35 binary packets
- Includes splice_insert and time_signal commands
- Creates cue-out/cue-in sequences at specific timestamps

## 🔧 Testing Scenarios

### Scenario 1: Basic Plugin Functionality

**Goal:** Verify the plugin loads and processes streams

1. **Start Ant Media Server** with the plugin:
   ```bash
   cd /path/to/ant-media-server
   ./start.sh
   ```

2. **Check plugin loading** in logs:
   ```bash
   tail -f logs/antmedia.log | grep -i scte35
   ```
   Look for: `SCTE-35 Plugin initialized successfully`

3. **Start test stream**:
   ```bash
   ./test-scripts/create-scte35-test-stream.sh
   ```

4. **Verify stream processing**:
   ```bash
   # Check if stream is received
   curl http://localhost:5080/WebRTCAppEE/rest/v2/broadcasts/scte35-test-stream
   ```

### Scenario 2: HLS Output Verification

**Goal:** Verify SCTE-35 markers appear in HLS manifests

1. **Start stream** with HLS output enabled
2. **Monitor M3U8 files**:
   ```bash
   # Watch for SCTE-35 tags in HLS manifest
   watch -n 1 'curl -s http://localhost:5080/WebRTCAppEE/streams/scte35-test-stream.m3u8 | grep -E "(SCTE35|CUE|DATERANGE)"'
   ```

3. **Expected HLS tags**:
   ```m3u8
   #EXT-X-CUE-OUT:30.000
   #EXT-X-CUE-IN
   #EXT-X-DATERANGE:ID="1001",START-DATE="2024-01-15T10:30:00.000Z",PLANNED-DURATION=30.000
   ```

### Scenario 3: Real-time Monitoring

**Goal:** Monitor SCTE-35 events in real-time

1. **Enable debug logging**:
   ```xml
   <!-- Add to logback.xml -->
   <logger name="io.antmedia.scte35" level="DEBUG"/>
   ```

2. **Monitor logs**:
   ```bash
   tail -f logs/antmedia.log | grep -E "(SCTE-35|CUE-OUT|CUE-IN)"
   ```

3. **Use REST API** to check status:
   ```bash
   # Check cue-out status
   curl "http://localhost:5080/WebRTCAppEE/rest/v2/broadcasts/scte35-test-stream/scte35/status"
   ```

## 🛠️ Professional Testing Tools

### Option 1: Elemental Live/MediaLive

If you have access to AWS Elemental services:

```bash
# Configure Elemental Live to inject SCTE-35
# Set up SRT output to Ant Media Server
# Enable SCTE-35 passthrough
```

### Option 2: Harmonic VOS

For professional broadcast environments:

```bash
# Configure VOS encoder
# Enable SCTE-35 insertion
# Set SRT output target
```

### Option 3: Wowza Streaming Engine

Using Wowza as SCTE-35 source:

```bash
# Configure Wowza with SCTE-35 module
# Set up SRT publishing to AMS
# Schedule ad insertion events
```

## 📊 Validation Methods

### 1. Log Analysis

**Check for key messages:**

```bash
# Plugin initialization
grep "SCTE-35 Plugin initialized" logs/antmedia.log

# Stream processing
grep "SCTE-35 packet listener added" logs/antmedia.log

# Event detection
grep "SCTE-35 message received" logs/antmedia.log

# HLS injection
grep "SCTE-35 metadata added to HLS" logs/antmedia.log
```

### 2. HLS Manifest Inspection

**Verify HLS tags:**

```bash
# Download and inspect M3U8
curl -o test.m3u8 http://localhost:5080/WebRTCAppEE/streams/scte35-test-stream.m3u8

# Check for SCTE-35 tags
grep -E "(EXT-X-CUE|EXT-X-DATERANGE|EXT-X-SCTE35)" test.m3u8
```

### 3. REST API Testing

**Check plugin status:**

```bash
# Get stream info
curl "http://localhost:5080/WebRTCAppEE/rest/v2/broadcasts/scte35-test-stream"

# Check SCTE-35 status (if API endpoint exists)
curl "http://localhost:5080/WebRTCAppEE/rest/v2/broadcasts/scte35-test-stream/scte35/status"

# Get plugin statistics
curl "http://localhost:5080/WebRTCAppEE/rest/v2/plugins/scte35/stats"
```

## 🐛 Troubleshooting

### Common Issues

1. **Plugin Not Loading**
   ```bash
   # Check plugin file
   ls -la /path/to/ant-media-server/plugins/ant-media-scte35-plugin.jar
   
   # Check Java classpath
   grep -i scte35 logs/antmedia.log
   ```

2. **No SCTE-35 Detection**
   ```bash
   # Verify stream format
   ffprobe srt://127.0.0.1:8080?streamid=scte35-test-stream
   
   # Check packet processing
   grep "onDataPacket" logs/antmedia.log
   ```

3. **HLS Tags Missing**
   ```bash
   # Verify HLS muxer
   grep "HLSMuxer" logs/antmedia.log
   
   # Check metadata writing
   grep "writeMetaData" logs/antmedia.log
   ```

### Debug Commands

```bash
# Monitor network traffic
sudo tcpdump -i any port 8080 -w scte35-test.pcap

# Analyze MPEG-TS stream
ffprobe -show_packets -select_streams d srt://127.0.0.1:8080?streamid=scte35-test-stream

# Check Java heap and threads
jstack $(pgrep -f antmedia)
```

## 📈 Performance Testing

### Load Testing

```bash
# Multiple concurrent streams
for i in {1..10}; do
    python3 test-scripts/advanced-scte35-test.py \
        --stream-id "scte35-test-$i" \
        --port $((8080 + i)) \
        --duration 600 &
done
```

### Memory Monitoring

```bash
# Monitor Java memory usage
watch -n 5 'jstat -gc $(pgrep -f antmedia)'

# Check plugin memory usage
jmap -histo $(pgrep -f antmedia) | grep -i scte35
```

## 🎬 Sample Test Streams

### Test Stream URLs

If you have access to test streams with SCTE-35:

```bash
# Example test streams (replace with actual URLs)
SCTE35_TEST_STREAM_1="srt://test-server.example.com:1935?streamid=scte35-test-1"
SCTE35_TEST_STREAM_2="srt://test-server.example.com:1935?streamid=scte35-test-2"

# Relay to Ant Media Server
ffmpeg -i "$SCTE35_TEST_STREAM_1" -c copy -f mpegts "srt://127.0.0.1:8080?streamid=relay-test-1"
```

### Creating Custom Test Content

```bash
# Create video with timecode overlay
ffmpeg -f lavfi -i testsrc2=duration=300:size=1280x720:rate=25 \
       -vf "drawtext=text='%{pts\:hms}':x=10:y=10:fontsize=24:fontcolor=white" \
       -f lavfi -i sine=frequency=1000:duration=300 \
       -c:v libx264 -c:a aac \
       -f mpegts "srt://127.0.0.1:8080?streamid=timecode-test"
```

## 📋 Test Checklist

- [ ] Plugin loads successfully
- [ ] Stream is received and processed
- [ ] SCTE-35 packets are detected
- [ ] HLS manifests contain SCTE-35 tags
- [ ] Cue-out/cue-in timing is accurate
- [ ] Multiple concurrent streams work
- [ ] No memory leaks during long runs
- [ ] Error handling works correctly
- [ ] REST API responses are correct
- [ ] Log messages are informative

## 🔗 Additional Resources

- [SCTE-35 Standard Documentation](https://www.scte.org/standards/library/)
- [HLS SCTE-35 Guidelines](https://tools.ietf.org/html/rfc8216#section-4.3.2.7)
- [FFmpeg SCTE-35 Support](https://ffmpeg.org/ffmpeg-formats.html#mpegts)
- [Ant Media Server Documentation](https://antmedia.io/docs)

---

**Note:** This testing guide assumes you have the SCTE-35 plugin properly installed and Ant Media Server running. Adjust paths and URLs according to your specific setup. 