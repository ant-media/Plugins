# SCTE-35 Plugin Troubleshooting Guide

## 🚨 Common Issues and Solutions

### 1. **Validation Script Errors**

#### Issue: "404 Not Found" errors during validation
```bash
✗ FAILED: Plugin REST endpoints accessible
```

**Solution:** This is now fixed. The validation script was trying to access non-existent endpoints:
- ❌ `/rest/v2/plugins` (doesn't exist)
- ❌ `/rest/v2/broadcasts` (doesn't exist)
- ✅ `/rest/v2/broadcasts/count` (correct endpoint)

**Action:** Use the updated validation script:
```bash
./test-scripts/validate-plugin.sh
```

### 2. **Plugin Not Loading**

#### Issue: Plugin JAR not found or not loading
```bash
✗ FAILED: Plugin JAR file exists
```

**Solutions:**
1. **Check plugin location:**
   ```bash
   # Check if plugin exists
   ls -la /path/to/antmedia/plugins/ant-media-scte35-plugin.jar
   
   # Common locations:
   ls -la /opt/antmedia/plugins/
   ls -la /usr/local/antmedia/plugins/
   ```

2. **Rebuild and redeploy:**
   ```bash
   ./redeploy.sh
   ```

3. **Check permissions:**
   ```bash
   sudo chown antmedia:antmedia /path/to/antmedia/plugins/ant-media-scte35-plugin.jar
   sudo chmod 644 /path/to/antmedia/plugins/ant-media-scte35-plugin.jar
   ```

### 3. **Ant Media Server Connection Issues**

#### Issue: Cannot connect to Ant Media Server
```bash
✗ FAILED: Ant Media Server is running
✗ FAILED: REST API is accessible
```

**Solutions:**
1. **Check if AMS is running:**
   ```bash
   # Check process
   ps aux | grep antmedia
   
   # Check port
   netstat -tlnp | grep 5080
   ```

2. **Start Ant Media Server:**
   ```bash
   cd /path/to/antmedia
   sudo ./start.sh
   ```

3. **Check configuration:**
   ```bash
   # Update validation script if using different host/port
   export AMS_HOST="your-server-ip"
   export AMS_PORT="your-port"
   ```

### 4. **SCTE-35 Detection Issues**

#### Issue: Plugin loads but doesn't detect SCTE-35 data
```bash
# No SCTE-35 messages in logs
tail -f /path/to/antmedia/logs/antmedia.log | grep -i scte35
```

**Solutions:**
1. **Verify stream contains SCTE-35:**
   ```bash
   # Check stream with FFprobe
   ffprobe -show_packets -select_streams d srt://127.0.0.1:8080?streamid=your-stream
   ```

2. **Check packet listener registration:**
   ```bash
   # Look for registration messages
   grep "SCTE-35 packet listener added" /path/to/antmedia/logs/antmedia.log
   ```

3. **Test with known SCTE-35 stream:**
   ```bash
   # Use the advanced test generator
   python3 test-scripts/advanced-scte35-test.py --duration 60
   ```

### 5. **HLS Output Issues**

#### Issue: No SCTE-35 tags in HLS manifests
```bash
# Check M3U8 file
curl http://localhost:5080/WebRTCAppEE/streams/your-stream.m3u8 | grep -E "(SCTE35|CUE|DATERANGE)"
```

**Solutions:**
1. **Verify HLS muxer initialization:**
   ```bash
   grep "SCTE35HLSMuxer" /path/to/antmedia/logs/antmedia.log
   ```

2. **Check metadata writing:**
   ```bash
   grep "SCTE-35 metadata sent to HLS" /path/to/antmedia/logs/antmedia.log
   ```

3. **Enable debug logging:**
   ```xml
   <!-- Add to logback.xml -->
   <logger name="io.antmedia.scte35" level="DEBUG"/>
   ```

### 6. **Build and Deployment Issues**

#### Issue: Maven build fails
```bash
[ERROR] Failed to execute goal
```

**Solutions:**
1. **Check Java version:**
   ```bash
   java -version  # Should be Java 17+
   mvn -version
   ```

2. **Clean and rebuild:**
   ```bash
   mvn clean
   mvn install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true
   ```

3. **Check dependencies:**
   ```bash
   # Verify parent POM exists
   ls -la ../pom.xml
   ```

### 7. **SRT Stream Issues**

#### Issue: Cannot receive SRT streams
```bash
✗ FAILED: SRT port available for testing
```

**Solutions:**
1. **Check port availability:**
   ```bash
   # Check if port is in use
   netstat -tlnp | grep 8080
   
   # Use different port if needed
   export SRT_PORT="8081"
   ```

2. **Test SRT connectivity:**
   ```bash
   # Simple SRT test
   ffmpeg -f lavfi -i testsrc2=duration=10:size=640x480:rate=25 \
          -f mpegts "srt://127.0.0.1:8080?streamid=test&mode=caller"
   ```

### 8. **Performance Issues**

#### Issue: High memory usage or CPU consumption

**Solutions:**
1. **Monitor Java memory:**
   ```bash
   # Check heap usage
   jstat -gc $(pgrep -f antmedia)
   
   # Check for memory leaks
   jmap -histo $(pgrep -f antmedia) | grep -i scte35
   ```

2. **Adjust JVM settings:**
   ```bash
   # In start.sh, increase heap size if needed
   -Xmx4g -Xms2g
   ```

3. **Limit concurrent streams:**
   ```bash
   # Monitor active streams
   curl http://localhost:5080/WebRTCAppEE/rest/v2/broadcasts/active-live-stream-count
   ```

## 🔧 Debug Commands

### Log Analysis
```bash
# Plugin initialization
grep "SCTE-35 Plugin initialized" /path/to/antmedia/logs/antmedia.log

# Stream processing
grep "SCTE-35 packet listener" /path/to/antmedia/logs/antmedia.log

# Event detection
grep "SCTE-35 message received" /path/to/antmedia/logs/antmedia.log

# HLS processing
grep "SCTE-35 metadata" /path/to/antmedia/logs/antmedia.log
```

### Network Debugging
```bash
# Monitor SRT traffic
sudo tcpdump -i any port 8080 -w scte35-debug.pcap

# Check HTTP requests
curl -v http://localhost:5080/WebRTCAppEE/rest/v2/broadcasts/count
```

### Stream Analysis
```bash
# Analyze MPEG-TS stream
ffprobe -show_streams -show_packets srt://127.0.0.1:8080?streamid=your-stream

# Check for SCTE-35 tables
ffprobe -show_data -show_packets -select_streams d srt://127.0.0.1:8080?streamid=your-stream
```

## 📞 Getting Help

If you're still experiencing issues:

1. **Check logs first:** Always start with the Ant Media Server logs
2. **Run validation:** Use `./test-scripts/validate-plugin.sh`
3. **Test with simple stream:** Use `./test-scripts/quick-test.sh`
4. **Gather debug info:**
   ```bash
   # System info
   uname -a
   java -version
   
   # AMS info
   curl http://localhost:5080/WebRTCAppEE/rest/v2/version
   
   # Plugin info
   ls -la /path/to/antmedia/plugins/ant-media-scte35-plugin.jar
   ```

## 📚 Additional Resources

- **Plugin Documentation:** `README.md`
- **Testing Guide:** `test-scripts/TESTING_GUIDE.md`
- **Testing Summary:** `TESTING_SUMMARY.md`
- **Ant Media Server Docs:** [https://antmedia.io/docs](https://antmedia.io/docs)
- **SCTE-35 Standard:** [https://www.scte.org/standards/library/](https://www.scte.org/standards/library/) 