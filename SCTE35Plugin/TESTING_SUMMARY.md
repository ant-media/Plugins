# SCTE-35 Plugin Testing Summary

## 🎯 Quick Start

You now have several options to test the SCTE-35 plugin functionality:

### 1. **Validation Check** (Start Here)
```bash
./test-scripts/validate-plugin.sh
```
**Purpose:** Verifies that all prerequisites are met and the plugin is properly installed.

### 2. **Quick Test** (Basic Functionality)
```bash
./test-scripts/quick-test.sh
```
**Purpose:** Runs a 1-minute test stream to verify basic plugin operation.

### 3. **Simple FFmpeg Test** (Extended Testing)
```bash
./test-scripts/create-scte35-test-stream.sh
```
**Purpose:** Creates a 5-minute test stream with simulated SCTE-35 timing.

### 4. **Advanced Python Test** (Realistic SCTE-35)
```bash
python3 test-scripts/advanced-scte35-test.py --duration 300
```
**Purpose:** Generates proper SCTE-35 binary data and injects it into the stream.

## 📋 Testing Workflow

### Step 1: Pre-flight Check
```bash
# 1. Validate environment
./test-scripts/validate-plugin.sh

# 2. Build and deploy plugin (if needed)
./redeploy.sh

# 3. Start Ant Media Server
# (Make sure AMS is running before testing)
```

### Step 2: Basic Testing
```bash
# Run quick test
./test-scripts/quick-test.sh

# Monitor logs in another terminal
tail -f /path/to/antmedia/logs/antmedia.log | grep -i scte35
```

### Step 3: Advanced Testing
```bash
# Test with real SCTE-35 data
python3 test-scripts/advanced-scte35-test.py \
    --stream-id "advanced-test" \
    --duration 180

# Check HLS output
curl http://localhost:5080/WebRTCAppEE/streams/advanced-test.m3u8
```

## 🔍 What to Look For

### In Logs
- `SCTE-35 Plugin initialized successfully`
- `SCTE-35 packet listener added successfully for stream: [stream-id]`
- `SCTE-35 message received for stream [stream-id]: type=CUE_OUT`
- `SCTE-35 metadata sent to HLS muxer for stream: [stream-id]`

### In HLS Manifests
```m3u8
#EXT-X-CUE-OUT:30.000
#EXT-X-CUE-IN
#EXT-X-DATERANGE:ID="1001",START-DATE="2024-01-15T10:30:00.000Z",PLANNED-DURATION=30.000
#EXT-X-SCTE35:CUE="/DA0AAAAAAAA///wBQb+cr0AUAAeAhxDVUVJSAAAjn/PAAGlwS..."
```

### Via REST API
```bash
# Check stream status
curl http://localhost:5080/WebRTCAppEE/rest/v2/broadcasts/[stream-id]

# Check if stream is live
curl http://localhost:5080/WebRTCAppEE/rest/v2/broadcasts/[stream-id]/broadcast-statistics
```

## 🛠️ Real-World Testing Options

### Option 1: Use Existing SCTE-35 Streams
If you have access to broadcast streams with SCTE-35:
```bash
# Relay existing stream to AMS
ffmpeg -i "your-scte35-stream-url" -c copy -f mpegts \
    "srt://127.0.0.1:8080?streamid=real-scte35-test"
```

### Option 2: Professional Tools
- **AWS Elemental MediaLive**: Configure SCTE-35 insertion and SRT output
- **Harmonic VOS**: Set up SCTE-35 encoding with SRT delivery
- **Wowza Streaming Engine**: Use SCTE-35 module with SRT publishing

### Option 3: SCTE-35 Injection Tools
- **Zixi Broadcaster**: Supports SCTE-35 insertion
- **Haivision Makito X**: Hardware encoder with SCTE-35 support
- **SRS (Simple Realtime Server)**: Can inject SCTE-35 markers

## 🚨 Troubleshooting Quick Reference

### Plugin Not Loading
```bash
# Check plugin file
ls -la /path/to/antmedia/plugins/ant-media-scte35-plugin.jar

# Check logs for errors
grep -i "error\|exception" /path/to/antmedia/logs/antmedia.log | grep -i scte35
```

### No SCTE-35 Detection
```bash
# Verify stream format
ffprobe srt://127.0.0.1:8080?streamid=your-stream

# Check if data packets are being processed
grep "onDataPacket" /path/to/antmedia/logs/antmedia.log
```

### HLS Tags Missing
```bash
# Check HLS muxer initialization
grep "HLSMuxer" /path/to/antmedia/logs/antmedia.log

# Verify metadata writing
grep "writeMetaData" /path/to/antmedia/logs/antmedia.log
```

## 📊 Performance Testing

### Load Testing
```bash
# Multiple concurrent streams
for i in {1..5}; do
    python3 test-scripts/advanced-scte35-test.py \
        --stream-id "load-test-$i" \
        --port $((8080 + i)) \
        --duration 300 &
done
```

### Memory Monitoring
```bash
# Monitor Java memory
watch -n 5 'jstat -gc $(pgrep -f antmedia)'

# Check for memory leaks
jmap -histo $(pgrep -f antmedia) | grep -i scte35
```

## 📚 Additional Resources

- **Full Testing Guide**: `test-scripts/TESTING_GUIDE.md`
- **Plugin Documentation**: `README.md`
- **Build Instructions**: `build.sh` and `redeploy.sh`
- **SCTE-35 Standard**: [SCTE-35 2019r1](https://www.scte.org/standards/library/)

## 🎉 Success Criteria

Your SCTE-35 plugin is working correctly if:

- ✅ Plugin loads without errors
- ✅ Streams are detected and processed
- ✅ SCTE-35 messages are parsed correctly
- ✅ HLS manifests contain SCTE-35 tags
- ✅ Timing is accurate (cue-out/cue-in)
- ✅ Multiple streams work concurrently
- ✅ No memory leaks during extended operation

---

**Happy Testing! 🚀**

For questions or issues, check the logs first, then refer to the detailed testing guide in `test-scripts/TESTING_GUIDE.md`. 