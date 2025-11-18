package io.antmedia.scte35;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;

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
    public static class SCTE35CueState {
        final int eventId;
        final long startPts;
        final long duration;
        final boolean isCueOut;
        final String base64Data;
        final long wallClockStartMs; // System time when cue was created (used by manifest filter)
        int cueOutSegmentIndex = -1;  // Media sequence index where CUE-OUT was injected
        int cueInSegmentIndex = -1;   // Media sequence index where CUE-IN should be injected
        boolean cueInReceived = false; // Has CUE-IN metadata been received
        boolean cueInIndexAssigned = false; // Has CUE-IN segment index been assigned

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
        logger.info("SCTE-35 support disabled for stream {}", streamId);
    }

    @Override
    public synchronized void writeMetaData(String data, long dts) {
        logger.debug("writeMetaData: data: {}, dts: {}", data, dts);
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

            logger.debug("handleSCTE35Metadata: eventId: {}, pts: {}, isCueOut: {}, duration: {}", eventId, pts, isCueOut, duration);

            if (isCueOut) {
                // Handle CUE-OUT
                logger.info("handleSCTE35Metadata: CUE-OUT");
                long cueOutDuration = duration != null ? duration : -1;
                String base64Data = SCTE35ManifestModifier.generateSCTE35Base64(eventId, pts, cueOutDuration, true);

                // Create cue state
                SCTE35CueState cueState = new SCTE35CueState(eventId, pts, cueOutDuration, true, base64Data);

                // Calculate segment index where CUE-OUT will be injected (after last segment in playlist)
                String m3u8Path = getOutputURL();
                if (m3u8Path != null && new File(m3u8Path).exists()) {
                    try {
                        String content = new String(Files.readAllBytes(new File(m3u8Path).toPath()));
                        int mediaSequence = SCTE35ManifestModifier.parseMediaSequence(content);
                        int segmentCount = SCTE35ManifestModifier.countSegments(content);

                        // CUE-OUT will be injected after last segment
                        cueState.cueOutSegmentIndex = mediaSequence + segmentCount;
                        logger.info("CUE-OUT will be injected at segment index: {}", cueState.cueOutSegmentIndex);
                    } catch (Exception e) {
                        logger.error("Error calculating segment index: {}", e.getMessage());
                    }
                }

                activeCues.put(eventId, cueState);

                logger.info("SCTE-35 CUE-OUT registered for stream {}: eventId={}, duration={}", streamId, eventId, cueOutDuration);

            } else {
                // Handle CUE-IN
                logger.info("handleSCTE35Metadata: CUE-IN");
                SCTE35CueState cueState = activeCues.get(eventId);
                if (cueState != null) {
                    cueState.cueInReceived = true;
                    logger.info("SCTE-35 CUE-IN received for stream {}: eventId={}", streamId, eventId);
                }
            }

        } catch (Exception e) {
            logger.error("Error handling SCTE-35 metadata for stream {}: {}", streamId, e.getMessage());
        }
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

    /**
     * Expose current active cue map for external helpers (e.g. Manifest filter)
     */
    public Map<Integer, SCTE35CueState> getActiveCues() {
        return activeCues;
    }

    @Override
    protected synchronized void clearResource() {
        super.clearResource();
        activeCues.clear();
    }
}
