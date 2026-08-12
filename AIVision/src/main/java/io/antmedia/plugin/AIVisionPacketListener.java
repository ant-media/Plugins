package io.antmedia.plugin;

import static org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_ref;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24;
import static org.bytedeco.ffmpeg.global.avutil.av_free;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_image_fill_arrays;
import static org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size;
import static org.bytedeco.ffmpeg.global.avutil.av_malloc;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.plugin.api.IPacketListener;
import io.antmedia.plugin.api.StreamParametersInfo;
import io.vertx.core.Vertx;

public class AIVisionPacketListener implements IPacketListener {

	private static final Logger logger = LoggerFactory.getLogger(AIVisionPacketListener.class);

	private final String streamId;
	private final File imageDirectory;
	private final String imageUrlPrefix;
	private final Vertx vertx;
	private final AtomicReference<AIVisionImage> latestImage = new AtomicReference<>();
	private final AtomicBoolean decodeInProgress = new AtomicBoolean(false);
	private volatile AIVisionVideoDecoder videoDecoder;
	private volatile StreamParametersInfo videoStreamInfo;

	public AIVisionPacketListener(String streamId, File imageDirectory, String imageUrlPrefix, Vertx vertx) {
		this.streamId = streamId;
		this.imageDirectory = imageDirectory;
		this.imageUrlPrefix = imageUrlPrefix;
		this.vertx = vertx;
	}

	@Override
	public AVPacket onVideoPacket(String incomingStreamId, AVPacket packet) {
		if (!isKeyFrame(packet) || videoDecoder == null || !decodeInProgress.compareAndSet(false, true)) {
			return packet;
		}

		AVPacket packetCopy = new AVPacket();
		av_packet_ref(packetCopy, packet);
		vertx.executeBlocking(() -> {
			try {
				decodeAndStore(packetCopy);
			}
			catch (Exception e) {
				logger.warn("Could not decode latest keyframe for stream {}", streamId, e);
			}
			finally {
				av_packet_unref(packetCopy);
				packetCopy.close();
				decodeInProgress.set(false);
			}
			return null;
		}, false);

		return packet;
	}

	@Override
	public AVPacket onAudioPacket(String incomingStreamId, AVPacket packet) {
		return packet;
	}

	@Override
	public AVPacket onDataPacket(String incomingStreamId, AVPacket packet) {
		return packet;
	}

	@Override
	public void setVideoStreamInfo(String incomingStreamId, StreamParametersInfo videoStreamInfo) {
		this.videoStreamInfo = videoStreamInfo;
		try {
			videoDecoder = new AIVisionVideoDecoder(streamId, videoStreamInfo);
			AVRational timeBase = videoStreamInfo.getTimeBase();
			if (timeBase != null) {
				videoDecoder.setDecoderTimeBase(timeBase);
			}
			logger.info("AI Vision decoder initialized for stream {}", streamId);
		}
		catch (Exception e) {
			logger.warn("Could not initialize AI Vision decoder for stream {}", streamId, e);
			videoDecoder = null;
		}
	}

	@Override
	public void setAudioStreamInfo(String incomingStreamId, StreamParametersInfo audioStreamInfo) {
		// Audio is not used by this plugin.
	}

	@Override
	public void writeTrailer(String incomingStreamId) {
		stop();
	}

	public AIVisionImage getLatestImage() {
		return latestImage.get();
	}

	public void stop() {
		AIVisionVideoDecoder decoder = videoDecoder;
		videoDecoder = null;
		if (decoder != null) {
			decoder.stop();
		}
	}

	private boolean isKeyFrame(AVPacket packet) {
		return packet != null && (packet.flags() & AV_PKT_FLAG_KEY) == AV_PKT_FLAG_KEY;
	}

	private void decodeAndStore(AVPacket packet) throws IOException {
		AIVisionVideoDecoder decoder = videoDecoder;
		StreamParametersInfo streamInfo = videoStreamInfo;
		if (decoder == null || streamInfo == null) {
			return;
		}

		AVFrame frame = decoder.decodeVideoPacket(packet);
		if (frame == null) {
			return;
		}

		BufferedImage image = convertToBufferedImage(frame);
		imageDirectory.mkdirs();
		long timestamp = System.currentTimeMillis();
		String fileName = streamId + "-" + timestamp + ".png";
		File imageFile = new File(imageDirectory, fileName);
		ImageIO.write(image, "png", imageFile);
		latestImage.set(new AIVisionImage(imageFile, imageUrlPrefix + "/" + fileName));
		logger.debug("Stored latest AI Vision keyframe for stream {} at {}", streamId, imageFile.getAbsolutePath());
	}

	private BufferedImage convertToBufferedImage(AVFrame sourceFrame) {
		int width = sourceFrame.width();
		int height = sourceFrame.height();
		int destinationPixelFormat = AV_PIX_FMT_BGR24;
		int bufferSize = av_image_get_buffer_size(destinationPixelFormat, width, height, 1);

		AVFrame bgrFrame = av_frame_alloc();
		BytePointer buffer = new BytePointer(av_malloc(bufferSize));
		SwsContext scaler = null;
		try {
			av_image_fill_arrays(bgrFrame.data(), bgrFrame.linesize(), buffer, destinationPixelFormat, width, height, 1);
			scaler = sws_getContext(width, height, sourceFrame.format(), width, height, destinationPixelFormat,
					SWS_BILINEAR, null, null, (DoublePointer) null);
			if (scaler == null) {
				throw new IllegalStateException("Could not create FFmpeg scaler context");
			}

			sws_scale(scaler, sourceFrame.data(), sourceFrame.linesize(), 0, height, bgrFrame.data(), bgrFrame.linesize());

			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
			byte[] imageData = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
			int sourceLineSize = bgrFrame.linesize(0);
			int targetLineSize = width * 3;
			BytePointer sourceData = bgrFrame.data(0);
			for (int y = 0; y < height; y++) {
				sourceData.position((long) y * sourceLineSize).get(imageData, y * targetLineSize, targetLineSize);
			}
			return image;
		}
		finally {
			if (scaler != null) {
				sws_freeContext(scaler);
			}
			if (bgrFrame != null) {
				av_frame_free(bgrFrame);
				bgrFrame.close();
			}
			if (buffer != null) {
				av_free(buffer);
				buffer.close();
			}
		}
	}
}
