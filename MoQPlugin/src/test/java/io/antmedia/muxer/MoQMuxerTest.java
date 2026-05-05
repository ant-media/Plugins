package io.antmedia.muxer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.bytedeco.ffmpeg.global.avcodec.*;

import io.vertx.core.Vertx;
import org.junit.Test;

import java.lang.reflect.Method;

public class MoQMuxerTest {

    private MoQMuxer newMuxer(int height) {
        return new MoQMuxer(mock(Vertx.class), "stream1", height, "live", "http://localhost:4443/moq");
    }

    @Test
    public void testFreshMuxer() {
        // height=0 -> "source", height=N -> "Np"
        assertEquals("moq://live/stream1/source", newMuxer(0).getOutputURL());
        assertEquals("moq://live/stream1/720p", newMuxer(720).getOutputURL());

        // moq-cli not started yet -> no error stream
        assertNull(newMuxer(0).getCliErrorStream());
    }

    @Test
    public void testIsCodecSupported() {
        MoQMuxer m = newMuxer(0);

        assertTrue(m.isCodecSupported(AV_CODEC_ID_H264));
        assertTrue(m.isCodecSupported(AV_CODEC_ID_H265));
        assertTrue(m.isCodecSupported(AV_CODEC_ID_AAC));
        assertTrue(m.isCodecSupported(AV_CODEC_ID_OPUS));

        assertFalse(m.isCodecSupported(AV_CODEC_ID_VP8));
        assertFalse(m.isCodecSupported(AV_CODEC_ID_NONE));
    }

    @Test
    public void testExtractAnnexBSPSPPS() throws Exception {
        Method m = MoQMuxer.class.getDeclaredMethod("extractAnnexBSPSPPS", byte[].class);
        m.setAccessible(true);
        MoQMuxer muxer = newMuxer(0);

        // SPS (type=7) + PPS (type=8) -> valid extradata, first byte must be 0x00
        byte[] sps = { 0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x0A, (byte) 0xDA };
        byte[] pps = { 0x00, 0x00, 0x00, 0x01, 0x68, (byte) 0xCE, 0x38, (byte) 0x80 };
        byte[] full = new byte[sps.length + pps.length];
        System.arraycopy(sps, 0, full, 0, sps.length);
        System.arraycopy(pps, 0, full, sps.length, pps.length);

        byte[] result = (byte[]) m.invoke(muxer, (Object) full);
        assertNotNull(result);
        assertEquals(0x00, result[0]);

        // Empty input -> null
        assertNull(m.invoke(muxer, (Object) new byte[0]));

        // SPS without PPS -> null
        assertNull(m.invoke(muxer, (Object) sps));
    }
}
