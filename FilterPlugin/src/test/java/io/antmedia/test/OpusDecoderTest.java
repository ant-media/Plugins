package io.antmedia.test;

import static org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.javacpp.BytePointer;
import org.junit.Test;

import io.antmedia.filter.OpusDecoder;

public class OpusDecoderTest {
	
	
	@Test
	public void testPrepare() {
		
		OpusDecoder opusDecoder = new OpusDecoder();
		
		opusDecoder.prepare("streamId");
		
		assertTrue(opusDecoder.isInitialized());
		
		AVPacket packet = new AVPacket();
				
		packet.stream_index(0);
		packet.pts(1);
		packet.dts(1);
		packet.flags(AV_PKT_FLAG_KEY);
		
		byte[] data = new byte[]{10, 20, 30};
		
		packet.data(new BytePointer(ByteBuffer.allocate(100)));
		packet.size(100);
		packet.position(0);
		
		assertNotNull(opusDecoder.decode(packet));
		
		data = new byte[]{10, 20, 30};
		packet.data(new BytePointer(data));
		packet.size(data.length);
		assertNull(opusDecoder.decode(packet));
		
		
		
	}

}
