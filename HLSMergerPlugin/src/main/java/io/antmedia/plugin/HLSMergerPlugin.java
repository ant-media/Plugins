package io.antmedia.plugin;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.muxer.HLSMuxer;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.muxer.Muxer;
import io.vertx.core.Vertx;
import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H265;

@Component(value="plugin.hls-merger")
public class HLSMergerPlugin implements ApplicationContextAware{

	protected static Logger logger = LoggerFactory.getLogger(HLSMergerPlugin.class);
	
	private Vertx vertx;
	private ApplicationContext applicationContext;
	private final ConcurrentHashMap<String, Long> mergeTimers = new ConcurrentHashMap<>();

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");
	}
		
	public MuxAdaptor getMuxAdaptor(String streamId) 
	{
		IAntMediaStreamHandler application = getApplication();
		MuxAdaptor selectedMuxAdaptor = null;

		if(application != null)
		{
			selectedMuxAdaptor = application.getMuxAdaptor(streamId);
		}

		return selectedMuxAdaptor;
	}
	
	public IAntMediaStreamHandler getApplication() {
		return (IAntMediaStreamHandler) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}
	
	public boolean mergeStreams(String fileName, String[] streamIds) {
		long hlsMergedFileUpdate = vertx.setPeriodic(5000,h -> mergeStreamsInternal(fileName, streamIds));
		mergeTimers.put(fileName, hlsMergedFileUpdate);
		return true;
	}

	public void mergeStreamsInternal(String fileName, String[] streamIds) {
		StringBuilder streamIdBuilder = new StringBuilder();
		String subfolder = "";
		streamIdBuilder.append("#EXTM3U").append("\n");
		for (String streamId : streamIds) {
			MuxAdaptor muxAdaptor = getMuxAdaptor(streamId);
			long bitrate = 0;
			int programId = 1;
			if (muxAdaptor != null) {
				for (Muxer muxer : muxAdaptor.getMuxerList()) {
					if (muxer instanceof HLSMuxer) {
						HLSMuxer hlsMuxer = (HLSMuxer) muxer;
						bitrate = hlsMuxer.getAverageBitrate();
					}
				}
				int width = muxAdaptor.getWidth();
				int height = muxAdaptor.getHeight();
				int codec = muxAdaptor.getVideoCodecId();
				subfolder = muxAdaptor.getBroadcast().getSubFolder();

				streamIdBuilder.append("#EXT-X-STREAM-INF:PROGRAM-ID=")
						.append(programId)
						.append(",BANDWIDTH=").append(bitrate)
						.append(",RESOLUTION=").append(width).append("x").append(height);
				if (codec == AV_CODEC_ID_H264) {
					streamIdBuilder.append(",CODECS=\"avc1.42e00a,mp4a.40.2\"");
				}
				else if (codec == AV_CODEC_ID_H265) {
					streamIdBuilder.append(",CODECS=\"hvc1.1.4.L60.B01,mp4a.40.2\"");
				}
				streamIdBuilder.append("\n");

				streamIdBuilder.append(streamId + ".m3u8")
						.append("\n");
			}
		}

		writeHLSFile(fileName, streamIdBuilder.toString(), subfolder);
    }

	public boolean stopMerge(String fileName) {
		if (mergeTimers.containsKey(fileName)) {
			long hlsMergedFileUpdate = mergeTimers.get(fileName);
			vertx.cancelTimer(hlsMergedFileUpdate);
			mergeTimers.remove(fileName);
		}
		return true;
	}

	public boolean addMultipleAudioStreams(String fileName, String videoStreamId, String[] audioStreamIds) {
		long hlsMergedFileUpdate = vertx.setPeriodic(5000,h -> addMultipleAudioInternal(fileName, videoStreamId, audioStreamIds));
		mergeTimers.put(fileName, hlsMergedFileUpdate);
		return true;
	}
	
	public void addMultipleAudioInternal(String fileName, String videoStreamId, String[] audioStreamIds) {
		StringBuilder streamBuilder = new StringBuilder();
		String subfolder = "";
		streamBuilder.append("#EXTM3U\n");

		// Add audio streams
		for (String audioStreamId : audioStreamIds) {
			MuxAdaptor muxAdaptor = getMuxAdaptor(audioStreamId);
			if (muxAdaptor != null) {
				subfolder = muxAdaptor.getBroadcast().getSubFolder();
				streamBuilder.append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"")
					.append(audioStreamId)
					.append("\",DEFAULT=NO,AUTOSELECT=YES,URI=\"")
					.append(audioStreamId).append(".m3u8\"\n");
			}
		}

		// Add video stream with reference to audio group
		MuxAdaptor videoMuxAdaptor = getMuxAdaptor(videoStreamId);
		if (videoMuxAdaptor != null) {
			long bitrate = 0;
			for (Muxer muxer : videoMuxAdaptor.getMuxerList()) {
				if (muxer instanceof HLSMuxer) {
					HLSMuxer hlsMuxer = (HLSMuxer) muxer;
					bitrate = hlsMuxer.getAverageBitrate();
				}
			}
			int width = videoMuxAdaptor.getWidth();
			int height = videoMuxAdaptor.getHeight();
			streamBuilder.append("#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=")
				.append(bitrate)
				.append(",RESOLUTION=").append(width).append("x").append(height)
				.append(",CODECS=\"avc1.42e00a,mp4a.40.2\",AUDIO=\"audio\"\n");
			streamBuilder.append(videoStreamId).append(".m3u8\n");
		}

		writeHLSFile(fileName, streamBuilder.toString(), subfolder);
	}

	public void writeHLSFile(String fileName, String content, String subfolder) {
		IScope scope = getApplication().getScope();
		File audioHLSFile = Muxer.getRecordFile(scope, fileName, ".m3u8.tmp", subfolder);

		try (PrintWriter out = new PrintWriter(audioHLSFile)) {
			out.println(content);
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage());
		}

		File originalFile = Muxer.getRecordFile(scope, fileName, ".m3u8", subfolder);
		try {
			Files.move(audioHLSFile.toPath(), originalFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

	public ConcurrentHashMap<String, Long> getMergeTimers() {
		return mergeTimers;
	}
}
