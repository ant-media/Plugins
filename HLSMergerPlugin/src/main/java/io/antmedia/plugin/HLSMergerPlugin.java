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

		IScope scope = getApplication().getScope();
		File mergedHLSFile = Muxer.getRecordFile(scope, fileName, ".m3u8.tmp", subfolder);
		PrintWriter out = null;
		try {
			out = new PrintWriter(mergedHLSFile);
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage());
		}

		out.println(streamIdBuilder.toString());

		out.flush();
		out.close();

		File originalFile = Muxer.getRecordFile(scope, fileName, ".m3u8", subfolder);
        try {
            Files.move(mergedHLSFile.toPath(), originalFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			logger.error(e.getMessage());
        }
    }

	public boolean stopMerge(String fileName) {
		if (mergeTimers.containsKey(fileName)) {
			long hlsMergedFileUpdate = mergeTimers.get(fileName);
			vertx.cancelTimer(hlsMergedFileUpdate);
			mergeTimers.remove(fileName);
		}
		return true;
	}
}
