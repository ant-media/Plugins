package io.antmedia.scte35;

import java.util.Base64;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.scte35.SCTE35HLSMuxer.SCTE35CueState;
import io.antmedia.scte35.SCTE35HLSMuxer.SCTE35TagType;

/**
 * Utility class for injecting SCTE-35 tags into HLS playlists.
 * 
 * Handles the transformation logic for adding CUE-OUT, CUE-IN, and CUE-OUT-CONT
 * tags based on active cue state.
 */
public class SCTE35ManifestModifier {

    private static final Logger logger = LoggerFactory.getLogger(SCTE35ManifestModifier.class);

    public static class ModificationResult {
        public final String modifiedPlaylist;  // null if no modifications were made
        public final int mediaSequence;        // Parsed media sequence from playlist
        public final int segmentCount;         // Number of segments in playlist
        public final double targetDuration;    // Target duration from playlist

        public ModificationResult(String modifiedPlaylist, int mediaSequence, int segmentCount, double targetDuration) {
            this.modifiedPlaylist = modifiedPlaylist;
            this.mediaSequence = mediaSequence;
            this.segmentCount = segmentCount;
            this.targetDuration = targetDuration;
        }

        public boolean hasModifications() {
            return modifiedPlaylist != null;
        }
    }

    /**
     * Injects SCTE-35 tags into HLS playlist based on active cue states.
     * 
     * ⚠️ WARNING: This method mutates SCTE35CueState objects in activeCues
     *    to assign cueInSegmentIndex when a CUE-IN is received. This is intentional
     *    for simplicity and performance (avoids re-parsing playlist multiple times).
     * 
     * @param playlist The original HLS playlist content
     * @param activeCues Map of active SCTE-35 cues (will be mutated)
     * @param tagType The type of SCTE-35 tags to inject
     * @return ModificationResult with modified playlist (null if no active cues in window)
     *         and parsed metadata (mediaSequence, segmentCount, targetDuration)
     */
    public static ModificationResult injectSCTE35Tags(
            String playlist,
            Map<Integer, SCTE35CueState> activeCues,
            SCTE35TagType tagType) {

        if (playlist == null || playlist.isEmpty()) {
            return new ModificationResult(null, 0, 0, 0.0);
        }

        // Parse playlist metadata
        int mediaSequence = parseMediaSequence(playlist);
        double targetDuration = parseTargetDuration(playlist);
        int segmentCount = countSegments(playlist);

        // Return early if no active cues
        if (activeCues == null || activeCues.isEmpty()) {
            return new ModificationResult(null, mediaSequence, segmentCount, targetDuration);
        }

        // UPDATE STATE: Assign cueInSegmentIndex for cues that received CUE-IN
        for (SCTE35CueState cueState : activeCues.values()) {
            if (cueState.cueInReceived && !cueState.cueInIndexAssigned) {
                cueState.cueInSegmentIndex = mediaSequence + segmentCount;
                cueState.cueInIndexAssigned = true;
                logger.info("Assigned cueInSegmentIndex={} for eventId={}", 
                           cueState.cueInSegmentIndex, cueState.eventId);
            }
        }

        // Build modified playlist
        String[] lines = playlist.split("\n");
        StringBuilder newContent = new StringBuilder();
        int segmentIndex = 0;
        boolean inSegmentBlock = false;
        boolean anyTagsInjected = false;
        boolean discontinuityInjected = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Skip existing SCTE-35 related tags to avoid duplicates
            if (line.startsWith("#EXT-X-CUE-OUT") ||
                    line.startsWith("#EXT-X-CUE-IN") ||
                    line.startsWith("#EXT-X-SCTE35") ||
                    line.startsWith("#EXT-X-SPLICEPOINT-SCTE35") ||
                    (line.startsWith("#EXT-X-DATERANGE") && line.contains("SCTE35")) ||
                    line.equals("#EXT-X-DISCONTINUITY")) {
                continue; // Skip old SCTE-35 tags
            }

            // Check if this is an EXTINF line (start of segment block)
            if (line.startsWith("#EXTINF:")) {
                inSegmentBlock = true;

                // Calculate absolute segment index for THIS segment
                int absoluteSegmentIndex = mediaSequence + segmentIndex;

                // Inject DISCONTINUITY + CUE-OUT/CUE-IN BEFORE this segment's #EXTINF
                for (SCTE35CueState cueState : activeCues.values()) {
                    
                    // Inject CUE-OUT before this segment
                    if (absoluteSegmentIndex == cueState.cueOutSegmentIndex) {
                        newContent.append("#EXT-X-DISCONTINUITY\n");
                        newContent.append("#EXT-X-CUE-OUT");
                        if (cueState.duration > 0) {
                            newContent.append(String.format(":%.3f", cueState.duration / 90000.0));
                        }
                        newContent.append("\n");
                        discontinuityInjected = true;
                        anyTagsInjected = true;
                    }
                    
                    // Inject CUE-IN before this segment
                    else if (cueState.cueInSegmentIndex != -1 && absoluteSegmentIndex == cueState.cueInSegmentIndex) {
                        newContent.append("#EXT-X-DISCONTINUITY\n");
                        newContent.append("#EXT-X-CUE-IN\n");
                        discontinuityInjected = true;
                        anyTagsInjected = true;
                    }
                    
                    // Inject CUE-OUT-CONT before this segment (if in middle of ad break)
                    else if (absoluteSegmentIndex > cueState.cueOutSegmentIndex &&
                            (cueState.cueInSegmentIndex == -1 || absoluteSegmentIndex < cueState.cueInSegmentIndex)) {

                        double elapsed = (absoluteSegmentIndex - cueState.cueOutSegmentIndex) * targetDuration;
                        newContent.append(String.format("#EXT-X-CUE-OUT-CONT:Elapsed=%.3f", elapsed));
                        if (cueState.duration > 0) {
                            newContent.append(String.format(",Duration=%.3f", cueState.duration / 90000.0));
                        }
                        newContent.append("\n");
                        anyTagsInjected = true;
                    }
                }

                newContent.append(line).append("\n");

            } else if (inSegmentBlock && (line.trim().endsWith(".ts") || line.trim().endsWith(".fmp4"))) {
                // This is the segment file line
                newContent.append(line).append("\n");
                segmentIndex++;
                inSegmentBlock = false;

            } else {
                // Regular line (header, other tags, etc.)
                newContent.append(line).append("\n");
            }
        }

        // Return null playlist if no tags were injected
        String resultPlaylist = anyTagsInjected ? newContent.toString() : null;
        return new ModificationResult(resultPlaylist, mediaSequence, segmentCount, targetDuration);
    }

    /**
     * Parse media sequence number from playlist
     */
    public static int parseMediaSequence(String playlistContent) {
        try {
            String[] lines = playlistContent.split("\n");
            for (String line : lines) {
                if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                    return Integer.parseInt(line.substring("#EXT-X-MEDIA-SEQUENCE:".length()).trim());
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing media sequence: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Parse target duration from playlist
     */
    public static double parseTargetDuration(String playlistContent) {
        try {
            String[] lines = playlistContent.split("\n");
            for (String line : lines) {
                if (line.startsWith("#EXT-X-TARGETDURATION:")) {
                    return Double.parseDouble(line.substring("#EXT-X-TARGETDURATION:".length()).trim());
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing target duration: {}", e.getMessage());
        }
        return 2.0; // Default fallback
    }

    /**
     * Count number of segments in playlist
     */
    public static int countSegments(String playlistContent) {
        int count = 0;
        String[] lines = playlistContent.split("\n");
        for (String line : lines) {
            if (line.trim().endsWith(".ts") || line.trim().endsWith(".fmp4")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Generate base64 encoded SCTE-35 data (simplified)
     */
    public static String generateSCTE35Base64(int eventId, long pts, long duration, boolean isCueOut) {
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
    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * Convert byte array to hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }
}
