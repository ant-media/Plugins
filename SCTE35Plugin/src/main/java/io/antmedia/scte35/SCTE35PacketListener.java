package io.antmedia.scte35;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.plugin.api.IPacketListener;
import io.antmedia.plugin.api.StreamParametersInfo;
import io.antmedia.muxer.Muxer;
import io.antmedia.muxer.HLSMuxer;

/**
 * SCTE-35 Packet Listener that detects SCTE-35 cue points from SRT streams
 * and converts them to HLS markers for ad insertion
 */
public class SCTE35PacketListener implements IPacketListener {

    private static final Logger logger = LoggerFactory.getLogger(SCTE35PacketListener.class);
    
    // SCTE-35 stream type identifier in MPEG-TS
    private static final int SCTE35_STREAM_TYPE = 0x86;
    
    private final String streamId;
    private final IAntMediaStreamHandler streamHandler;
    private final ConcurrentMap<Integer, SCTE35Event> activeEvents = new ConcurrentHashMap<>();
    
    // Track current cue-out state
    private boolean inCueOut = false;
    private long cueOutStartTime = -1;
    private long cueOutDuration = -1;
    private int currentEventId = -1;

    public SCTE35PacketListener(String streamId, IAntMediaStreamHandler streamHandler) {
        this.streamId = streamId;
        this.streamHandler = streamHandler;
    }

    @Override
    public AVPacket onVideoPacket(String streamId, AVPacket packet) {
        // Video packets pass through unchanged
        return packet;
    }

    @Override
    public AVPacket onAudioPacket(String streamId, AVPacket packet) {
        // Audio packets pass through unchanged
        return packet;
    }

    /**
     * Process data packets that may contain SCTE-35 information
     */
    public AVPacket onDataPacket(String streamId, AVPacket packet) {
        try {
            // Extract packet data
            byte[] data = new byte[packet.size()];
            packet.data().position(0).get(data, 0, data.length);
            
            // Parse SCTE-35 data
            SCTE35Message scte35Message = parseSCTE35Data(data);
            if (scte35Message != null) {
                handleSCTE35Message(scte35Message, packet.pts());
            }
        } catch (Exception e) {
            logger.error("Error processing SCTE-35 data for stream {}: {}", streamId, e.getMessage());
        }
        
        return packet;
    }

    /**
     * Parse SCTE-35 data from packet payload
     */
    private SCTE35Message parseSCTE35Data(byte[] data) {
        if (data.length < 14) { // Minimum SCTE-35 message size
            return null;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            
            // Parse SCTE-35 header
            int tableId = buffer.get() & 0xFF;
            if (tableId != 0xFC) { // SCTE-35 table ID
                return null;
            }

            // Skip section syntax indicator, private indicator, reserved bits
            int sectionLength = buffer.getShort() & 0x0FFF;
            
            // Protocol version
            int protocolVersion = buffer.get() & 0xFF;
            if (protocolVersion != 0) {
                logger.warn("Unsupported SCTE-35 protocol version: {}", protocolVersion);
                return null;
            }

            // Encrypted packet, encryption algorithm, PTS adjustment
            int encryptedPacket = buffer.get() & 0xFF;
            long ptsAdjustment = buffer.getLong() & 0x1FFFFFFFFL;
            
            // CW index, tier, splice command length
            int cwIndex = buffer.get() & 0xFF;
            int tier = (buffer.getShort() & 0x0FFF);
            int spliceCommandLength = buffer.getShort() & 0x0FFF;
            
            // Splice command type
            int spliceCommandType = buffer.get() & 0xFF;
            
            return parseSpliceCommand(buffer, spliceCommandType, spliceCommandLength, ptsAdjustment);
            
        } catch (Exception e) {
            logger.error("Error parsing SCTE-35 data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse specific splice command types
     */
    private SCTE35Message parseSpliceCommand(ByteBuffer buffer, int commandType, int commandLength, long ptsAdjustment) {
        switch (commandType) {
            case 0x05: // splice_insert
                return parseSpliceInsert(buffer, ptsAdjustment);
            case 0x06: // time_signal
                return parseTimeSignal(buffer, ptsAdjustment);
            default:
                logger.debug("Unsupported SCTE-35 command type: {}", commandType);
                return null;
        }
    }

    /**
     * Parse splice_insert command
     */
    private SCTE35Message parseSpliceInsert(ByteBuffer buffer, long ptsAdjustment) {
        try {
            int spliceEventId = buffer.getInt();
            int spliceEventCancelIndicator = (buffer.get() & 0x80) >> 7;
            
            if (spliceEventCancelIndicator == 1) {
                // Event cancellation
                return new SCTE35Message(SCTE35Message.Type.SPLICE_INSERT_CANCEL, spliceEventId, -1, -1, false);
            }

            int outOfNetworkIndicator = (buffer.get(-1) & 0x40) >> 6;
            int programSpliceFlag = (buffer.get(-1) & 0x20) >> 5;
            int durationFlag = (buffer.get(-1) & 0x10) >> 4;
            int spliceImmediateFlag = (buffer.get(-1) & 0x08) >> 3;
            
            long spliceTime = -1;
            long breakDuration = -1;
            
            // Parse splice time if not immediate
            if (spliceImmediateFlag == 0) {
                int timeSpecifiedFlag = (buffer.get() & 0x80) >> 7;
                if (timeSpecifiedFlag == 1) {
                    spliceTime = buffer.getLong() & 0x1FFFFFFFFL;
                    spliceTime += ptsAdjustment;
                }
            }
            
            // Parse break duration if present
            if (durationFlag == 1) {
                int autoReturn = (buffer.get() & 0x80) >> 7;
                breakDuration = buffer.getLong() & 0x1FFFFFFFFL;
            }
            
            boolean isOut = outOfNetworkIndicator == 1;
            SCTE35Message.Type type = isOut ? SCTE35Message.Type.CUE_OUT : SCTE35Message.Type.CUE_IN;
            
            return new SCTE35Message(type, spliceEventId, spliceTime, breakDuration, spliceImmediateFlag == 1);
            
        } catch (Exception e) {
            logger.error("Error parsing splice_insert: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse time_signal command (simplified)
     */
    private SCTE35Message parseTimeSignal(ByteBuffer buffer, long ptsAdjustment) {
        try {
            int timeSpecifiedFlag = (buffer.get() & 0x80) >> 7;
            long spliceTime = -1;
            
            if (timeSpecifiedFlag == 1) {
                spliceTime = buffer.getLong() & 0x1FFFFFFFFL;
                spliceTime += ptsAdjustment;
            }
            
            return new SCTE35Message(SCTE35Message.Type.TIME_SIGNAL, -1, spliceTime, -1, false);
            
        } catch (Exception e) {
            logger.error("Error parsing time_signal: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Handle parsed SCTE-35 message and generate HLS markers
     */
    private void handleSCTE35Message(SCTE35Message message, long currentPts) {
        logger.info("SCTE-35 message received for stream {}: type={}, eventId={}, spliceTime={}, duration={}", 
                   streamId, message.getType(), message.getEventId(), message.getSpliceTime(), message.getBreakDuration());

        switch (message.getType()) {
            case CUE_OUT:
                handleCueOut(message, currentPts);
                break;
            case CUE_IN:
                handleCueIn(message, currentPts);
                break;
            case TIME_SIGNAL:
                handleTimeSignal(message, currentPts);
                break;
            default:
                logger.debug("Unhandled SCTE-35 message type: {}", message.getType());
        }
    }

    /**
     * Handle CUE-OUT (ad break start)
     */
    private void handleCueOut(SCTE35Message message, long currentPts) {
        if (!inCueOut) {
            inCueOut = true;
            cueOutStartTime = currentPts;
            cueOutDuration = message.getBreakDuration();
            currentEventId = message.getEventId();
            
            // Add SCTE-35 event for HLS processing
            SCTE35Event event = new SCTE35Event(message.getEventId(), currentPts, message.getBreakDuration(), true);
            activeEvents.put(message.getEventId(), event);
            
            // Notify HLS muxer about cue-out
            notifyHLSMuxer(event);
            
            logger.info("CUE-OUT started for stream {}: eventId={}, duration={}", streamId, message.getEventId(), message.getBreakDuration());
        }
    }

    /**
     * Handle CUE-IN (ad break end)
     */
    private void handleCueIn(SCTE35Message message, long currentPts) {
        if (inCueOut && (message.getEventId() == currentEventId || message.getEventId() == -1)) {
            inCueOut = false;
            
            SCTE35Event event = new SCTE35Event(message.getEventId(), currentPts, -1, false);
            
            // Remove active event
            if (message.getEventId() != -1) {
                activeEvents.remove(message.getEventId());
            } else {
                // Remove current active event
                activeEvents.remove(currentEventId);
            }
            
            // Notify HLS muxer about cue-in
            notifyHLSMuxer(event);
            
            logger.info("CUE-IN received for stream {}: eventId={}", streamId, message.getEventId());
            
            currentEventId = -1;
            cueOutStartTime = -1;
            cueOutDuration = -1;
        }
    }

    /**
     * Handle TIME_SIGNAL
     */
    private void handleTimeSignal(SCTE35Message message, long currentPts) {
        // Time signals can be used for various purposes
        // Implementation depends on specific use case
        logger.debug("TIME_SIGNAL received for stream {}: spliceTime={}", streamId, message.getSpliceTime());
    }

    /**
     * Notify HLS muxer about SCTE-35 events
     */
    private void notifyHLSMuxer(SCTE35Event event) {
        try {
            // Get the mux adaptor for this stream
            var muxAdaptor = streamHandler.getMuxAdaptor(streamId);
            if (muxAdaptor != null) {
                // Send SCTE-35 metadata to HLS muxer
                String scte35Data = createSCTE35Metadata(event);
                
                // Find HLS muxer and send metadata
                for (Muxer muxer : muxAdaptor.getMuxerList()) {
                    if (muxer instanceof HLSMuxer) {
                        muxer.writeMetaData(scte35Data, event.getPts());
                        logger.debug("SCTE-35 metadata sent to HLS muxer for stream: {}", streamId);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error notifying HLS muxer for stream {}: {}", streamId, e.getMessage());
        }
    }

    /**
     * Create SCTE-35 metadata for HLS
     */
    private String createSCTE35Metadata(SCTE35Event event) {
        // Create JSON metadata with SCTE-35 information
        StringBuilder metadata = new StringBuilder();
        metadata.append("{");
        metadata.append("\"type\":\"scte35\",");
        metadata.append("\"eventId\":").append(event.getEventId()).append(",");
        metadata.append("\"pts\":").append(event.getPts()).append(",");
        metadata.append("\"isCueOut\":").append(event.isCueOut());
        
        if (event.getDuration() > 0) {
            metadata.append(",\"duration\":").append(event.getDuration());
        }
        
        metadata.append("}");
        
        return metadata.toString();
    }

    @Override
    public void writeTrailer(String streamId) {
        logger.info("SCTE35PacketListener.writeTrailer() for stream: {}", streamId);
        activeEvents.clear();
    }

    @Override
    public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
        logger.info("SCTE35PacketListener.setVideoStreamInfo() for stream: {}", streamId);
    }

    @Override
    public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
        logger.info("SCTE35PacketListener.setAudioStreamInfo() for stream: {}", streamId);
    }

    /**
     * Get current cue-out status
     */
    public boolean isInCueOut() {
        return inCueOut;
    }

    /**
     * Get current cue-out duration
     */
    public long getCueOutDuration() {
        return cueOutDuration;
    }

    /**
     * Get elapsed time since cue-out started
     */
    public long getCueOutElapsedTime(long currentPts) {
        if (inCueOut && cueOutStartTime > 0) {
            return currentPts - cueOutStartTime;
        }
        return 0;
    }
} 