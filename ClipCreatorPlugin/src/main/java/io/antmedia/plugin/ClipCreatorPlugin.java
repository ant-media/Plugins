package io.antmedia.plugin;

import com.google.gson.Gson;
import io.antmedia.AppSettings;
import io.antmedia.IAppSettingsUpdateListener;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.RecordMuxer;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.rest.model.Result;
import io.lindstrom.m3u8.model.MediaPlaylist;
import io.lindstrom.m3u8.model.MediaSegment;
import io.lindstrom.m3u8.parser.MediaPlaylistParser;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.RandomStringUtils;
import org.checkerframework.checker.units.qual.C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.vertx.core.Vertx;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DateFormat;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component(value = "plugin.clip-creator")
public class ClipCreatorPlugin implements ApplicationContextAware, IStreamListener {

	protected static Logger logger = LoggerFactory.getLogger(ClipCreatorPlugin.class);

	public static final String PLUGIN_KEY = "clip-creator";
	private Gson gson = new Gson();
	private Vertx vertx;
	private ApplicationContext applicationContext;
	private Map<String, Long> lastMp4CreateTimeMSForStream = new ConcurrentHashMap<>();
	private Map<String, Long> streamRecoderTimer = new ConcurrentHashMap<>();

	private AppSettings appSettings;

	private ClipCreatorSettings clipCreatorSettings;
	private DataStore dataStore;

	private String appName;

	private String streamsFolder;

	private MediaPlaylistParser m3u8Parser = new MediaPlaylistParser();

	private int createdMp4Count = 0;
	
	DateFormat dateFormat = DateFormat.getDateTimeInstance();


	public final static class DirectoryFilter implements FilenameFilter {
		/**
		 * Check whether file matches filter rules
		 * 
		 * @param dir
		 *            Directory
		 * @param name
		 *            File name
		 * @return true If file does match filter rules, false otherwise
		 */
		public boolean accept(File dir, String name) {
			File f = new File(dir, name);
			if (logger.isTraceEnabled()) {
				logger.trace("Filtering: {} name: {} dir: {}", dir.getName(), name, f.getAbsolutePath());
			}
			// filter out all non-directories that are hidden and/or not readable
			boolean result = f.isDirectory() && f.canRead() && !f.isHidden();
			// nullify
			f = null;
			return result;
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");

		IAntMediaStreamHandler app = getApplication();

		appSettings = app.getAppSettings();
		dataStore = app.getDataStore();
		appName = app.getScope().getName();
		streamsFolder = IAntMediaStreamHandler.WEBAPPS_PATH + appName + File.separator + "streams";

		app.addStreamListener(this);

		app.addSettingsUpdateListener(new IAppSettingsUpdateListener() {

			@Override
			public boolean settingsUpdated(AppSettings newSettings) 
			{
				ClipCreatorSettings newClipCreatorSettings = getClipCreatorSettings(newSettings);

				if (newClipCreatorSettings.getMp4CreationIntervalSeconds() != clipCreatorSettings
						.getMp4CreationIntervalSeconds()) 
				{
					logger.info("Clip creator settings has changed in {}. The new interval: {}", appName, newClipCreatorSettings.getMp4CreationIntervalSeconds());
					clipCreatorSettings = newClipCreatorSettings;

					return true;
				}

				return false;
			}

			@Override
			public AppSettings getCurrentSettings() {
				return null;
			}
		});

		clipCreatorSettings = getClipCreatorSettings(appSettings);


		logger.info("Clip Creator Plugin is initialized for app: {}", appName);

	}

	public ClipCreatorSettings getClipCreatorSettings(AppSettings appSettingsParameter) {
		Object clipCreatorSettingsString = appSettingsParameter.getCustomSetting("plugin."+ClipCreatorPlugin.PLUGIN_KEY);

		ClipCreatorSettings clipCreatorSettingsLocal = null;
		if (clipCreatorSettingsString == null) 
		{
			logger.error("Using default settings for Clip Creator because no Clip Creator settings in the AppSettings for app:{} ", appName);
			clipCreatorSettingsLocal = new ClipCreatorSettings();
		} 
		else 
		{
			try {
				clipCreatorSettingsLocal = gson.fromJson(clipCreatorSettingsString.toString(), ClipCreatorSettings.class);
				logger.error("Using Clip Creator settings for app:{} ", appName);
			} catch (Exception e) {
				logger.error("Invalid Clip Creator settings, using default settings for app:{}", appName);
				clipCreatorSettingsLocal = new ClipCreatorSettings();
			}
		}

		return clipCreatorSettingsLocal;
	}



	public MediaPlaylist readPlaylist(File m3u8File) {
		try {
			return getM3u8Parser().readPlaylist(m3u8File.toPath());

		} catch (IOException e) {
			logger.error("Could not read playlist file {}", m3u8File.getAbsolutePath());
		}
		return null;
	}

	public Mp4CreationResponse convertHlsToMp4(Broadcast broadcast, boolean upateLastMp4CreateTime) {

		String streamId = broadcast.getStreamId();

		File m3u8File = getM3u8File(streamId);
		if (m3u8File == null) {
			logger.error("No m3u8 file found for stream {}", streamId);
			return null;
		}
		MediaPlaylist playList = readPlaylist(m3u8File);
		
		if (playList == null) {
			logger.error("No HLS playlist found for stream {} from the file:{}", streamId, m3u8File.getAbsolutePath());
			return null;
		}

		Long startTime = getLastMp4CreateTimeForStream().get(streamId);
		long endTime = System.currentTimeMillis();

		if (startTime == null) {
			startTime = 0L;
		}
		
		ArrayList<File> tsFilesToMerge = getSegmentFilesWithinTimeRange(playList, startTime, endTime, m3u8File);

		Mp4CreationResponse response = null;
		
		
		try {
			File tsFileListTextFile = writeTsFilePathsToTxt(m3u8File, tsFilesToMerge);

			String vodId = RandomStringUtils.randomNumeric(24);

			String mp4FilePath = m3u8File.getParentFile().getAbsolutePath() + File.separator + vodId + ".mp4";

			if (ClipCreatorConverter.createMp4(tsFileListTextFile, mp4FilePath)) 
			{
				
				File mp4File = new File(mp4FilePath);

				long durationMs = RecordMuxer.getDurationInMs(mp4File, streamId);
				logger.info("New MP4 created from HLS playlist {} for stream {} for the time between {} and {} and duration:{}ms", mp4FilePath, streamId, dateFormat.format(new Date(startTime)), dateFormat.format(new Date(endTime)), durationMs);
				
				if (upateLastMp4CreateTime) {
					lastMp4CreateTimeMSForStream.put(streamId, endTime);
				}

				getApplication().muxingFinished(
						broadcast,
						streamId,
						mp4File,
						startTime,
						durationMs,
						ClipCreatorConverter.getResolutionHeight(mp4FilePath),
						mp4FilePath,
						vodId
						);
				createdMp4Count++;
				response = new Mp4CreationResponse(mp4File, vodId);
			} else {
				logger.info("Could not create MP4 from HLS for stream {}", streamId);
			}
		} catch (IOException e) {
			logger.error("Error occurred while converting Clip Creator for stream {}: {}", streamId, e.getMessage());
		}

		return response;
	}

	public File writeTsFilePathsToTxt(File m3u8File, ArrayList<File> tsFilesToMerge) throws IOException {
		String txtFilePath = m3u8File.getAbsolutePath().replace(".m3u8", ".txt");
		File txtFile = new File(txtFilePath);

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(txtFile))) {
			for (File tsFile : tsFilesToMerge) {
				writer.write("file '" + tsFile.getAbsolutePath() + "'");
				writer.newLine();
			}
		}

		return txtFile;
	}

	public ArrayList<File> getSegmentFilesWithinTimeRange(MediaPlaylist playList, long startTime, long endTime, File m3u8File) {
		ArrayList<File> segmentFiles = new ArrayList<>();

		Path playlistBasePath = m3u8File.getParentFile().toPath();

		List<MediaSegment> mediaSegments = playList.mediaSegments();

		for (MediaSegment segment : mediaSegments) 
		{
			Optional<OffsetDateTime> segmentDateTimeOpt = segment.programDateTime();

			if (segmentDateTimeOpt.isPresent()) 
			{
				OffsetDateTime segmentDateTime = segmentDateTimeOpt.get();
				long segmentTime = segmentDateTime.toEpochSecond() * 1000;

				if (segmentTime >= startTime && segmentTime <= endTime) 
				{
					Path segmentPath = playlistBasePath.resolve(segment.uri());
					segmentFiles.add(segmentPath.toFile());
				}
			}
		}

		return segmentFiles;
	}

	public File getM3u8File(String streamId) {
		File directory = new File(streamsFolder);
		if (directory.isDirectory()) {
			return searchM3u8File(directory, streamId);
		}
		return null;
	}


	private File searchM3u8File(File directory, String streamId) 
	{
		if (directory == null || !directory.isDirectory()) {
			return null;
		}

		File file = new File(directory, streamId + ".m3u8");
		if (file.exists()) 
		{
			return file;
		}
		else {
			File[] directories = directory.listFiles(new DirectoryFilter());
			for (File dir : directories) {
				File result = searchM3u8File(dir, streamId);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}



	public IAntMediaStreamHandler getApplication() {
		return (IAntMediaStreamHandler) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}

	public Map<String, Long> getLastMp4CreateTimeForStream() {
		return lastMp4CreateTimeMSForStream;
	}

	public ClipCreatorSettings getClipCreatorSettings() {
		return clipCreatorSettings;
	}

	public MediaPlaylistParser getM3u8Parser() {
		return this.m3u8Parser;
	}

	public void setM3u8Parser(MediaPlaylistParser m3u8Parser) {
		this.m3u8Parser = m3u8Parser;
	}

	public String getStreamsFolder() {
		return streamsFolder;
	}

	public void setStreamsFolder(String streamsFolder) {
		this.streamsFolder = streamsFolder;
	}

	public int getCreatedMp4Count() {
		return createdMp4Count;
	}

	public void streamStarted(Broadcast broadcast) {


		String streamId = broadcast.getStreamId();

		long mp4CreationSeconds = clipCreatorSettings.getMp4CreationIntervalSeconds();

		long timerId = vertx.setPeriodic(mp4CreationSeconds * 1000, (h) -> {

			vertx.executeBlocking(() -> 
			{
				//get up to date broadcast	
				Broadcast broadcastLocal = getDataStore().get(streamId);
				if (broadcastLocal != null) {
					convertHlsToMp4(broadcastLocal, true);
				}
				else {
					logger.error("No broadcast found for stream id: {} to record", streamId);
				}
				return null;
				
			}, false);
		});

		streamRecoderTimer.put(broadcast.getStreamId(), timerId);


	}

	@Override
	public void streamStarted(String streamId) {

	}

	@Override
	public void streamFinished(String streamId) {

	}

	@Override
	public void streamFinished(Broadcast broadcast) {

		String streamId = broadcast.getStreamId();

		Long timerId = streamRecoderTimer.remove(streamId);
		if (timerId != null) {
			//timerId can be null if stream is stopped before it's started
			vertx.cancelTimer(timerId);
		}
		
		vertx.executeBlocking(() -> {
			convertHlsToMp4(broadcast, true);
			return null;
		}, false);
		

	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
		//no need to implement
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
		// no need to implement
	}

	public DataStore getDataStore() {
		return dataStore;
	}

	public void setDataStore(DataStore dataStore) {
		this.dataStore = dataStore;
	}
	
	public Map<String, Long> getStreamRecoderTimer() {
		return streamRecoderTimer;
	}
}
