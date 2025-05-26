package io.antmedia.scte35;

/**
 * Represents a SCTE-35 event for HLS processing
 */
public class SCTE35Event {
    
    private final int eventId;
    private final long pts;
    private final long duration;
    private final boolean isCueOut;
    
    public SCTE35Event(int eventId, long pts, long duration, boolean isCueOut) {
        this.eventId = eventId;
        this.pts = pts;
        this.duration = duration;
        this.isCueOut = isCueOut;
    }
    
    public int getEventId() {
        return eventId;
    }
    
    public long getPts() {
        return pts;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public boolean isCueOut() {
        return isCueOut;
    }
    
    @Override
    public String toString() {
        return String.format("SCTE35Event{eventId=%d, pts=%d, duration=%d, isCueOut=%s}",
                eventId, pts, duration, isCueOut);
    }
} 