package io.antmedia.scte35;

/**
 * Represents a parsed SCTE-35 message
 */
public class SCTE35Message {
    
    public enum Type {
        CUE_OUT,
        CUE_IN,
        TIME_SIGNAL,
        SPLICE_INSERT_CANCEL
    }
    
    private final Type type;
    private final int eventId;
    private final long spliceTime;
    private final long breakDuration;
    private final boolean immediate;
    
    public SCTE35Message(Type type, int eventId, long spliceTime, long breakDuration, boolean immediate) {
        this.type = type;
        this.eventId = eventId;
        this.spliceTime = spliceTime;
        this.breakDuration = breakDuration;
        this.immediate = immediate;
    }
    
    public Type getType() {
        return type;
    }
    
    public int getEventId() {
        return eventId;
    }
    
    public long getSpliceTime() {
        return spliceTime;
    }
    
    public long getBreakDuration() {
        return breakDuration;
    }
    
    public boolean isImmediate() {
        return immediate;
    }
    
    @Override
    public String toString() {
        return String.format("SCTE35Message{type=%s, eventId=%d, spliceTime=%d, breakDuration=%d, immediate=%s}",
                type, eventId, spliceTime, breakDuration, immediate);
    }
} 