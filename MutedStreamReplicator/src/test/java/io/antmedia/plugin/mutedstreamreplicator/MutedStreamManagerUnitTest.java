package io.antmedia.plugin.mutedstreamreplicator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

import org.junit.Test;

import io.antmedia.enterprise.adaptive.EncoderAdaptor;
import io.antmedia.enterprise.adaptive.StreamAdaptor;
import io.antmedia.enterprise.adaptive.base.VideoEncoder;
import io.antmedia.muxer.Muxer;

public class MutedStreamManagerUnitTest {

	@Test
	public void testStartReturnsFalseWhenAdaptorsAreNull() {
		assertFalse(new MutedStreamManager(null, null).start());
	}

	@Test
	public void testStartAttachesDirectReceiverAndStopDetachesIt() {
		EncoderAdaptor sourceAdaptor = mock(EncoderAdaptor.class);
		EncoderAdaptor targetAdaptor = mock(EncoderAdaptor.class);
		Muxer targetMuxer = mock(Muxer.class);
		Queue<StreamAdaptor> emptyAdaptors = new LinkedList<>();

		when(sourceAdaptor.addMuxer(any(MutedPacketReceiver.class))).thenReturn(true);
		when(targetAdaptor.getMuxerList()).thenReturn(Arrays.asList(targetMuxer));
		when(sourceAdaptor.getStreamAdaptorList()).thenReturn(emptyAdaptors);
		when(targetAdaptor.getStreamAdaptorList()).thenReturn(emptyAdaptors);

		MutedStreamManager manager = new MutedStreamManager(sourceAdaptor, targetAdaptor);

		assertTrue(manager.start());

		verify(sourceAdaptor).addMuxer(any(MutedPacketReceiver.class));

		manager.stop();

		verify(sourceAdaptor).removeMuxer(any(MutedPacketReceiver.class));
	}

	@Test
	public void testStartAttachesMatchingRenditionReceiverAndStopDetachesIt() {
		EncoderAdaptor sourceAdaptor = mock(EncoderAdaptor.class);
		EncoderAdaptor targetAdaptor = mock(EncoderAdaptor.class);
		StreamAdaptor sourceStreamAdaptor = mock(StreamAdaptor.class);
		StreamAdaptor targetStreamAdaptor = mock(StreamAdaptor.class);
		VideoEncoder sourceVideoEncoder = mock(VideoEncoder.class);
		VideoEncoder targetVideoEncoder = mock(VideoEncoder.class);
		Muxer targetMuxer = mock(Muxer.class);
		Queue<StreamAdaptor> sourceAdaptors = new LinkedList<>();
		Queue<StreamAdaptor> targetAdaptors = new LinkedList<>();

		sourceAdaptors.add(sourceStreamAdaptor);
		targetAdaptors.add(targetStreamAdaptor);

		when(targetAdaptor.getMuxerList()).thenReturn(Collections.emptyList());
		when(sourceAdaptor.getStreamAdaptorList()).thenReturn(sourceAdaptors);
		when(targetAdaptor.getStreamAdaptorList()).thenReturn(targetAdaptors);
		when(sourceStreamAdaptor.getVideoEncoderList()).thenReturn(Arrays.asList(sourceVideoEncoder));
		when(targetStreamAdaptor.getVideoEncoderList()).thenReturn(Arrays.asList(targetVideoEncoder));
		when(sourceVideoEncoder.getResolutionHeight()).thenReturn(360);
		when(targetVideoEncoder.getResolutionHeight()).thenReturn(360);
		when(targetVideoEncoder.getMuxerList()).thenReturn(Arrays.asList(targetMuxer));

		MutedStreamManager manager = new MutedStreamManager(sourceAdaptor, targetAdaptor);

		assertTrue(manager.start());

		verify(sourceVideoEncoder).addMuxer(any(MutedPacketReceiver.class));

		manager.stop();

		verify(sourceVideoEncoder).removeMuxer(any(MutedPacketReceiver.class));
		verify(sourceAdaptor, never()).removeMuxer(any(MutedPacketReceiver.class));
	}

	@Test
	public void testStartReturnsFalseWhenNoReceiversCanBeAttached() {
		EncoderAdaptor sourceAdaptor = mock(EncoderAdaptor.class);
		EncoderAdaptor targetAdaptor = mock(EncoderAdaptor.class);

		when(targetAdaptor.getMuxerList()).thenReturn(Collections.emptyList());
		when(sourceAdaptor.getStreamAdaptorList()).thenReturn(new LinkedList<>());
		when(targetAdaptor.getStreamAdaptorList()).thenReturn(new LinkedList<>());

		assertFalse(new MutedStreamManager(sourceAdaptor, targetAdaptor).start());
		verify(sourceAdaptor, never()).addMuxer(any(MutedPacketReceiver.class));
	}
}
