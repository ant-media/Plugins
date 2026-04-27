package io.antmedia.muxer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.bytedeco.ffmpeg.global.avcodec.*;

import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

public class MoQMuxerTest {

    private MoQMuxer muxer;

    @Before
    public void setUp() {
        muxer = new MoQMuxer(mock(Vertx.class), "stream1", 0, "live", "http://localhost:4443/moq");
    }

    @Test
    public void testGetOutputURL_source() {
        assertEquals("moq://live/stream1/source", muxer.getOutputURL());
    }

    @Test
    public void testGetOutputURL_qualityVariant() {
        MoQMuxer variantMuxer = new MoQMuxer(mock(Vertx.class), "stream1", 720, "live", "http://localhost:4443/moq");
        assertEquals("moq://live/stream1/720p", variantMuxer.getOutputURL());
    }

    @Test
    public void testIsCodecSupported_supported() {
        assertTrue(muxer.isCodecSupported(AV_CODEC_ID_H264));
        assertTrue(muxer.isCodecSupported(AV_CODEC_ID_H265));
        assertTrue(muxer.isCodecSupported(AV_CODEC_ID_AAC));
        assertTrue(muxer.isCodecSupported(AV_CODEC_ID_OPUS));
    }

    @Test
    public void testIsCodecSupported_unsupported() {
        assertFalse(muxer.isCodecSupported(AV_CODEC_ID_VP8));
        assertFalse(muxer.isCodecSupported(AV_CODEC_ID_NONE));
    }

    @Test
    public void testGetCliErrorStream_noProcessStarted_returnsNull() {
        assertNull(muxer.getCliErrorStream());
    }

    @Test
    public void testExtractAnnexBSPSPPS_validInput_returnsExtradata() throws Exception {
        // Build synthetic Annex B buffer with SPS (nal_unit_type=7) and PPS (nal_unit_type=8)
        // SPS: 00 00 00 01 67 42 00 0A DA (nal_unit_type = 0x67 & 0x1F = 7)
        // PPS: 00 00 00 01 68 CE 38 80    (nal_unit_type = 0x68 & 0x1F = 8)
        byte[] annexB = new byte[] {
            0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x0A, (byte) 0xDA,
            0x00, 0x00, 0x00, 0x01, 0x68, (byte) 0xCE, 0x38, (byte) 0x80
        };

        Method method = MoQMuxer.class.getDeclaredMethod("extractAnnexBSPSPPS", byte[].class);
        method.setAccessible(true);
        byte[] result = (byte[]) method.invoke(muxer, (Object) annexB);

        assertNotNull(result);
        // First byte should be 0x00 (Annex B format marker)
        assertEquals(0x00, result[0]);
    }

    @Test
    public void testExtractAnnexBSPSPPS_emptyInput_returnsNull() throws Exception {
        Method method = MoQMuxer.class.getDeclaredMethod("extractAnnexBSPSPPS", byte[].class);
        method.setAccessible(true);
        byte[] result = (byte[]) method.invoke(muxer, (Object) new byte[0]);

        assertNull(result);
    }

    @Test
    public void testExtractAnnexBSPSPPS_onlySps_returnsNull() throws Exception {
        // Buffer with only SPS, no PPS
        byte[] annexB = new byte[] {
            0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x0A, (byte) 0xDA
        };

        Method method = MoQMuxer.class.getDeclaredMethod("extractAnnexBSPSPPS", byte[].class);
        method.setAccessible(true);
        byte[] result = (byte[]) method.invoke(muxer, (Object) annexB);

        assertNull(result);
    }
}
