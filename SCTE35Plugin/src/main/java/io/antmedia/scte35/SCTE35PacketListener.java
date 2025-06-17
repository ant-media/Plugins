package io.antmedia.scte35;

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
    private static final int SCTE35_STREAM_ID = 0x06;
    
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
    @Override
    public AVPacket onDataPacket(String streamId, AVPacket packet) {
        try {
            logger.info("Processing SCTE-35 data for stream: {}", streamId);
            // Extract packet data
            byte[] data = new byte[packet.size()];
            packet.data().position(0).get(data, 0, data.length);
            logger.info("SCTE-35 data: {}", data);
            // Parse SCTE-35 data
            SCTE35Message scte35Message = parseSCTE35Data(data);
            logger.info("SCTE-35 message: {}", scte35Message);
            if (scte35Message != null) {
                handleSCTE35Message(scte35Message, packet.pts());
            }
        } catch (Exception e) {
            logger.error("Error processing SCTE-35 data for stream {}: {}", streamId, e.getMessage());
        }
        
        return packet;
    }

    // -----------------------------------------------------------
    // Bit-level helper for parsing SCTE-35 binary payloads
    // -----------------------------------------------------------
    private static class BitReader {
        private final byte[] data;
        private int bytePos = 0;
        private int bitPos = 0; // next bit index inside current byte (0-7)

        BitReader(byte[] data) {
            this.data = data;
        }

        public boolean readBit() {
            if (bytePos >= data.length) {
                throw new IndexOutOfBoundsException("Read past end of data");
            }
            boolean bit = ((data[bytePos] >> (7 - bitPos)) & 0x01) == 1;
            bitPos++;
            if (bitPos == 8) {
                bitPos = 0;
                bytePos++;
            }
            return bit;
        }

        public void skipBits(int n) {
            for (int i = 0; i < n; i++) {
                readBit();
            }
        }

        public int readBits(int n) {
            if (n > 32) {
                throw new IllegalArgumentException("Cannot read more than 32 bits as int");
            }
            int value = 0;
            for (int i = 0; i < n; i++) {
                value = (value << 1) | (readBit() ? 1 : 0);
            }
            return value;
        }

        public long readBitsLong(int n) {
            long value = 0;
            for (int i = 0; i < n; i++) {
                value = (value << 1) | (readBit() ? 1 : 0);
            }
            return value;
        }
    }

    /**
     * Parse SCTE-35 data from packet payload
     */
    private SCTE35Message parseSCTE35Data(byte[] data) {
        if (data == null || data.length < 14) {
            return null; // Too short to be a valid SCTE-35 message
        }

        try {
            BitReader reader = new BitReader(data);

            int tableId = reader.readBits(8);
            if (tableId != 0xFC) {
                logger.warn("Not an SCTE-35 table (id={})", tableId);
                return null;
            }

            reader.skipBits(4);                 // section_syntax_indicator, private_indicator, reserved
            int sectionLength = reader.readBits(12);

            int protocolVersion = reader.readBits(8);
            if (protocolVersion != 0) {
                logger.warn("Unsupported SCTE-35 protocol version: {}", protocolVersion);
                return null;
            }

            boolean encryptedPacket = reader.readBit();
            int encryptionAlgorithm = reader.readBits(6);
            long ptsAdjustment = reader.readBitsLong(33);
            logger.info("Pts adjustment: {}", ptsAdjustment);

            int cwIndex = reader.readBits(8);
            int tier = reader.readBits(12);
            int spliceCommandLength = reader.readBits(12);
            int spliceCommandType = reader.readBits(8);

            logger.info("Splice command type: {}", spliceCommandType);
            logger.info("Splice command length: {}", spliceCommandLength);

            SCTE35Message message = parseSpliceCommand(reader, spliceCommandType, spliceCommandLength, ptsAdjustment);
            return message;
        } catch (Exception e) {
            logger.error("Error parsing SCTE-35 data: {}", e.toString());
            return null;
        }
    }

    /**
     * Parse specific splice command types
     */
    private SCTE35Message parseSpliceCommand(BitReader reader, int commandType, int commandLength, long ptsAdjustment) {
        logger.info("Parsing splice command: type={}, length={}, ptsAdjustment={}", commandType, commandLength, ptsAdjustment);
        switch (commandType) {
            case 0x05: // splice_insert
                return parseSpliceInsert(reader, ptsAdjustment);
            case 0x06: // time_signal
                return parseTimeSignal(reader, ptsAdjustment);
            default:
                // Skip unknown command payload
                reader.skipBits(commandLength * 8);
                logger.warn("Unsupported SCTE-35 command type: {}", commandType);
                return null;
        }
    }

    /**
     * Parse splice_insert command
     */
    private SCTE35Message parseSpliceInsert(BitReader reader, long ptsAdjustment) {
        try {
            logger.info("Parsing splice insert");
            int spliceEventId = (int) reader.readBitsLong(32);
            boolean spliceEventCancelIndicator = reader.readBit();
            reader.skipBits(7);

            if (spliceEventCancelIndicator) {
                logger.warn("Splice event cancel indicator: {}", spliceEventCancelIndicator);
                return new SCTE35Message(SCTE35Message.Type.SPLICE_INSERT_CANCEL, spliceEventId, -1, -1, false);
            }

            boolean outOfNetworkIndicator = reader.readBit();
            boolean programSpliceFlag = reader.readBit();
            boolean durationFlag = reader.readBit();
            boolean spliceImmediateFlag = reader.readBit();
            reader.skipBits(4);

            long spliceTime = -1;
            long breakDuration = -1;

            // Read splice time when needed
            if (programSpliceFlag && !spliceImmediateFlag) {
                boolean timeSpecifiedFlag = reader.readBit();
                reader.skipBits(6);
                if (timeSpecifiedFlag) {
                    spliceTime = reader.readBitsLong(33) + ptsAdjustment;
                }
            } else if (!programSpliceFlag) {
                int componentCount = reader.readBits(8);
                for (int i = 0; i < componentCount; i++) {
                    reader.skipBits(8); // component_tag
                    boolean timeSpecifiedFlag = reader.readBit();
                    reader.skipBits(6);
                    if (timeSpecifiedFlag) {
                        reader.skipBits(33); // pts_time (we ignore per-component timing)
                    }
                }
            }

            // Read break duration if present
            if (durationFlag) {
                boolean autoReturn = reader.readBit();
                reader.skipBits(6);
                breakDuration = reader.readBitsLong(33);
            }

            // Skip unique_program_id, avail_num, avails_expected
            reader.skipBits(16 + 8 + 8);

            SCTE35Message.Type type = outOfNetworkIndicator ? SCTE35Message.Type.CUE_OUT : SCTE35Message.Type.CUE_IN;
            logger.info("Splice insert parsed: type={}, eventId={}, spliceTime={}, breakDuration={}, immediate={}", type, spliceEventId, spliceTime, breakDuration, spliceImmediateFlag);
            return new SCTE35Message(type, spliceEventId, spliceTime, breakDuration, spliceImmediateFlag);
        } catch (Exception e) {
            logger.error("Error parsing splice_insert: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse time_signal command (simplified)
     */
    private SCTE35Message parseTimeSignal(BitReader reader, long ptsAdjustment) {
        try {
            logger.info("Parsing time signal");
            boolean timeSpecifiedFlag = reader.readBit();
            reader.skipBits(6);
            long spliceTime = -1;

            if (timeSpecifiedFlag) {
                spliceTime = reader.readBitsLong(33) + ptsAdjustment;
            }
            logger.info("Time signal parsed: spliceTime={}", spliceTime);
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
        logger.info("handleCueOut: message: {}", message);
        logger.info("handleCueOut: currentPts: {}", currentPts);
        logger.info("handleCueOut: inCueOut: {}", inCueOut);
        logger.info("handleCueOut: cueOutStartTime: {}", cueOutStartTime);
        logger.info("handleCueOut: cueOutDuration: {}", cueOutDuration);
        logger.info("handleCueOut: currentEventId: {}", currentEventId);
        if (!inCueOut) {
            inCueOut = true;
            cueOutStartTime = currentPts;
            cueOutDuration = message.getBreakDuration();
            currentEventId = message.getEventId();
            
            // Add SCTE-35 event for HLS processing
            SCTE35Event event = new SCTE35Event(message.getEventId(), currentPts, message.getBreakDuration(), true);
            activeEvents.put(message.getEventId(), event);
            logger.info("handleCueOut: event: {}, activeEvents: {}", event, activeEvents);
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
            logger.info("notifyHLSMuxer: muxAdaptor: {}", muxAdaptor);
            if (muxAdaptor != null) {
                // Send SCTE-35 metadata to HLS muxer
                String scte35Data = createSCTE35Metadata(event);
                logger.info("notifyHLSMuxer: scte35Data: {}", scte35Data);
                // Find HLS muxer and send metadata
                for (Muxer muxer : muxAdaptor.getMuxerList()) {
                    logger.info("notifyHLSMuxer: muxer: {}", muxer instanceof SCTE35HLSMuxer);
                    if (muxer instanceof SCTE35HLSMuxer) {
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