package io.antmedia.muxer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

import io.antmedia.plugin.MoqBinaries;
import io.antmedia.plugin.MoqTestBinary;
import io.vertx.core.Vertx;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avformat.Write_packet_Pointer_BytePointer_int;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MoQMuxerTest {

    private MoQMuxer newMuxer(int height) {
        return new MoQMuxer(mock(Vertx.class), "stream1", height, "live", "http://localhost:4443/moq", true);
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

        // A buffer that ends on a bare start code: the scan must stop, not read past the end
        assertEquals(0, ((byte[]) m.invoke(muxer, (Object) new byte[] { 0x00, 0x00, 0x00, 0x01 })).length);

        // A truncated SPS (< 4 bytes of payload) is rejected, so no half-parsed extradata escapes
        byte[] shortSps = { 0x00, 0x00, 0x00, 0x01, 0x67, 0x42 };
        assertEquals(0, ((byte[]) m.invoke(muxer, (Object) concat(shortSps, pps4))).length);

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

        // AAC registers the ADTS to ASC filter; other codecs do not
        MoQMuxer m5 = spy(newMuxer(0));
        p.codec_type(AVMEDIA_TYPE_AUDIO);
        p.codec_id(AV_CODEC_ID_AAC);
        doReturn(true).when(m5).callSuperAddStream(any(), any(), anyInt());
        assertTrue(m5.addStream(p, tb, 1));
        assertTrue(m5.getBsfAudioNames().contains("aac_adtstoasc"));

        MoQMuxer m6 = spy(newMuxer(0));
        p.codec_id(AV_CODEC_ID_OPUS);
        doReturn(true).when(m6).callSuperAddStream(any(), any(), anyInt());
        assertTrue(m6.addStream(p, tb, 1));
        assertTrue(m6.getBsfAudioNames().isEmpty());

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
    public void testPrepareIO() throws Exception {
        // Success: openIO returns true -> running flags set
        MoQMuxer ok = spy(newMuxer(0));
        doNothing().when(ok).startMoqCli();
        doReturn(true).when(ok).openIO();

        assertTrue(ok.prepareIO());
        verify(ok).startMoqCli();
        verify(ok).openIO();
        assertTrue(getBoolean(ok, "running"));
        assertTrue(ok.isRunning.get());

        // Failure: openIO returns false -> prepareIO returns false and nothing is left running.
        // MuxAdaptor drops a muxer whose prepareIO fails without calling writeTrailer, so a moq
        // process or drain thread started here would never be reaped.
        MoQMuxer bad = spy(newMuxer(0));
        doReturn(false).when(bad).openIO();

        assertFalse(bad.prepareIO());
        verify(bad, never()).startMoqCli();
        assertNull("no moq process may survive a failed prepareIO", getField(bad, "moqCliProcess"));
        assertNull("no drain thread may survive a failed prepareIO", getField(bad, "drainThread"));
        assertFalse(getBoolean(bad, "running"));
        assertFalse(bad.isRunning.get());
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
    @SuppressWarnings("unchecked")
    public void testOpenIO_nullFormatContext_failsWithoutNpeOrRegistering() throws Exception {
        MoQMuxer muxer = spy(newMuxer(0));
        doReturn(null).when(muxer).getOutputFormatContext();

        assertFalse("a null format context must fail cleanly, not NPE", muxer.openIO());

        Field f = MoQMuxer.class.getDeclaredField("instances");
        f.setAccessible(true);
        Map<BytePointer, MoQMuxer> instances = (Map<BytePointer, MoQMuxer>) f.get(null);
        assertFalse("a muxer that failed to open must not stay in the static map",
                instances.containsValue(muxer));
    }

    @Test
    public void testBuildMoqCliCommand() {
        // newMuxer passes tlsDisableVerify=true, as the embedded self-signed relay needs
        java.util.List<String> cmd = newMuxer(0).buildMoqCliCommand();

        assertEquals("http://localhost:4443/moq", cmd.get(cmd.indexOf("--client-connect") + 1));
        assertEquals(MoqBinaries.CLIENT_BIND, cmd.get(cmd.indexOf("--client-bind") + 1));
        assertEquals("live/stream1/source", cmd.get(cmd.indexOf("--broadcast") + 1));
        assertTrue("--client-tls-disable-verify added for the embedded relay",
                cmd.contains("--client-tls-disable-verify"));
        assertEquals("import is the verb", "import", cmd.get(cmd.size() - 2));
        assertEquals("fmp4", cmd.get(cmd.size() - 1));
    }

    @Test
    public void testBuildMoqCliCommand_externalTarget_omitsTlsDisableVerify() {
        MoQMuxer muxer = new MoQMuxer(mock(Vertx.class), "stream1", 720, "live",
                "https://cdn.example.com/token", false);
        java.util.List<String> cmd = muxer.buildMoqCliCommand();

        assertFalse("a real certificate must still be verified",
                cmd.contains("--client-tls-disable-verify"));
        assertEquals("https://cdn.example.com/token", cmd.get(cmd.indexOf("--client-connect") + 1));
        assertEquals("live/stream1/720p", cmd.get(cmd.indexOf("--broadcast") + 1));
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
        doThrow(new IOException("moq not found")).when(bad).spawnMoqCli();
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
        BytePointer buf = new BytePointer("hello".getBytes());
        ArrayBlockingQueue<byte[]> queue = getField(muxer, "queue");
        try {
            // Unknown opaque -> returns size, no queue side effect
            assertEquals(5, callback.call(opaque, buf, 5));
            assertTrue("an unregistered opaque must not reach any queue", queue.isEmpty());

            // Registered opaque -> bytes copied to queue, returns size
            instances.put(opaque, muxer);
            assertEquals(5, callback.call(opaque, buf, 5));
            assertArrayEquals("hello".getBytes(), queue.poll());

            // Queue full -> the chunk is dropped, but FFmpeg is still told every byte was taken,
            // otherwise movenc aborts the whole muxer on a transient backlog.
            for (int i = 0; i < 64; i++) queue.offer(new byte[1]); // QUEUE_CAPACITY
            assertEquals(5, callback.call(opaque, buf, 5));
            assertEquals("the chunk must be dropped, not queued past capacity", 64, queue.size());
        } finally {
            instances.remove(opaque);
        }
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

        // Frame contains no SPS/PPS, so extractAnnexBSPSPPS returns an empty array and the
        // second extradata-size check still sees 0 -> early return before avformat_write_header
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

    /** A real baseline SPS/PPS pair: movenc parses these, so avformat_write_header actually succeeds. */
    private static final byte[] SPS_PPS_ANNEXB = {
            0x00, 0x00, 0x00, 0x01, 0x67, 0x42, 0x00, 0x0A, (byte) 0xF8, 0x41, (byte) 0xA2,
            0x00, 0x00, 0x00, 0x01, 0x68, (byte) 0xCE, 0x38, (byte) 0x80
    };

    private static AVStream addH264OutStream(MoQMuxer muxer, int codecId) {
        AVStream out = avformat_new_stream(muxer.getOutputFormatContext(), null);
        out.codecpar().codec_type(AVMEDIA_TYPE_VIDEO);
        out.codecpar().codec_id(codecId);
        out.codecpar().width(176);
        out.codecpar().height(144);
        out.time_base().num(1);
        out.time_base().den(90000);
        return out;
    }

    private static void invokeWriteVideoFrame(MoQMuxer muxer, AVPacket pkt, AVFormatContext ctx) throws Exception {
        Method m = MoQMuxer.class.getDeclaredMethod("writeVideoFrame", AVPacket.class, AVFormatContext.class);
        m.setAccessible(true);
        m.invoke(muxer, pkt, ctx);
    }

    private static AVPacket keyframe(byte[] payload) {
        AVPacket pkt = new AVPacket();
        pkt.data(new BytePointer(payload));
        pkt.size(payload.length);
        pkt.flags(AV_PKT_FLAG_KEY);
        return pkt;
    }

    @Test
    public void testAddStreams_againstTheRealMuxer() throws Exception {
        // No stubbing of the callSuper* seams: this is the wiring the plugin actually runs.
        MoQMuxer muxer = newMuxer(0);
        AVRational tb = new AVRational(); tb.num(1).den(90000);

        // Video with extradata already on codecpar (the RTMP/SRT path)
        byte[] avcc = { 1, 0x42, 0, 0x0A, (byte) 0xFF };
        AVCodecParameters videoPar = new AVCodecParameters();
        BytePointer ed = new BytePointer(avcc);
        videoPar.extradata(ed);
        videoPar.extradata_size(avcc.length);
        assertTrue(muxer.addVideoStream(176, 144, tb, AV_CODEC_ID_H264, 0, true, videoPar));
        assertEquals("the real Muxer must have registered the output index",
                0, getInt(muxer, "videoOutStreamIdx"));
        assertArrayEquals("extradata must be copied onto the output stream",
                avcc, extradataOf(muxer.getOutputFormatContext().streams(0)));

        // Video whose codecpar carries no extradata (the WebRTC path): accepted, extraction deferred
        MoQMuxer webrtc = newMuxer(0);
        AVCodecParameters emptyPar = new AVCodecParameters();
        assertTrue(webrtc.addVideoStream(176, 144, tb, AV_CODEC_ID_H264, 0, true, emptyPar));
        assertEquals(0, webrtc.getOutputFormatContext().streams(0).codecpar().extradata_size());

        // Opus audio with a real channel layout: OpusHead is synthesised from it
        AVChannelLayout stereo = new AVChannelLayout();
        av_channel_layout_default(stereo, 2);
        assertTrue(muxer.addAudioStream(48000, stereo, AV_CODEC_ID_OPUS, 1));
        byte[] opusHead = extradataOf(muxer.getOutputFormatContext().streams(1));
        assertArrayEquals(MoQMuxer.buildOpusHead(2, 48000), opusHead);

        // Direct-muxing addStream for AAC: registers the ADTS-to-ASC filter and copies the params
        MoQMuxer direct = newMuxer(0);
        AVCodecParameters aacPar = new AVCodecParameters();
        aacPar.codec_type(AVMEDIA_TYPE_AUDIO);
        aacPar.codec_id(AV_CODEC_ID_AAC);
        aacPar.sample_rate(48000);
        av_channel_layout_default(aacPar.ch_layout(), 2);
        assertTrue(direct.addStream(aacPar, tb, 0));
        assertTrue(direct.getBsfAudioNames().contains("aac_adtstoasc"));

        // An unsupported codec is refused by the real Muxer, so no muxer is wired up for it
        MoQMuxer vp8 = newMuxer(0);
        assertFalse(vp8.addVideoStream(176, 144, tb, AV_CODEC_ID_VP8, 0, true, null));

        tb.close(); videoPar.close(); emptyPar.close(); ed.close(); stereo.close(); aacPar.close();
    }

    private static byte[] extradataOf(AVStream stream) {
        byte[] out = new byte[stream.codecpar().extradata_size()];
        stream.codecpar().extradata().get(out, 0, out.length);
        return out;
    }

    @Test
    public void testWriteVideoFrame_extradataAlreadySet_skipsKeyframeExtraction() throws Exception {
        // RTMP/SRT path: addStream copied AVCC extradata, so the keyframe carries no SPS/PPS
        // and must not need to. The header still has to be written on that first keyframe.
        MoQMuxer muxer = spy(newMuxer(0));
        doNothing().when(muxer).startMoqCli();
        doNothing().when(muxer).callSuperWriteVideoFrame(any(), any());

        AVFormatContext ctx = muxer.getOutputFormatContext();
        AVStream out = addH264OutStream(muxer, AV_CODEC_ID_H264);
        Method setExtradata = MoQMuxer.class.getDeclaredMethod("setExtradata", AVStream.class, byte[].class);
        setExtradata.setAccessible(true);
        setExtradata.invoke(muxer, out, new byte[] { 1, 0x42, 0x00, 0x0A, (byte) 0xFF, (byte) 0xE1,
                0x00, 0x07, 0x67, 0x42, 0x00, 0x0A, (byte) 0xF8, 0x41, (byte) 0xA2,
                0x01, 0x00, 0x04, 0x68, (byte) 0xCE, 0x38, (byte) 0x80 });
        setInt(muxer, "videoOutStreamIdx", out.index());
        assertTrue(muxer.prepareIO());

        AVPacket pkt = keyframe(new byte[] { 0x00, 0x00, 0x00, 0x01, 0x65, 0x11, 0x22, 0x33 }); // IDR slice only
        invokeWriteVideoFrame(muxer, pkt, ctx);

        assertTrue(getBoolean(muxer, "headerWritten"));
        verify(muxer).callSuperWriteVideoFrame(eq(pkt), eq(ctx));
        pkt.close();
    }

    @Test
    public void testDrainLoop_keepsPumpingWhileRunning() throws Exception {
        MoQMuxer muxer = newMuxer(0);
        setBoolean(muxer, "running", true);

        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        muxer.startDrainThread(sink);
        Thread t = getField(muxer, "drainThread");

        // Empty queue: the loop must idle on poll(), not exit while still running
        Thread.sleep(300);
        assertTrue("the drain thread must stay up while running", t.isAlive());

        ArrayBlockingQueue<byte[]> queue = getField(muxer, "queue");
        queue.put(new byte[] { 9, 8 });
        setBoolean(muxer, "running", false);

        t.join(3000);
        assertFalse(t.isAlive());
        assertArrayEquals("chunks queued before shutdown must still be flushed",
                new byte[] { 9, 8 }, sink.toByteArray());
    }

    @Test
    public void testWriteTrailer_cliExitsOnItsOwn_isNotDestroyed() throws Exception {
        MoQMuxer muxer = spy(newMuxer(0));
        doNothing().when(muxer).callSuperClearResource();

        Process moq = mock(Process.class);
        when(moq.waitFor(anyLong(), any())).thenReturn(true); // closing stdin was enough
        setField(muxer, "moqCliProcess", moq);

        muxer.writeTrailer();

        verify(moq, never()).destroy();
        assertNull(getField(muxer, "moqCliProcess"));
    }

    @Test
    public void testWriteVideoFrame_firstKeyframeSetsExtradataAndWritesHeader() throws Exception {
        MoQMuxer muxer = spy(newMuxer(0));
        doNothing().when(muxer).startMoqCli();
        doNothing().when(muxer).callSuperWriteVideoFrame(any(), any());

        AVFormatContext ctx = muxer.getOutputFormatContext();
        AVStream out = addH264OutStream(muxer, AV_CODEC_ID_H264);
        setInt(muxer, "videoOutStreamIdx", out.index());

        // prepareIO installs the CMAF movflags and wires the custom AVIO to the queue
        assertTrue(muxer.prepareIO());
        assertFalse("header must be deferred, not written by prepareIO", getBoolean(muxer, "headerWritten"));

        AVPacket pkt = keyframe(SPS_PPS_ANNEXB);
        invokeWriteVideoFrame(muxer, pkt, ctx);

        assertTrue("first keyframe must trigger the deferred header", getBoolean(muxer, "headerWritten"));
        assertTrue("SPS/PPS must be lifted off the keyframe onto the output stream",
                out.codecpar().extradata_size() > 0);
        assertEquals("extradata must stay Annex B (leading 0x00) so movenc converts the mdat",
                0, out.codecpar().extradata().get(0));
        verify(muxer).callSuperWriteVideoFrame(eq(pkt), eq(ctx));

        // delay_moov is what makes this safe: the header call itself emits nothing, so the
        // moov still picks up tracks (e.g. a late audio stream) added after the first keyframe.
        ArrayBlockingQueue<byte[]> queue = getField(muxer, "queue");
        assertTrue("delay_moov must hold the init segment back at header time", queue.isEmpty());

        // A second keyframe skips the whole lazy-header block and goes straight to super
        AVPacket second = keyframe(SPS_PPS_ANNEXB);
        invokeWriteVideoFrame(muxer, second, ctx);
        verify(muxer).callSuperWriteVideoFrame(eq(second), eq(ctx));

        pkt.close(); second.close();
    }

    @Test
    public void testWriteVideoFrame_headerFails_setsHeaderFailedAndStopsRetrying() throws Exception {
        MoQMuxer muxer = spy(newMuxer(0));
        doNothing().when(muxer).startMoqCli();
        doNothing().when(muxer).callSuperWriteVideoFrame(any(), any());

        AVFormatContext ctx = muxer.getOutputFormatContext();
        // codec_id NONE: extradata is present so writeFormatHeader is reached, but mp4 has no tag for it
        AVStream out = addH264OutStream(muxer, AV_CODEC_ID_NONE);
        setInt(muxer, "videoOutStreamIdx", out.index());
        assertTrue(muxer.prepareIO());

        AVPacket pkt = keyframe(SPS_PPS_ANNEXB);
        invokeWriteVideoFrame(muxer, pkt, ctx);

        assertFalse(getBoolean(muxer, "headerWritten"));
        assertTrue("a failed header must latch, not be retried on every keyframe",
                getBoolean(muxer, "headerFailed"));
        verify(muxer, never()).callSuperWriteVideoFrame(any(), any());

        // Latched: the next keyframe returns on the headerFailed guard without touching FFmpeg again
        AVPacket second = keyframe(SPS_PPS_ANNEXB);
        invokeWriteVideoFrame(muxer, second, ctx);
        verify(muxer, never()).callSuperWriteVideoFrame(any(), any());

        pkt.close(); second.close();
    }

    @Test
    public void testWriteTrailer_afterOpenIO_freesAvioAndDeregisters() throws Exception {
        MoQMuxer muxer = spy(newMuxer(0));
        doNothing().when(muxer).callSuperWriteTrailer();
        doNothing().when(muxer).callSuperClearResource();

        assertTrue(muxer.openIO());
        BytePointer opaque = getField(muxer, "opaque");

        Field instancesField = MoQMuxer.class.getDeclaredField("instances");
        instancesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<BytePointer, MoQMuxer> instances = (Map<BytePointer, MoQMuxer>) instancesField.get(null);
        assertSame("openIO must register the muxer under its opaque key", muxer, instances.get(opaque));

        muxer.writeTrailer();

        assertNull("avioContext must be freed", getField(muxer, "avioContext"));
        assertNull("opaque must be released", getField(muxer, "opaque"));
        assertFalse("a closed muxer must not leak into the static instances map",
                instances.containsKey(opaque));
    }

    @Test
    public void testDrainLoop_writeFailure_isSwallowedAndThreadExits() throws Exception {
        MoQMuxer muxer = newMuxer(0);
        ArrayBlockingQueue<byte[]> queue = getField(muxer, "queue");
        queue.put(new byte[] { 1, 2, 3 });

        OutputStream broken = new OutputStream() {
            @Override public void write(int b) throws IOException { throw new IOException("pipe closed"); }
            @Override public void write(byte[] b) throws IOException { throw new IOException("pipe closed"); }
        };

        muxer.startDrainThread(broken);

        Thread t = getField(muxer, "drainThread");
        t.join(2000);
        assertFalse("a dead moq stdin must end the drain thread, not spin or escape", t.isAlive());
    }

    @Test
    public void testSpawnMoqCli_execsTheBuiltCommand() throws Exception {
        Path dir = Files.createTempDirectory("moq-spawn");
        Path argv = dir.resolve("argv.txt");
        Path bin = MoqTestBinary.write(dir, argv);

        MoQMuxer muxer = newMuxer(0);
        Process p = MoqTestBinary.withResolvedMoq(bin, muxer::spawnMoqCli);
        try {
            assertTrue("spawnMoqCli must hand back a started process", p.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, p.exitValue());

            // What actually reached exec() has to be what buildMoqCliCommand produced
            List<String> cmd = muxer.buildMoqCliCommand();
            assertEquals(cmd.subList(1, cmd.size()), Files.readAllLines(argv));
        } finally {
            p.destroyForcibly();
        }
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
