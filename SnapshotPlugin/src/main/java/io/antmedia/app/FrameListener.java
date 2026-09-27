package io.antmedia.app;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGB24;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUVJ420P;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_clone;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_copy_props;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_image_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_make_q;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BICUBIC;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.PointerPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.StreamParametersInfo;
import io.vertx.core.Vertx;

public class FrameListener implements IFrameListener {

	protected static Logger logger = LoggerFactory.getLogger(FrameListener.class);
	
	private AtomicBoolean snapshotRequest = new AtomicBoolean(false);
	private String snapshotDirectory;
	private Vertx vertx;
	private SnapshotSettings settings = new SnapshotSettings();
	private long lastSnapshotTime = 0;

	public FrameListener(String snapshotDirectory, Vertx vertx) {
		this.snapshotDirectory = snapshotDirectory;
		this.vertx = vertx;
	}

	public void scheduleSnapshot() {
		this.snapshotRequest.set(true);
	}
	
	public void updateSettings(SnapshotSettings settings) {
		this.settings = settings;
	}
	
	public SnapshotSettings getSettings() {
		return this.settings;
	}

	@Override
	public AVFrame onAudioFrame(String streamId, AVFrame audioFrame) {
		return audioFrame;
	}

	@Override
	public AVFrame onVideoFrame(String streamId, AVFrame videoFrame) {
		long now = System.currentTimeMillis();
		boolean manual = snapshotRequest.compareAndSet(true, false);
		boolean auto = settings.isAutoSnapshotEnabled() && (now - lastSnapshotTime) >= (settings.getIntervalSeconds() * 1000L);

		if (manual || auto) {
			// Clone frame for async processing because the original frame will be released/reused by the server
			AVFrame frameCopy = av_frame_clone(videoFrame);
			if (frameCopy != null) {
				vertx.executeBlocking(() -> {
					processSnapshot(streamId, frameCopy, settings, manual);
					return null;
				}, false);
				
				if (auto) {
					lastSnapshotTime = now;
				}
			} else {
				logger.error("Failed to clone frame for snapshot");
			}
		}
		return videoFrame;
	}

	private void processSnapshot(String streamId, AVFrame videoFrame, SnapshotSettings config, boolean isManual) {
		try {
			int sourceWidth = videoFrame.width();
			int sourceHeight = videoFrame.height();
			int targetWidth = config.getResolutionWidth();
			int targetHeight = config.getResolutionHeight();
			
			// Resolution logic: 
			// 1. If target is 0, use source.
			// 2. If target > source, do NOT upscale (use source).
			// 3. If target < source, downscale.
			
			int finalWidth = sourceWidth;
			int finalHeight = sourceHeight;
			
			if (targetWidth > 0 && targetHeight > 0) {
				if (targetWidth < sourceWidth || targetHeight < sourceHeight) {
					// Downscale requested
					finalWidth = targetWidth;
					finalHeight = targetHeight;
				}
				// If target >= source, we stay with source (no upscaling)
			}
			
			if (finalWidth != sourceWidth || finalHeight != sourceHeight) {
				AVFrame scaledFrame = scaleFrame(videoFrame, finalWidth, finalHeight);
				if (scaledFrame != null) {
					saveSnapshot(streamId, scaledFrame, config, isManual);
					av_frame_free(scaledFrame);
				}
			} else {
				saveSnapshot(streamId, videoFrame, config, isManual);
			}
		} catch (Exception e) {
			logger.error("Error processing snapshot for stream: " + streamId, e);
		} finally {
			// Always free the cloned frame
			av_frame_free(videoFrame);
		}
	}
	
	private AVFrame scaleFrame(AVFrame src, int width, int height) {
		SwsContext sws_ctx = null;
		AVFrame dst = av_frame_alloc();
		
		try {
			dst.format(src.format());
			dst.width(width);
			dst.height(height);
			
			int ret = av_image_alloc(dst.data(), dst.linesize(), width, height, src.format(), 1);
			if (ret < 0) {
				logger.error("Could not allocate destination image");
				av_frame_free(dst);
				return null;
			}
			
			sws_ctx = sws_getContext(src.width(), src.height(), src.format(),
					width, height, src.format(),
					SWS_BICUBIC, null, null, (DoublePointer)null);
			
			if (sws_ctx == null) {
				logger.error("Could not initialize sws context");
				av_frame_free(dst);
				return null;
			}
			
			sws_scale(sws_ctx, new PointerPointer(src.data()), src.linesize(),
					0, src.height(), new PointerPointer(dst.data()), dst.linesize());
			
			av_frame_copy_props(dst, src);
			
			return dst;
		} catch (Exception e) {
			logger.error("Error scaling frame", e);
			if (dst != null) av_frame_free(dst);
			return null;
		} finally {
			if (sws_ctx != null) {
				sws_freeContext(sws_ctx);
			}
		}
	}

	private void saveSnapshot(String streamId, AVFrame videoFrame, SnapshotSettings config, boolean isManual) {
		String dirPath = snapshotDirectory + File.separator + streamId + File.separator;
		File dir = new File(dirPath);
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				logger.error("Failed to create snapshot directory: " + dirPath);
				return;
			}
		}

		String format = "jpg"; // default
		
		String prefix = isManual ? "manual" : "auto";
		String fileName = String.format("%s_%d_%d.%s", prefix, videoFrame.height(), System.currentTimeMillis(), format);
		
		File file = new File(dir, fileName);
		File tmpFile = new File(dir, fileName + ".tmp");

		logger.info("Taking snapshot ({}) for stream: {} to file: {}", prefix, streamId, file.getAbsolutePath());

		AVCodecContext c = null;
		AVPacket pkt = null;

		try {
			// Map format to Codec ID
			int codecId = avcodec.AV_CODEC_ID_MJPEG;

			AVCodec codec = avcodec_find_encoder(codecId);
			if (codec == null) {
				logger.error("Codec not found for format: " + format);
				return;
			}

			c = avcodec_alloc_context3(codec);
			if (c == null) {
				logger.error("Could not allocate video codec context");
				return;
			}

			c.width(videoFrame.width());
			c.height(videoFrame.height());
			c.pix_fmt(AV_PIX_FMT_YUVJ420P); // Default MJPEG

			c.time_base(av_make_q(1, 25)); 

			saveSnapshotInternal(c, codec, videoFrame, tmpFile, file);

		} catch (Exception e) {
			logger.error("Error taking snapshot for stream: {}", streamId, e);
		} finally {
			if (c != null && !c.isNull()) {
				avcodec_free_context(c);
			}
		}
	}

	private void saveSnapshotInternal(AVCodecContext c, AVCodec codec, AVFrame frame, File tmpFile, File file) {
		AVPacket pkt = null;
		try {
			if (avcodec_open2(c, codec, (AVDictionary) null) < 0) {
				logger.error("Could not open codec");
				return;
			}
			
			frame.pict_type(0); 
			if (avcodec_send_frame(c, frame) < 0) {
				logger.error("Error sending frame to encoder");
				return;
			}

			pkt = av_packet_alloc();
			if (avcodec_receive_packet(c, pkt) >= 0) {
				try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
					byte[] data = new byte[pkt.size()];
					pkt.data().get(data);
					fos.write(data);
				}
				av_packet_unref(pkt);
				
				try {
					Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException e) {
					logger.error("Failed to rename temp file to final destination", e);
				}
			}
		} catch (Exception e) {
			logger.error("Internal save error", e);
		} finally {
			if (pkt != null) av_packet_free(pkt);
			if (tmpFile.exists()) tmpFile.delete();
		}
	}

	@Override
	public void writeTrailer(String streamId) {
	}

	@Override
	public void setVideoStreamInfo(String streamId, StreamParametersInfo videoStreamInfo) {
	}

	@Override
	public void setAudioStreamInfo(String streamId, StreamParametersInfo audioStreamInfo) {
	}

	@Override
	public void start() {
		logger.info("FrameListener.start()");
	}

}
