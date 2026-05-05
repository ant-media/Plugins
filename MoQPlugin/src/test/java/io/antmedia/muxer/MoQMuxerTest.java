package io.antmedia.muxer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

import io.vertx.core.Vertx;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avformat.Write_packet_Pointer_BytePointer_int;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.BytePointer;
import org.junit.Test;

import static org.bytedeco.ffmpeg.global.avformat.avformat_new_stream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

public class MoQMuxerTest {

    private MoQMuxer newMuxer(int height) {
        return new MoQMuxer(mock(Vertx.class), "stream1", height, "live", "http://localhost:4443/moq");
    }

    @Test
    public void testFreshMuxer() throws Exception {
        assertEquals("moq://live/stream1/source", newMuxer(0).getOutputURL());
        assertEquals("moq://live/stream1/720p", newMuxer(720).getOutputURL());

        // No process attached: getCliErrorStream returns null
        MoQMuxer muxer = newMuxer(0);
        assertNull(muxer.getCliErrorStream());

        // After injecting a process: returns its stderr
        Process moq = mock(Process.class);
        InputStream stderr = new ByteArrayInputStream(new byte[0]);
        when(moq.getErrorStream()).thenReturn(stderr);
        setField(muxer, "moqCliProcess", moq);
        assertSame(stderr, muxer.getCliErrorStream());
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

        // Output: 00 00 00 01 <sps> 00 00 00 01 <pps>. The leading 0x00 (not 0x01) is what
        // FFmpeg's movenc.c uses to detect Annex B and trigger ff_nal_parse_units conversion.
        byte[] sps4 = { 0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x0A, (byte) 0xDA };
        byte[] pps4 = { 0x00, 0x00, 0x00, 0x01, 0x68, (byte) 0xCE, 0x38, (byte) 0x80 };
        byte[] expected = {
                0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x0A, (byte) 0xDA,
                0x00, 0x00, 0x00, 0x01, 0x68, (byte) 0xCE, 0x38, (byte) 0x80
        };
        assertArrayEquals(expected, (byte[]) m.invoke(muxer, (Object) concat(sps4, pps4)));

        // 3-byte start codes are normalised to 4-byte in the output
        byte[] sps3 = { 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x0A, (byte) 0xDA };
        byte[] pps3 = { 0x00, 0x00, 0x01, 0x68, (byte) 0xCE, 0x38, (byte) 0x80 };
        assertArrayEquals(expected, (byte[]) m.invoke(muxer, (Object) concat(sps3, pps3)));

        // Other NAL types (here SEI, type=6) interleaved before SPS/PPS are ignored
        byte[] sei = { 0x00, 0x00, 0x00, 0x01, 0x06, 0x05, 0x10, 0x20 };
        assertArrayEquals(expected, (byte[]) m.invoke(muxer, (Object) concat(sei, sps4, pps4)));

        // Negative cases all return an empty array, not null
        assertEquals(0, ((byte[]) m.invoke(muxer, (Object) new byte[0])).length);
        assertEquals(0, ((byte[]) m.invoke(muxer, (Object) sps4)).length);
        assertEquals(0, ((byte[]) m.invoke(muxer, (Object) pps4)).length);
        assertEquals(0, ((byte[]) m.invoke(muxer, (Object) new byte[] { 0x67, 0x42, 0x00, 0x0A })).length);
    }

    @Test
    public void testBuildOpusHead() {
        // Defaults applied when inputs are <= 0
        byte[] defaults = MoQMuxer.buildOpusHead(0, 0);
        assertEquals(19, defaults.length);
        assertArrayEquals(new byte[] {'O','p','u','s','H','e','a','d'}, Arrays.copyOf(defaults, 8));
        assertEquals(1, defaults[8]);
        assertEquals(2, defaults[9]);
        assertEquals(0x38, defaults[10] & 0xFF);
        assertEquals(0x01, defaults[11] & 0xFF);
        assertEquals(48000, intLE(defaults, 12));
        assertEquals(0, defaults[16]);
        assertEquals(0, defaults[17]);
        assertEquals(0, defaults[18]);

        byte[] mono16k = MoQMuxer.buildOpusHead(1, 16000);
        assertEquals(1, mono16k[9]);
        assertEquals(16000, intLE(mono16k, 12));

        byte[] surround = MoQMuxer.buildOpusHead(6, 48000);
        assertEquals(6, surround[9]);
        assertEquals(48000, intLE(surround, 12));
    }

    @Test
    public void testAddStream() throws Exception {
        AVCodecParameters p = new AVCodecParameters();
        AVRational tb = new AVRational();
        tb.num(1).den(90000);

        // Video + outIdx in map -> sets videoOutStreamIdx
        MoQMuxer m1 = spy(newMuxer(0));
        p.codec_type(AVMEDIA_TYPE_VIDEO);
        doReturn(true).when(m1).callSuperAddStream(any(), any(), anyInt());
        m1.inputOutputStreamIndexMap.put(5, 7);
        assertTrue(m1.addStream(p, tb, 5));
        assertEquals(7, getInt(m1, "videoOutStreamIdx"));

        // Video without outIdx -> field stays at default
        MoQMuxer m2 = spy(newMuxer(0));
        setInt(m2, "videoOutStreamIdx", -1);
        doReturn(true).when(m2).callSuperAddStream(any(), any(), anyInt());
        assertTrue(m2.addStream(p, tb, 5));
        assertEquals(-1, getInt(m2, "videoOutStreamIdx"));

        // Audio: never touches videoOutStreamIdx, even with outIdx in map
        MoQMuxer m3 = spy(newMuxer(0));
        setInt(m3, "videoOutStreamIdx", -1);
        p.codec_type(AVMEDIA_TYPE_AUDIO);
        doReturn(true).when(m3).callSuperAddStream(any(), any(), anyInt());
        m3.inputOutputStreamIndexMap.put(0, 0);
        assertTrue(m3.addStream(p, tb, 0));
        assertEquals(-1, getInt(m3, "videoOutStreamIdx"));

        // Super fails -> returns false
        MoQMuxer m4 = spy(newMuxer(0));
        p.codec_type(AVMEDIA_TYPE_VIDEO);
        doReturn(false).when(m4).callSuperAddStream(any(), any(), anyInt());
        assertFalse(m4.addStream(p, tb, 5));

        p.close(); tb.close();
    }

    @Test
    public void testAddVideoStream_superFails_returnsFalse() {
        MoQMuxer muxer = spy(newMuxer(0));
        AVRational tb = new AVRational();
        tb.num(1).den(90000);
        doReturn(false).when(muxer).callSuperAddVideoStream(
                anyInt(), anyInt(), any(), anyInt(), anyInt(), anyBoolean(), any());

        assertFalse(muxer.addVideoStream(1920, 1080, tb, AV_CODEC_ID_H264, 0, true, null));
        tb.close();
    }

    @Test
    public void testAddVideoStream_copiesExtradataFromCodecpar() throws Exception {
        MoQMuxer muxer = spy(newMuxer(0));
        AVFormatContext ctx = muxer.getOutputFormatContext();
        avformat_new_stream(ctx, null); // output stream at index 0
        muxer.inputOutputStreamIndexMap.put(3, 0);
        doReturn(true).when(muxer).callSuperAddVideoStream(
                anyInt(), anyInt(), any(), anyInt(), anyInt(), anyBoolean(), any());

        // codecpar carrying extradata -> the success branch copies it onto the output stream
        byte[] data = { 1, 2, 3, 4, 5 };
        AVCodecParameters cp = new AVCodecParameters();
        BytePointer ed = new BytePointer(data);
        cp.extradata(ed);
        cp.extradata_size(data.length);

        AVRational tb = new AVRational(); tb.num(1).den(90000);
        assertTrue(muxer.addVideoStream(1920, 1080, tb, AV_CODEC_ID_H264, 3, true, cp));
        assertEquals(0, getInt(muxer, "videoOutStreamIdx"));
        assertEquals(data.length, ctx.streams(0).codecpar().extradata_size());

        tb.close(); cp.close(); ed.close();
    }

    @Test
    public void testAddVideoStream_nullCodecpar_takesWarnBranch() {
        // null codecpar exercises the else (warn) branch; super=true keeps overall result true
        MoQMuxer muxer = spy(newMuxer(0));
        avformat_new_stream(muxer.getOutputFormatContext(), null);
        muxer.inputOutputStreamIndexMap.put(0, 0);
        doReturn(true).when(muxer).callSuperAddVideoStream(
                anyInt(), anyInt(), any(), anyInt(), anyInt(), anyBoolean(), any());

        AVRational tb = new AVRational(); tb.num(1).den(90000);
        assertTrue(muxer.addVideoStream(1920, 1080, tb, AV_CODEC_ID_H264, 0, true, null));
        tb.close();
    }

    @Test
    public void testAddAudioStream() {
        // AAC: super=true -> no OpusHead path executed
        MoQMuxer aac = spy(newMuxer(0));
        doReturn(true).when(aac).callSuperAddAudioStream(anyInt(), any(), anyInt(), anyInt());
        assertTrue(aac.addAudioStream(48000, null, AV_CODEC_ID_AAC, 0));

        // Opus + no outIdx in map -> warns and skips extradata setup
        MoQMuxer opusNoMap = spy(newMuxer(0));
        doReturn(true).when(opusNoMap).callSuperAddAudioStream(anyInt(), any(), anyInt(), anyInt());
        assertTrue(opusNoMap.addAudioStream(48000, null, AV_CODEC_ID_OPUS, 0));

        // Opus + outIdx in map -> OpusHead synthesised and stored as 19-byte extradata
        MoQMuxer opusInMap = spy(newMuxer(0));
        AVFormatContext ctx = opusInMap.getOutputFormatContext();
        avformat_new_stream(ctx, null); // index 0
        opusInMap.inputOutputStreamIndexMap.put(0, 0);
        doReturn(true).when(opusInMap).callSuperAddAudioStream(anyInt(), any(), anyInt(), anyInt());
        assertTrue(opusInMap.addAudioStream(48000, null, AV_CODEC_ID_OPUS, 0));
        assertEquals(19, ctx.streams(0).codecpar().extradata_size());

        // Super fails -> returns false (whatever the codec)
        MoQMuxer fail = spy(newMuxer(0));
        doReturn(false).when(fail).callSuperAddAudioStream(anyInt(), any(), anyInt(), anyInt());
        assertFalse(fail.addAudioStream(48000, null, AV_CODEC_ID_OPUS, 0));
    }

    @Test
    public void testWritePacket() throws Exception {
        AVRational tb = new AVRational();
        tb.num(1).den(90000);

        // Without header: audio + non-key video are dropped, only keyframes pass
        MoQMuxer noHeader = spy(newMuxer(0));
        doNothing().when(noHeader).callSuperWritePacket(any(), any(), any(), anyInt());

        AVPacket audio = new AVPacket();
        noHeader.writePacket(audio, tb, tb, AVMEDIA_TYPE_AUDIO);
        AVPacket nonKey = new AVPacket();
        nonKey.flags(0);
        noHeader.writePacket(nonKey, tb, tb, AVMEDIA_TYPE_VIDEO);
        verify(noHeader, never()).callSuperWritePacket(any(), any(), any(), anyInt());

        AVPacket key = new AVPacket();
        key.flags(AV_PKT_FLAG_KEY);
        noHeader.writePacket(key, tb, tb, AVMEDIA_TYPE_VIDEO);
        verify(noHeader).callSuperWritePacket(eq(key), any(), any(), eq(AVMEDIA_TYPE_VIDEO));

        // With header: every packet passes through, regardless of type or keyframe flag
        MoQMuxer withHeader = spy(newMuxer(0));
        setBoolean(withHeader, "headerWritten", true);
        doNothing().when(withHeader).callSuperWritePacket(any(), any(), any(), anyInt());

        AVPacket audio2 = new AVPacket();
        withHeader.writePacket(audio2, tb, tb, AVMEDIA_TYPE_AUDIO);
        AVPacket nonKey2 = new AVPacket();
        nonKey2.flags(0);
        withHeader.writePacket(nonKey2, tb, tb, AVMEDIA_TYPE_VIDEO);
        verify(withHeader).callSuperWritePacket(eq(audio2), any(), any(), eq(AVMEDIA_TYPE_AUDIO));
        verify(withHeader).callSuperWritePacket(eq(nonKey2), any(), any(), eq(AVMEDIA_TYPE_VIDEO));

        audio.close(); nonKey.close(); key.close(); audio2.close(); nonKey2.close(); tb.close();
    }

    @Test
    public void testWriteVideoFrame() throws Exception {
        Method m = MoQMuxer.class.getDeclaredMethod("writeVideoFrame", AVPacket.class, AVFormatContext.class);
        m.setAccessible(true);
        AVPacket pkt = new AVPacket();

        // No header + no outIdx -> early return, super never called
        MoQMuxer noOutIdx = spy(newMuxer(0));
        setInt(noOutIdx, "videoOutStreamIdx", -1);
        m.invoke(noOutIdx, pkt, null);
        verify(noOutIdx, never()).callSuperWriteVideoFrame(any(), any());

        // Header already written -> jumps straight to super, context can be null
        MoQMuxer headerWritten = spy(newMuxer(0));
        setBoolean(headerWritten, "headerWritten", true);
        doNothing().when(headerWritten).callSuperWriteVideoFrame(any(), any());
        m.invoke(headerWritten, pkt, null);
        verify(headerWritten).callSuperWriteVideoFrame(eq(pkt), isNull());

        pkt.close();
    }

    @Test
    public void testWriteTrailer_branchesOnHeaderWritten() throws Exception {
        // No header -> clearResource path
        MoQMuxer noHeader = spy(newMuxer(0));
        setBoolean(noHeader, "headerWritten", false);
        doNothing().when(noHeader).callSuperWriteTrailer();
        doNothing().when(noHeader).callSuperClearResource();
        noHeader.writeTrailer();
        verify(noHeader).callSuperClearResource();
        verify(noHeader, never()).callSuperWriteTrailer();

        // Header written -> writeTrailer path
        MoQMuxer withHeader = spy(newMuxer(0));
        setBoolean(withHeader, "headerWritten", true);
        doNothing().when(withHeader).callSuperWriteTrailer();
        doNothing().when(withHeader).callSuperClearResource();
        withHeader.writeTrailer();
        verify(withHeader).callSuperWriteTrailer();
        verify(withHeader, never()).callSuperClearResource();
    }

    @Test
    public void testWriteTrailer_destroysCliProcessAndJoinsDrainThread() throws Exception {
        MoQMuxer muxer = spy(newMuxer(0));
        setBoolean(muxer, "headerWritten", false);
        doNothing().when(muxer).callSuperWriteTrailer();
        doNothing().when(muxer).callSuperClearResource();

        Process moq = mock(Process.class);
        when(moq.waitFor(anyLong(), any())).thenReturn(false); // wait times out -> destroy() invoked
        setField(muxer, "moqCliProcess", moq);

        Thread drain = new Thread(() -> {}, "drain");
        drain.start();
        setField(muxer, "drainThread", drain);

        muxer.writeTrailer();

        verify(moq).destroy();
        assertNull(getField(muxer, "drainThread"));
        assertNull(getField(muxer, "moqCliProcess"));
    }

    @Test
    public void testPrepareIO() {
        // Success: openIO returns true -> running flags set
        MoQMuxer ok = spy(newMuxer(0));
        doNothing().when(ok).startMoqCli();
        doReturn(true).when(ok).openIO();

        assertTrue(ok.prepareIO());
        verify(ok).startMoqCli();
        verify(ok).openIO();
        try {
            assertTrue(getBoolean(ok, "running"));
        } catch (Exception e) { throw new RuntimeException(e); }
        assertTrue(ok.isRunning.get());

        // Failure: openIO returns false -> prepareIO returns false
        MoQMuxer bad = spy(newMuxer(0));
        doNothing().when(bad).startMoqCli();
        doReturn(false).when(bad).openIO();
        assertFalse(bad.prepareIO());
    }

    @Test
    public void testGetOutputFormatContext_allocatesAndCaches() {
        MoQMuxer muxer = newMuxer(0);
        AVFormatContext first = muxer.getOutputFormatContext();
        assertNotNull(first);
        assertSame(first, muxer.getOutputFormatContext());
    }

    @Test
    public void testOpenIO_allocatesAvioContext() throws Exception {
        MoQMuxer muxer = newMuxer(0);
        assertTrue(muxer.openIO());
        assertNotNull(getField(muxer, "avioContext"));
        assertNotNull(getField(muxer, "opaque"));
    }

    @Test
    public void testStartMoqCli() throws Exception {
        // Spawn succeeds -> drain thread started, process stored
        MoQMuxer ok = spy(newMuxer(0));
        Process moq = mock(Process.class);
        OutputStream stdin = new ByteArrayOutputStream();
        when(moq.getOutputStream()).thenReturn(stdin);
        doReturn(moq).when(ok).spawnMoqCli();
        doNothing().when(ok).startDrainThread(any());

        ok.startMoqCli();
        assertSame(moq, getField(ok, "moqCliProcess"));
        verify(ok).startDrainThread(stdin);

        // Spawn throws -> exception swallowed, no drain thread
        MoQMuxer bad = spy(newMuxer(0));
        doThrow(new IOException("moq-cli not found")).when(bad).spawnMoqCli();
        bad.startMoqCli();
        assertNull(getField(bad, "moqCliProcess"));
        verify(bad, never()).startDrainThread(any());
    }

    @Test
    public void testWriteCallback() throws Exception {
        MoQMuxer muxer = newMuxer(0);

        // Pull the static callback and the instances map by reflection
        Field cbField = MoQMuxer.class.getDeclaredField("writeCallback");
        cbField.setAccessible(true);
        Write_packet_Pointer_BytePointer_int callback = (Write_packet_Pointer_BytePointer_int) cbField.get(null);

        Field instancesField = MoQMuxer.class.getDeclaredField("instances");
        instancesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<BytePointer, MoQMuxer> instances = (Map<BytePointer, MoQMuxer>) instancesField.get(null);

        BytePointer opaque = new BytePointer("test-opaque");

        // Unknown opaque -> returns size, no queue side effect
        BytePointer buf = new BytePointer("hello".getBytes());
        assertEquals(5, callback.call(opaque, buf, 5));

        // Registered opaque -> bytes copied to queue, returns size
        instances.put(opaque, muxer);
        assertEquals(5, callback.call(opaque, buf, 5));
        @SuppressWarnings("unchecked")
        ArrayBlockingQueue<byte[]> queue = (ArrayBlockingQueue<byte[]>) getField(muxer, "queue");
        assertArrayEquals("hello".getBytes(), queue.poll());

        // Queue full -> still returns size, drops chunk silently
        for (int i = 0; i < 64; i++) queue.offer(new byte[1]); // QUEUE_CAPACITY
        assertEquals(5, callback.call(opaque, buf, 5));

        instances.remove(opaque);
    }

    @Test
    public void testSetExtradata_replacesExistingPointer() throws Exception {
        MoQMuxer muxer = newMuxer(0);
        AVFormatContext ctx = muxer.getOutputFormatContext();
        AVStream stream = avformat_new_stream(ctx, null);
        Method m = MoQMuxer.class.getDeclaredMethod("setExtradata", AVStream.class, byte[].class);
        m.setAccessible(true);

        m.invoke(muxer, stream, new byte[] { 1, 2, 3, 4 });
        assertEquals(4, stream.codecpar().extradata_size());

        // Second call: old pointer is freed (no leak), new one installed
        m.invoke(muxer, stream, new byte[] { 5, 6, 7 });
        assertEquals(3, stream.codecpar().extradata_size());
    }

    @Test
    public void testWriteVideoFrame_noExtradataAndExtractFails_returnsEarly() throws Exception {
        MoQMuxer muxer = spy(newMuxer(0));
        AVFormatContext ctx = muxer.getOutputFormatContext();
        avformat_new_stream(ctx, null); // creates output stream at index 0
        setInt(muxer, "videoOutStreamIdx", 0);
        setBoolean(muxer, "headerWritten", false);

        // Frame contains no SPS/PPS, so extractAnnexBSPSPPS returns null and the second
        // extradata-size check still sees 0 -> early return before avformat_write_header
        AVPacket pkt = new AVPacket();
        BytePointer data = new BytePointer("garbage-not-a-nal".getBytes());
        pkt.data(data);
        pkt.size((int) data.limit());

        Method m = MoQMuxer.class.getDeclaredMethod("writeVideoFrame", AVPacket.class, AVFormatContext.class);
        m.setAccessible(true);
        m.invoke(muxer, pkt, ctx);

        verify(muxer, never()).callSuperWriteVideoFrame(any(), any());
        pkt.close();
    }

    @Test
    public void testStartDrainThread_writesQueuedChunksAndCloses() throws Exception {
        MoQMuxer muxer = newMuxer(0);
        @SuppressWarnings("unchecked")
        ArrayBlockingQueue<byte[]> queue = (ArrayBlockingQueue<byte[]>) getField(muxer, "queue");
        queue.put(new byte[] { 1, 2, 3 });
        queue.put(new byte[] { 4, 5 });

        // running flag is false (default), so the drain loop exits as soon as the queue empties
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        muxer.startDrainThread(sink);

        Thread t = getField(muxer, "drainThread");
        t.join(2000);
        assertFalse(t.isAlive());
        assertArrayEquals(new byte[] { 1, 2, 3, 4, 5 }, sink.toByteArray());
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, out, pos, a.length);
            pos += a.length;
        }
        return out;
    }

    private static int intLE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
             | ((data[offset+1] & 0xFF) << 8)
             | ((data[offset+2] & 0xFF) << 16)
             | ((data[offset+3] & 0xFF) << 24);
    }

    private static int getInt(Object t, String name) throws Exception {
        return walkField(t, name).getInt(t);
    }
    private static boolean getBoolean(Object t, String name) throws Exception {
        return walkField(t, name).getBoolean(t);
    }
    private static void setInt(Object t, String name, int v) throws Exception {
        walkField(t, name).setInt(t, v);
    }
    private static void setBoolean(Object t, String name, boolean v) throws Exception {
        walkField(t, name).setBoolean(t, v);
    }
    @SuppressWarnings("unchecked")
    private static <T> T getField(Object t, String name) throws Exception {
        return (T) walkField(t, name).get(t);
    }
    private static void setField(Object t, String name, Object v) throws Exception {
        walkField(t, name).set(t, v);
    }
    private static Field walkField(Object t, String name) throws NoSuchFieldException {
        Class<?> c = t.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
