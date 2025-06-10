package io.antmedia.scte35;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.muxer.HLSMuxer;
import io.antmedia.storage.StorageClient;
import io.vertx.core.Vertx;

/**
 * Enhanced HLS Muxer with SCTE-35 support
 * 
 * This class extends the standard HLS Muxer to add SCTE-35 cue point support
 * by injecting appropriate HLS tags into M3U8 manifests.
 */
public class SCTE35HLSMuxer extends HLSMuxer {

    private static final Logger logger = LoggerFactory.getLogger(SCTE35HLSMuxer.class);
    
    // SCTE-35 related fields
    private boolean scte35Enabled = true;
    private final ConcurrentMap<Integer, SCTE35CueState> activeCues = new ConcurrentHashMap<>();
    private long currentSegmentStartTime = 0;
    private int segmentSequence = 0;
    // Timer id for periodic CUE-OUT-CONT updates
    private long cueContTimerId = -1;
    
    // HLS tag types for SCTE-35
    public enum SCTE35TagType {
        EXT_X_CUE_OUT_IN,           // #EXT-X-CUE-OUT and #EXT-X-CUE-IN
        EXT_X_DATERANGE,            // #EXT-X-DATERANGE
        EXT_X_SCTE35,               // #EXT-X-SCTE35
        EXT_X_SPLICEPOINT_SCTE35    // #EXT-X-SPLICEPOINT-SCTE35
    }
    
    private SCTE35TagType scte35TagType = SCTE35TagType.EXT_X_CUE_OUT_IN;
    
    /**
     * Represents the state of an active SCTE-35 cue
     */
    private static class SCTE35CueState {
        final int eventId;
        final long startPts;
        final long duration;
        final boolean isCueOut;
        final String base64Data;
        final long wallClockStartMs; // when we injected CUE-OUT (system time)
        
        SCTE35CueState(int eventId, long startPts, long duration, boolean isCueOut, String base64Data) {
            this.eventId = eventId;
            this.startPts = startPts;
            this.duration = duration;
            this.isCueOut = isCueOut;
            this.base64Data = base64Data;
            this.wallClockStartMs = System.currentTimeMillis();
        }
    }

    public SCTE35HLSMuxer(Vertx vertx, StorageClient storageClient, String s3StreamsFolderPath, 
                         int uploadExtensionsToS3, String httpEndpoint, boolean addDateTimeToResourceName) {
        super(vertx, storageClient, s3StreamsFolderPath, uploadExtensionsToS3, httpEndpoint, addDateTimeToResourceName);
    }

    /**
     * Enable SCTE-35 support with specified tag type
     */
    public void enableSCTE35(SCTE35TagType tagType) {
        this.scte35Enabled = true;
        this.scte35TagType = tagType;
        logger.info("SCTE-35 support enabled for stream {} with tag type: {}", streamId, tagType);
    }

    /**
     * Disable SCTE-35 support
     */
    public void disableSCTE35() {
        this.scte35Enabled = false;
        this.activeCues.clear();
        stopCueContTimerIfNeeded();
        logger.info("SCTE-35 support disabled for stream {}", streamId);
    }

    @Override
    public synchronized void writeMetaData(String data, long dts) {
        logger.info("writeMetaData: data: {}, dts: {}", data, dts);
        // Check if this is SCTE-35 metadata
        if (scte35Enabled && isSCTE35Metadata(data)) {
            handleSCTE35Metadata(data, dts);
        } else {
            // Pass through to parent for regular metadata (ID3)
            super.writeMetaData(data, dts);
        }
    }

    /**
     * Check if metadata is SCTE-35 related
     */
    private boolean isSCTE35Metadata(String data) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(data);
            logger.info("isSCTE35Metadata: json: {}", json);
            return "scte35".equals(json.get("type"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Handle SCTE-35 metadata and inject appropriate HLS tags
     */
    private void handleSCTE35Metadata(String data, long dts) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(data);
            
            int eventId = ((Long) json.get("eventId")).intValue();
            long pts = (Long) json.get("pts");
            boolean isCueOut = (Boolean) json.get("isCueOut");
            Long duration = (Long) json.get("duration");

            logger.info("handleSCTE35Metadata: eventId: {}, pts: {}, isCueOut: {}, duration: {}", eventId, pts, isCueOut, duration);
            
            if (isCueOut) {
                logger.info("handleSCTE35Metadata: CUE-OUT");
                // Handle CUE-OUT
                long cueOutDuration = duration != null ? duration : -1;
                String base64Data = generateSCTE35Base64(eventId, pts, cueOutDuration, true);
                logger.info("handleSCTE35Metadata: base64Data: {}, eventId: {}, pts: {}, cueOutDuration: {}", base64Data, eventId, pts, cueOutDuration);

                SCTE35CueState cueState = new SCTE35CueState(eventId, pts, cueOutDuration, true, base64Data);
                activeCues.put(eventId, cueState);

                startCueContTimerIfNeeded();
                
                injectCueOutTag(cueState);
                logger.info("SCTE-35 CUE-OUT injected for stream {}: eventId={}, duration={}", streamId, eventId, cueOutDuration);
                
            } else {
                // Handle CUE-IN
                logger.info("handleSCTE35Metadata: CUE-IN");
                SCTE35CueState cueState = activeCues.remove(eventId);
                if (cueState != null) {
                    String base64Data = generateSCTE35Base64(eventId, pts, -1, false);
                    logger.info("handleSCTE35Metadata: base64Data: {}, eventId: {}, pts: {}, cueInDuration: {}", base64Data, eventId, pts, -1);

                    injectCueInTag(eventId, base64Data);
                    logger.info("SCTE-35 CUE-IN injected for stream {}: eventId={}", streamId, eventId);
                }

                stopCueContTimerIfNeeded();
            }
            
        } catch (Exception e) {
            logger.error("Error handling SCTE-35 metadata for stream {}: {}", streamId, e.getMessage());
        }
    }

    /**
     * Generate base64 encoded SCTE-35 data (simplified)
     */
    private String generateSCTE35Base64(int eventId, long pts, long duration, boolean isCueOut) {
        // This is a simplified SCTE-35 message generation
        // In a real implementation, you would construct proper SCTE-35 binary data
        
        StringBuilder scte35Data = new StringBuilder();
        scte35Data.append("FC"); // Table ID
        scte35Data.append("30"); // Section syntax indicator + private indicator + reserved + section length (simplified)
        scte35Data.append("00"); // Protocol version
        scte35Data.append("00"); // Encrypted packet + encryption algorithm + PTS adjustment
        scte35Data.append("00000000"); // PTS adjustment continued
        scte35Data.append("00"); // CW index
        scte35Data.append("0000"); // Tier + splice command length
        scte35Data.append("05"); // Splice command type (splice_insert)
        
        // Event ID (4 bytes)
        scte35Data.append(String.format("%08X", eventId));
        
        // Flags
        if (isCueOut) {
            scte35Data.append("FF"); // out_of_network_indicator=1, program_splice_flag=1, duration_flag=1, splice_immediate_flag=1
        } else {
            scte35Data.append("7F"); // out_of_network_indicator=0, program_splice_flag=1, duration_flag=1, splice_immediate_flag=1
        }
        
        // Duration (if applicable)
        if (duration > 0) {
            scte35Data.append(String.format("%016X", duration));
        }
        
        // Convert hex string to bytes and then to base64
        byte[] bytes = hexStringToByteArray(scte35Data.toString());
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Convert hex string to byte array
     */
    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * Inject CUE-OUT tag into M3U8 manifest
     */
    private void injectCueOutTag(SCTE35CueState cueState) {
        try {
            String m3u8Path = getOutputURL();
            logger.info("injectCueOutTag: m3u8Path: {}", m3u8Path);
            if (m3u8Path != null && new File(m3u8Path).exists()) {
                modifyM3U8WithCueOut(m3u8Path, cueState);
            }
        } catch (Exception e) {
            logger.error("Error injecting CUE-OUT tag for stream {}: {}", streamId, e.getMessage());
        }
    }

    /**
     * Inject CUE-IN tag into M3U8 manifest
     */
    private void injectCueInTag(int eventId, String base64Data) {
        try {
            String m3u8Path = getOutputURL();
            logger.info("injectCueInTag: m3u8Path: {}", m3u8Path);
            if (m3u8Path != null && new File(m3u8Path).exists()) {
                modifyM3U8WithCueIn(m3u8Path, eventId, base64Data);
            }
        } catch (Exception e) {
            logger.error("Error injecting CUE-IN tag for stream {}: {}", streamId, e.getMessage());
        }
    }

    /**
     * Modify M3U8 file to add CUE-OUT tags
     */
    private void modifyM3U8WithCueOut(String m3u8Path, SCTE35CueState cueState) throws IOException {
        Path path = new File(m3u8Path).toPath();
        String content = new String(Files.readAllBytes(path));
        
        // Find the last segment line and add CUE-OUT tags before it
        String[] lines = content.split("\n");
        StringBuilder newContent = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Check if this is the last segment (before adding new segment)
            if (line.startsWith("#EXTINF:") && i + 1 < lines.length &&
                (lines[i + 1].endsWith(".ts") || lines[i + 1].endsWith(".fmp4"))) {
                
                // Add SCTE-35 tags based on configured type
                switch (scte35TagType) {
                    case EXT_X_CUE_OUT_IN:
                        newContent.append("#EXT-X-DISCONTINUITY\n");
                        if (cueState.duration > 0) {
                            newContent.append(String.format("#EXT-X-CUE-OUT:%.3f\n", cueState.duration / 90000.0));
                        } else {
                            newContent.append("#EXT-X-CUE-OUT\n");
                        }
                        break;
                        
                    case EXT_X_SCTE35:
                        newContent.append("#EXT-X-DISCONTINUITY\n");
                        newContent.append(String.format("#EXT-X-SCTE35:CUE=\"%s\",CUE-OUT=YES\n", cueState.base64Data));
                        break;
                        
                    case EXT_X_SPLICEPOINT_SCTE35:
                        newContent.append("#EXT-X-DISCONTINUITY\n");
                        newContent.append(String.format("#EXT-X-SPLICEPOINT-SCTE35:%s\n", cueState.base64Data));
                        break;
                        
                    case EXT_X_DATERANGE:
                        newContent.append("#EXT-X-DISCONTINUITY\n");
                        String startDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date());
                        if (cueState.duration > 0) {
                            newContent.append(String.format("#EXT-X-DATERANGE:ID=\"%d\",START-DATE=\"%s\",PLANNED-DURATION=%.3f,SCTE35-OUT=%s\n",
                                    cueState.eventId, startDate, cueState.duration / 90000.0,
                                    "0x" + bytesToHex(Base64.getDecoder().decode(cueState.base64Data))));
                        } else {
                            newContent.append(String.format("#EXT-X-DATERANGE:ID=\"%d\",START-DATE=\"%s\",SCTE35-OUT=%s\n",
                                    cueState.eventId, startDate, "0x" + bytesToHex(Base64.getDecoder().decode(cueState.base64Data))));
                        }
                        break;
                }
                break;
            }
            
            newContent.append(line).append("\n");
        }
        
        // Write modified content back to file
        Files.write(path, newContent.toString().getBytes(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Modify M3U8 file to add CUE-IN tags
     */
    private void modifyM3U8WithCueIn(String m3u8Path, int eventId, String base64Data) throws IOException {
        Path path = new File(m3u8Path).toPath();
        String content = new String(Files.readAllBytes(path));
        
        // Find the last segment line and add CUE-IN tags before it
        String[] lines = content.split("\n");
        StringBuilder newContent = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            // Check if this is the last segment (before adding new segment)
            if (line.startsWith("#EXTINF:") && i + 1 < lines.length &&
                (lines[i + 1].endsWith(".ts") || lines[i + 1].endsWith(".fmp4"))) {
                
                // Add SCTE-35 CUE-IN tags based on configured type
                switch (scte35TagType) {
                    case EXT_X_CUE_OUT_IN:
                        newContent.append("#EXT-X-DISCONTINUITY\n");
                        newContent.append("#EXT-X-CUE-IN\n");
                        break;
                        
                    case EXT_X_SCTE35:
                        newContent.append("#EXT-X-DISCONTINUITY\n");
                        newContent.append(String.format("#EXT-X-SCTE35:CUE=\"%s\",CUE-IN=YES\n", base64Data));
                        break;
                        
                    case EXT_X_SPLICEPOINT_SCTE35:
                        newContent.append("#EXT-X-DISCONTINUITY\n");
                        newContent.append(String.format("#EXT-X-SPLICEPOINT-SCTE35:%s\n", base64Data));
                        break;
                        
                    case EXT_X_DATERANGE:
                        newContent.append("#EXT-X-DISCONTINUITY\n");
                        String endDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date());
                        newContent.append(String.format("#EXT-X-DATERANGE:ID=\"%d\",END-DATE=\"%s\",SCTE35-IN=%s\n",
                                eventId, endDate, "0x" + bytesToHex(Base64.getDecoder().decode(base64Data))));
                        break;
                }
                break;
            }
            
            newContent.append(line).append("\n");
        }
        
        // Write modified content back to file
        Files.write(path, newContent.toString().getBytes(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Convert byte array to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }

    /**
     * Update CUE-OUT-CONT tags for ongoing ad breaks
     */
    private void updateCueOutCont() {
        if (!scte35Enabled || activeCues.isEmpty()) {
            return;
        }
        
        try {
            String m3u8Path = getOutputURL();
            if (m3u8Path != null && new File(m3u8Path).exists()) {
                // Implementation for updating CUE-OUT-CONT tags
                // This would be called periodically during segment creation
                updateM3U8WithCueOutCont(m3u8Path);
            }
        } catch (Exception e) {
            logger.error("Error updating CUE-OUT-CONT for stream {}: {}", streamId, e.getMessage());
        }
    }

    /**
     * Update M3U8 file with CUE-OUT-CONT tags
     */
    private void updateM3U8WithCueOutCont(String m3u8Path) throws IOException {
        if (activeCues.isEmpty()) {
            return;
        }

        Path path = new File(m3u8Path).toPath();
        String content = new String(Files.readAllBytes(path));

        String[] lines = content.split("\n");
        StringBuilder newContent = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            newContent.append(line).append("\n");

            // Inject immediately before the last segment URI (same rule we use for OUT/IN)
            if (line.startsWith("#EXTINF:") && i + 1 < lines.length &&
                (lines[i + 1].endsWith(".ts") || lines[i + 1].endsWith(".fmp4"))) {

                long nowMs = System.currentTimeMillis();

                for (SCTE35CueState cueState : activeCues.values()) {
                    double elapsedSec = (nowMs - cueState.wallClockStartMs) / 1000.0;
                    switch (scte35TagType) {
                        case EXT_X_CUE_OUT_IN:
                            if (cueState.duration > 0) {
                                newContent.append(String.format("#EXT-X-CUE-OUT-CONT:Elapsed=%.3f,Duration=%.3f\n",
                                        elapsedSec, cueState.duration / 90000.0));
                            } else {
                                newContent.append(String.format("#EXT-X-CUE-OUT-CONT:Elapsed=%.3f\n", elapsedSec));
                            }
                            break;

                        case EXT_X_SCTE35:
                            newContent.append(String.format("#EXT-X-SCTE35:CUE=\"%s\",CUE-OUT-CONT=YES,ELAPSED=%.3f\n",
                                    cueState.base64Data, elapsedSec));
                            break;

                        case EXT_X_SPLICEPOINT_SCTE35:
                            // No official CONT variant, reuse same tag type
                            newContent.append(String.format("#EXT-X-SPLICEPOINT-SCTE35:%s\n", cueState.base64Data));
                            break;

                        case EXT_X_DATERANGE:
                            newContent.append(String.format("#EXT-X-DATERANGE:ID=\"%d\",ELAPSED=%.3f,SCTE35-OUT-CONT=%s\n",
                                    cueState.eventId, elapsedSec, "0x" + bytesToHex(Base64.getDecoder().decode(cueState.base64Data))));
                            break;
                    }
                }
            }
        }

        Files.write(path, newContent.toString().getBytes(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Get SCTE-35 tag type
     */
    public SCTE35TagType getSCTE35TagType() {
        return scte35TagType;
    }

    /**
     * Set SCTE-35 tag type
     */
    public void setSCTE35TagType(SCTE35TagType tagType) {
        this.scte35TagType = tagType;
        logger.info("SCTE-35 tag type changed to {} for stream {}", tagType, streamId);
    }

    /**
     * Check if SCTE-35 is enabled
     */
    public boolean isSCTE35Enabled() {
        return scte35Enabled;
    }

    /**
     * Get active cue count
     */
    public int getActiveCueCount() {
        return activeCues.size();
    }

    @Override
    protected synchronized void clearResource() {
        super.clearResource();
        activeCues.clear();
        stopCueContTimerIfNeeded();
    }

    /**
     * Start periodic timer if it is not already running
     */
    private void startCueContTimerIfNeeded() {
        if (cueContTimerId == -1 && vertx != null) {
            // run roughly once per second (adjustable)
            cueContTimerId = vertx.setPeriodic(1000, tid -> {
                try {
                    updateCueOutCont();
                } catch (Exception ex) {
                    logger.error("Error in periodic CueOutCont update for stream {}: {}", streamId, ex.getMessage());
                }
            });
            logger.debug("Started CUE-OUT-CONT timer for stream {}", streamId);
        }
    }

    /**
     * Stop periodic timer if there are no active cues or SCTE-35 disabled
     */
    private void stopCueContTimerIfNeeded() {
        if (cueContTimerId != -1 && (activeCues.isEmpty() || !scte35Enabled) && vertx != null) {
            vertx.cancelTimer(cueContTimerId);
            cueContTimerId = -1;
            logger.debug("Stopped CUE-OUT-CONT timer for stream {}", streamId);
        }
    }
} 