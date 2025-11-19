package io.antmedia.scte35;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for SCTE35Message class
 */
public class SCTE35MessageTest {

    @Test
    public void testSCTE35MessageCreation() {
        // Test CUE-OUT message
        SCTE35Message cueOutMessage = new SCTE35Message(
            SCTE35Message.Type.CUE_OUT, 
            123, 
            1000000L, 
            2700000L, 
            false
        );
        
        assertEquals(SCTE35Message.Type.CUE_OUT, cueOutMessage.getType());
        assertEquals(123, cueOutMessage.getEventId());
        assertEquals(1000000L, cueOutMessage.getSpliceTime());
        assertEquals(2700000L, cueOutMessage.getBreakDuration());
        assertFalse(cueOutMessage.isImmediate());
    }

    @Test
    public void testSCTE35MessageCueIn() {
        // Test CUE-IN message
        SCTE35Message cueInMessage = new SCTE35Message(
            SCTE35Message.Type.CUE_IN, 
            123, 
            1000000L, 
            -1L, 
            true
        );
        
        assertEquals(SCTE35Message.Type.CUE_IN, cueInMessage.getType());
        assertEquals(123, cueInMessage.getEventId());
        assertEquals(1000000L, cueInMessage.getSpliceTime());
        assertEquals(-1L, cueInMessage.getBreakDuration());
        assertTrue(cueInMessage.isImmediate());
    }

    @Test
    public void testSCTE35MessageTimeSignal() {
        // Test TIME_SIGNAL message
        SCTE35Message timeSignalMessage = new SCTE35Message(
            SCTE35Message.Type.TIME_SIGNAL, 
            -1, 
            1000000L, 
            -1L, 
            false
        );
        
        assertEquals(SCTE35Message.Type.TIME_SIGNAL, timeSignalMessage.getType());
        assertEquals(-1, timeSignalMessage.getEventId());
        assertEquals(1000000L, timeSignalMessage.getSpliceTime());
        assertEquals(-1L, timeSignalMessage.getBreakDuration());
        assertFalse(timeSignalMessage.isImmediate());
    }

    @Test
    public void testSCTE35MessageToString() {
        SCTE35Message message = new SCTE35Message(
            SCTE35Message.Type.CUE_OUT, 
            456, 
            2000000L, 
            3600000L, 
            true
        );
        
        String toString = message.toString();
        assertTrue(toString.contains("CUE_OUT"));
        assertTrue(toString.contains("456"));
        assertTrue(toString.contains("2000000"));
        assertTrue(toString.contains("3600000"));
        assertTrue(toString.contains("true"));
    }
} 