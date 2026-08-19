package io.antmedia.muxer;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AC3;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AV1;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H265;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP8;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP9;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.junit.Test;

public class RtspMuxerTest {

	private static final String URL = "rtsp://127.0.0.1:8554/LiveApp/stream1";

	@Test
	public void testMuxerSetup() {
		RtspMuxer muxer = new RtspMuxer(URL, null);

		assertEquals(URL, muxer.getOutputURL());
		assertEquals("rtsp", muxer.format);
		assertEquals("tcp", muxer.options.get("rtsp_transport"));
		assertEquals("1400", muxer.options.get("pkt_size"));

		// the ANNOUNCE target is read off s->url, so the url has to reach the context itself
		AVFormatContext context = muxer.getOutputFormatContext();
		assertNotNull(context);
		assertEquals(URL, context.url().getString());

		assertTrue(muxer.isWritable());
		muxer.writeTrailer();
		assertFalse(muxer.isWritable());
	}

	@Test
	public void testSupportedCodecs() {
		RtspMuxer muxer = new RtspMuxer(URL, null);

		assertTrue(muxer.isCodecSupported(AV_CODEC_ID_H264));
		assertTrue(muxer.isCodecSupported(AV_CODEC_ID_H265));
		assertTrue(muxer.isCodecSupported(AV_CODEC_ID_VP8));
		assertTrue(muxer.isCodecSupported(AV_CODEC_ID_AAC));
		assertTrue(muxer.isCodecSupported(AV_CODEC_ID_OPUS));

		// all three were measured as broken, see HANDOFF.md
		assertFalse(muxer.isCodecSupported(AV_CODEC_ID_VP9));
		assertFalse(muxer.isCodecSupported(AV_CODEC_ID_AV1));
		assertFalse(muxer.isCodecSupported(AV_CODEC_ID_AC3));
	}
}
