package io.antmedia.muxer;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AC3;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AV1;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H265;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP8;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP9;
import static org.bytedeco.ffmpeg.global.avcodec.AV_PROFILE_AAC_HE;
import static org.bytedeco.ffmpeg.global.avcodec.AV_PROFILE_AAC_LOW;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVRational;
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

	/** Shutdown hooks have no ordering, so AMS can still be pumping packets at a dead MediaMTX. */
	@Test
	public void testNothingIsWrittenAfterTheTrailer() {
		RtspMuxer muxer = new RtspMuxer(URL, null);

		muxer.writeTrailer();
		muxer.writeTrailer();

		// a null packet would be a native crash if either of these reached av_write_frame
		muxer.writeVideoFrame(null, null);
		muxer.writeAudioFrame(null, null, null, null, 0);

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

		// all three were measured as broken, see the codec table in README.md
		assertFalse(muxer.isCodecSupported(AV_CODEC_ID_VP9));
		assertFalse(muxer.isCodecSupported(AV_CODEC_ID_AV1));
		assertFalse(muxer.isCodecSupported(AV_CODEC_ID_AC3));
	}

	/** A failed header frees the AVPackets. AMS retries prepare on the same instance, and a muxer that came
	 * back to life would segfault on the first packet. */
	@Test
	public void testTheMuxerStaysDeadOnceItIsReleased() {
		RtspMuxer muxer = new RtspMuxer(URL, null);
		assertNotNull(muxer.getOutputFormatContext());

		muxer.clearResource();

		assertFalse(muxer.isWritable());
		assertNull(muxer.getOutputFormatContext());
		assertFalse(muxer.prepareIO());
	}

	/** MPEG-TS ingest hands over AAC without extradata and the SDP cannot be built without it. */
	@Test
	public void testAacWithoutExtradataGetsAConfig() {
		// 44100 stereo AAC-LC, byte for byte what aac_adtstoasc produces
		assertArrayEquals(new byte[] { 0x12, 0x10 }, addAacStream(AV_PROFILE_AAC_LOW, 44100, 2));

		// ADTS only carries object types 1 to 4, HE-AAC frames are plain LC in the header
		assertArrayEquals(new byte[] { 0x12, 0x10 }, addAacStream(AV_PROFILE_AAC_HE, 44100, 2));

		assertArrayEquals(new byte[] { 0x11, (byte) 0xb0 }, addAacStream(AV_PROFILE_AAC_LOW, 48000, 6));

		// channel config 7 is 8 channels, there is none for 7
		assertArrayEquals(new byte[] { 0x12, 0x38 }, addAacStream(AV_PROFILE_AAC_LOW, 44100, 8));
	}

	@Test
	public void testParametersWithNoConfigAreLeftAlone() {
		assertEquals(0, addAacStream(AV_PROFILE_AAC_LOW, 12345, 2).length);
		assertEquals(0, addAacStream(AV_PROFILE_AAC_LOW, 44100, 7).length);
		assertEquals(0, addAacStream(AV_PROFILE_AAC_LOW, 44100, 0).length);
	}

	/** The extradata the muxer ends up announcing for one AAC stream. */
	private static byte[] addAacStream(int profile, int sampleRate, int channels) {
		RtspMuxer muxer = new RtspMuxer(URL, null);

		AVCodecParameters parameters = new AVCodecParameters();
		parameters.codec_type(AVMEDIA_TYPE_AUDIO);
		parameters.codec_id(AV_CODEC_ID_AAC);
		parameters.profile(profile);
		parameters.sample_rate(sampleRate);
		parameters.ch_layout().nb_channels(channels);

		assertTrue(muxer.addStream(parameters, new AVRational().num(1).den(90000), 0));

		AVCodecParameters added = muxer.getOutputFormatContext().streams(0).codecpar();
		byte[] config = new byte[added.extradata_size()];
		for (int i = 0; i < config.length; i++) {
			config[i] = added.extradata().get(i);
		}
		return config;
	}
}
