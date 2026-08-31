package io.antmedia.scte35;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.plugin.api.IPacketListener;
import io.antmedia.plugin.api.StreamParametersInfo;

/**
 * SCTE-35 Packet Listener that detects SCTE-35 cue points from SRT streams
 * and converts them to HLS markers for ad insertion
 */
public class SCTE35PacketListener implements IPacketListener {

    private static final Logger logger = LoggerFactory.getLogger(SCTE35PacketListener.class);
    
    // SCTE-35 break durations are carried in 90kHz ticks
    private static final long TICKS_PER_MS = 90;

    private final String streamId;
    private final SCTE35CueTracker cueTracker;

    public SCTE35PacketListener(String streamId, SCTE35CueTracker cueTracker) {
        this.streamId = streamId;
        this.cueTracker = cueTracker;
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
            byte[] data = new byte[packet.size()];
            packet.data().position(0).get(data, 0, data.length);

            SCTE35Message scte35Message = parseSCTE35Data(data);
            if (scte35Message != null) {
                handleSCTE35Message(scte35Message);
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
                logger.debug("Not an SCTE-35 table (id={})", tableId);
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
            logger.debug("Pts adjustment: {}", ptsAdjustment);

            int cwIndex = reader.readBits(8);
            int tier = reader.readBits(12);
            int spliceCommandLength = reader.readBits(12);
            int spliceCommandType = reader.readBits(8);

            return parseSpliceCommand(reader, spliceCommandType, spliceCommandLength, ptsAdjustment);
        } catch (Exception e) {
            logger.error("Error parsing SCTE-35 data: {}", e.toString());
            return null;
        }
    }

    /**
     * Parse specific splice command types
     */
    private SCTE35Message parseSpliceCommand(BitReader reader, int commandType, int commandLength, long ptsAdjustment) {
        logger.debug("Parsing splice command: type={}, length={}, ptsAdjustment={}", commandType, commandLength, ptsAdjustment);
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
            logger.debug("Splice insert parsed: type={}, eventId={}, spliceTime={}, breakDuration={}, immediate={}", type, spliceEventId, spliceTime, breakDuration, spliceImmediateFlag);
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
            boolean timeSpecifiedFlag = reader.readBit();
            reader.skipBits(6);
            long spliceTime = -1;

            if (timeSpecifiedFlag) {
                spliceTime = reader.readBitsLong(33) + ptsAdjustment;
            }
            logger.debug("Time signal parsed: spliceTime={}", spliceTime);
            return new SCTE35Message(SCTE35Message.Type.TIME_SIGNAL, -1, spliceTime, -1, false);
        } catch (Exception e) {
            logger.error("Error parsing time_signal: {}", e.getMessage());
            return null;
        }
    }

    private void handleSCTE35Message(SCTE35Message message) {
        logger.debug("SCTE-35 message on stream {}: type={}, eventId={}, spliceTime={}, duration={}",
                   streamId, message.getType(), message.getEventId(), message.getSpliceTime(), message.getBreakDuration());

        switch (message.getType()) {
            case CUE_OUT:
                cueTracker.cueOut(message.getEventId(), ticksToMs(message.getBreakDuration()));
                break;
            case CUE_IN:
                cueTracker.cueIn(message.getEventId());
                break;
            default:
                logger.debug("Unhandled SCTE-35 message type: {}", message.getType());
        }
    }

    private static long ticksToMs(long ticks) {
        return ticks > 0 ? ticks / TICKS_PER_MS : -1;
    }

    @Override
    public void writeTrailer(String streamId) {
        logger.debug("SCTE35PacketListener.writeTrailer() for stream: {}", streamId);
    }

    @Override
    public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
        logger.debug("SCTE35PacketListener.setVideoStreamInfo() for stream: {}", streamId);
    }

    @Override
    public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
        logger.debug("SCTE35PacketListener.setAudioStreamInfo() for stream: {}", streamId);
    }
}
