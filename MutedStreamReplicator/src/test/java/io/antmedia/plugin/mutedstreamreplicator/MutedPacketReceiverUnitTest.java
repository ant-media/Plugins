package io.antmedia.plugin.mutedstreamreplicator;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.junit.Test;

import io.antmedia.muxer.Muxer;

public class MutedPacketReceiverUnitTest {

	@Test
	public void testPrepareIOAndWriteTrailerToggleRunningState() {
		MutedPacketReceiver receiver = new MutedPacketReceiver(Collections.emptyList());

		assertTrue(receiver.prepareIO());
		assertTrue(receiver.getIsRunning().get());

		receiver.writeTrailer();

		assertFalse(receiver.getIsRunning().get());
	}

	@Test
	public void testWritePacketAndVideoBufferFanOutToAllTargetMuxers() {
		Muxer muxer1 = mock(Muxer.class);
		Muxer muxer2 = mock(Muxer.class);
		MutedPacketReceiver receiver = new MutedPacketReceiver(Arrays.asList(muxer1, muxer2));
		AVPacket packet = new AVPacket();
		AVStream stream = mock(AVStream.class);
		AVCodecContext codecContext = mock(AVCodecContext.class);
		ByteBuffer videoBuffer = ByteBuffer.allocate(8);

		receiver.writePacket(packet, stream);
		receiver.writePacket(packet, codecContext);
		receiver.writeVideoBuffer(videoBuffer, 10L, 0, 1, true, 20L, 30L);

		verify(muxer1).writePacket(packet, stream);
		verify(muxer2).writePacket(packet, stream);
		verify(muxer1).writePacket(packet, codecContext);
		verify(muxer2).writePacket(packet, codecContext);
		verify(muxer1).writeVideoBuffer(videoBuffer, 10L, 0, 1, true, 20L, 30L);
		verify(muxer2).writeVideoBuffer(videoBuffer, 10L, 0, 1, true, 20L, 30L);
	}

	@Test
	public void testNullOrEmptyTargetsAreHandledGracefully() {
		MutedPacketReceiver receiver = new MutedPacketReceiver(null);
		AVPacket packet = new AVPacket();
		AVStream stream = mock(AVStream.class);
		AVCodecContext codecContext = mock(AVCodecContext.class);

		receiver.writePacket(packet, stream);
		receiver.writePacket(packet, codecContext);
		receiver.writeVideoBuffer(ByteBuffer.allocate(4), 1L, 0, 0, false, 1L, 1L);
		receiver.writeAudioBuffer(ByteBuffer.allocate(4), 0, 1L);

		assertTrue(receiver.isCodecSupported(42));
		assertNull(receiver.getOutputFormatContext());
		assertTrue(receiver.addStream(mock(AVCodecParameters.class), new AVRational(), 0));
		assertTrue(receiver.addStream((org.bytedeco.ffmpeg.avcodec.AVCodec) null, codecContext, 0));
	}

	@Test
	public void testSnapshotPreventsUnexpectedWritesWhenTargetListIsEmpty() {
		Muxer muxer = mock(Muxer.class);
		MutedPacketReceiver receiver = new MutedPacketReceiver(Collections.emptyList());

		receiver.writePacket(new AVPacket(), mock(AVStream.class));

		verify(muxer, never()).writePacket(org.mockito.ArgumentMatchers.any(AVPacket.class), org.mockito.ArgumentMatchers.any(AVStream.class));
	}
}
