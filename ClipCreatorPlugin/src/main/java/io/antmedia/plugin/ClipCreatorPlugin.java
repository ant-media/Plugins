package io.antmedia.plugin;

import com.google.gson.Gson;
import io.antmedia.AppSettings;
import io.antmedia.IAppSettingsUpdateListener;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.RecordMuxer;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.rest.model.Result;
import io.antmedia.settings.ServerSettings;
import io.lindstrom.m3u8.model.MediaPlaylist;
import io.lindstrom.m3u8.model.MediaSegment;
import io.lindstrom.m3u8.parser.MediaPlaylistParser;
import jakarta.ws.rs.core.Response;

import org.apache.commons.collections.functors.NullIsExceptionPredicate;
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

	private AppSettings appSettings;

	private ClipCreatorSettings clipCreatorSettings;
	private DataStore dataStore;

	private String appName;

	private String streamsFolder;

	private MediaPlaylistParser m3u8Parser = new MediaPlaylistParser();

	private int createdMp4Count = 0;

	DateFormat dateFormat = DateFormat.getDateTimeInstance();

	private long timerId = -1;

	private ServerSettings serverSettings;


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

		serverSettings = (ServerSettings) applicationContext.getBean(ServerSettings.BEAN_NAME);

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
						.getMp4CreationIntervalSeconds() || newClipCreatorSettings.isEnabled() != clipCreatorSettings.isEnabled()) 
				{
					logger.info("Clip creator settings has changed in {}. The new interval: {} and is enabled:{} ", appName, newClipCreatorSettings.getMp4CreationIntervalSeconds(), newClipCreatorSettings.isEnabled());
					
					if (newClipCreatorSettings.isEnabled()) {
						startPeriodicRecording(newClipCreatorSettings.getMp4CreationIntervalSeconds());
					} else {
						stopPeriodicRecording();
					}
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

		if (clipCreatorSettings.isEnabled()) {
			logger.info("Clip Creator Plugin is enabled for app: {}", appName);
			startPeriodicRecording(clipCreatorSettings.getMp4CreationIntervalSeconds());
		} 
		else {
			logger.info("Clip Creator Plugin is not active for app: {}", appName);
		}


		

	}

	public Result startPeriodicRecording(int periodSeconds) 
	{

		Result result = new Result(false);

		if (timerId != -1) {
			stopPeriodicRecording();
			result.setMessage("Old clip-creator timer is cancelled for app: " + appName + " and new one will be created");
		}

		//set the new interval for recording
		clipCreatorSettings.setMp4CreationIntervalSeconds(periodSeconds);
		clipCreatorSettings.setEnabled(true);

		timerId = vertx.setPeriodic(periodSeconds * 1000, (l) -> {

			
			vertx.executeBlocking(() -> 
			{
				createRecordings();
				return null;
			}, false);
			
		});

		result.setSuccess(true);

		return result;
	}
	
	public void createRecordings() 
	{
		
		List<Broadcast> broadcasts = dataStore.getLocalLiveBroadcasts(serverSettings.getHostAddress());
		logger.info("createRecordings for active broadcasts size:{}", broadcasts.size());
		for (Broadcast broadcast : broadcasts) {
			convertHlsToMp4(broadcast, true);
		}
	}

	public Result stopPeriodicRecording() 
	{
		Result result = new Result(false);

		if (timerId != -1) 
		{
			vertx.cancelTimer(timerId);
			//finish all recordings

			logger.info("Clip Creator timer is stopped for app: {} ", appName);
			vertx.executeBlocking(() -> {
				
				try {
				
					//create recordings for the last time
					createRecordings();
				} catch (Exception e) {
					logger.error("Error occured in createRecordings", e);
				}
				
				//clear lastMp4CreateTimeMSForStream
				lastMp4CreateTimeMSForStream.clear();
				return null;
			}, false);

			result.setSuccess(true);

		}
		else {
			result.setMessage("There is no active timer for Clip Creator Plugin in app: " + appName);
		}
		
		logger.info("Clip creator periodic recording is stopped for app: {}", appName);

		clipCreatorSettings.setEnabled(false);
		
		//clear timer
		timerId = -1;
		return result;

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

	public synchronized Mp4CreationResponse convertHlsToMp4(Broadcast broadcast, boolean updateLastMp4CreateTime) {

		Mp4CreationResponse response = new Mp4CreationResponse();
		String streamId = broadcast.getStreamId();

		File m3u8File = getM3u8File(streamId);
		if (m3u8File == null) {
			logger.error("No m3u8 file found for stream {}", streamId);
			response.setMessage("No m3u8 file found for stream " + streamId);
			return response;
		}
		MediaPlaylist playList = readPlaylist(m3u8File);

		if (playList == null) {
			logger.error("No HLS playlist found for stream {} from the file:{}", streamId, m3u8File.getAbsolutePath());

			response.setMessage("No HLS playlist found for stream " + streamId);
			return response;
		}

		Long startTime = lastMp4CreateTimeMSForStream.get(streamId);
		
		long endTime = System.currentTimeMillis();

		if (startTime == null) {
			startTime = endTime - (clipCreatorSettings.getMp4CreationIntervalSeconds() * 1000);
		}
		
		ArrayList<File> tsFilesToMerge = getSegmentFilesWithinTimeRange(playList, startTime, endTime, m3u8File);

		if (tsFilesToMerge.size() == 0) {
			logger.info("No segment file found for stream {} between {} and {}", streamId,
					dateFormat.format(new Date(startTime)), dateFormat.format(new Date(endTime)));
			response.setMessage("No segment file found for stream " + streamId + " between "
					+ dateFormat.format(new Date(startTime)) + " and " + dateFormat.format(new Date(endTime)));
			return response;
		}

		try {
			logger.info("number of ts files: {} for streamId:{}", tsFilesToMerge.size(), streamId);
			File tsFileListTextFile = writeTsFilePathsToTxt(m3u8File, tsFilesToMerge);

			String vodId = RandomStringUtils.randomNumeric(24);

			String mp4FilePath = m3u8File.getParentFile().getAbsolutePath() + File.separator + vodId + ".mp4";

			if (ClipCreatorConverter.createMp4(tsFileListTextFile, mp4FilePath)) 
			{

				File mp4File = new File(mp4FilePath);

				long durationMs = RecordMuxer.getDurationInMs(mp4File, streamId);
				logger.info("New MP4 created from HLS playlist {} for stream {} for the time between {} and {} and duration:{}ms", mp4FilePath, streamId, dateFormat.format(new Date(startTime)), dateFormat.format(new Date(endTime)), durationMs);

				if (updateLastMp4CreateTime) {
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
				response.setSuccess(true);
			} else {
				logger.info("Could not create MP4 from HLS for stream {}", streamId);
				response.setMessage("Could not create MP4 from HLS for stream " + streamId);

			}
		} catch (IOException e) {
			logger.error("Error occurred while converting Clip Creator for stream {}: {}", streamId, e.getMessage());
			response.setMessage("Error occurred while converting Clip Creator for stream " + streamId);
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

	public ArrayList<File> getSegmentFilesWithinTimeRange(MediaPlaylist playList, long startTimeMs, long endTimeMs, File m3u8File) {
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

				if (segmentTime >= startTimeMs && segmentTime <= endTimeMs) 
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
	
	public void setClipCreatorSettings(ClipCreatorSettings clipCreatorSettings) {
		this.clipCreatorSettings = clipCreatorSettings;
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

	}

	@Override
	public void streamStarted(String streamId) {

	}

	@Override
	public void streamFinished(String streamId) {

	}

	@Override
	public void streamFinished(Broadcast broadcast) {


		if (clipCreatorSettings.isEnabled()) 
		{

			vertx.executeBlocking(() -> {
				convertHlsToMp4(broadcast, true);
				lastMp4CreateTimeMSForStream.remove(broadcast.getStreamId());
				return null;
			}, false);
		}


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
	
	public long getTimerId() {
		return timerId;
	}
}
