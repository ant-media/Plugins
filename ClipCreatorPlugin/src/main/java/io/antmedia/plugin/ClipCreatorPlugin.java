package io.antmedia.plugin;

import com.google.gson.Gson;
import io.antmedia.AppSettings;
import io.antmedia.IAppSettingsUpdateListener;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.datastore.db.types.VoD;
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

import javax.annotation.Nonnull;

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
				
				boolean result = false;
				ClipCreatorSettings newClipCreatorSettings = getClipCreatorSettings(newSettings);

				if (newClipCreatorSettings.getMp4CreationIntervalSeconds() != clipCreatorSettings
						.getMp4CreationIntervalSeconds() || newClipCreatorSettings.isEnabled() != clipCreatorSettings.isEnabled()
						|| newClipCreatorSettings.isDeleteHLSFilesAfterCreatedMp4() != clipCreatorSettings.isDeleteHLSFilesAfterCreatedMp4()) 
				{
					logger.info("Clip-creator settings has changed in {}. The interval: {}secs and enabled:{} and deleteHLSFilesOnEnded:{}", appName, newClipCreatorSettings.getMp4CreationIntervalSeconds(),
							newClipCreatorSettings.isEnabled(), newClipCreatorSettings.isDeleteHLSFilesAfterCreatedMp4());

					if (newClipCreatorSettings.isEnabled()) {
						startPeriodicRecording(newClipCreatorSettings.getMp4CreationIntervalSeconds());
					} else {
						stopPeriodicRecording();
					}
					
					result = true;
				}
				
				clipCreatorSettings = newClipCreatorSettings;

				return result;
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
			logger.info("Old clip-creator timer is cancelled for app: {}", appName);
			result.setMessage("Old clip-creator timer is cancelled for app: " + appName + " and new one will be created");
		}

		//set the new interval for recording
		clipCreatorSettings.setMp4CreationIntervalSeconds(periodSeconds);
		clipCreatorSettings.setEnabled(true);

		logger.info("Clip-Creator Plugin is started for app: {} with interval: {} seconds", appName, periodSeconds);
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
		logger.info("createRecordings for active broadcasts size:{} for app:{}", broadcasts.size(), appName);

		for (Broadcast broadcast : broadcasts) 
		{
			vertx.executeBlocking(() -> 
			{
				long endTime = System.currentTimeMillis();
				// Get TS segment duration from appSettings (hlsTime) and add 100ms
				long segmentDurationMs = 2100; // default fallback
				try {
					String hlsTimeStr = appSettings.getHlsTime();
					if (hlsTimeStr != null) {
						logger.info("The HLS segment duration is:{}", hlsTimeStr);
						double hlsTime = Double.parseDouble(hlsTimeStr);
						segmentDurationMs = (long)(hlsTime * 1000) + 200;
					}
				} catch (Exception e) {
					logger.warn("Could not parse hlsTime from appSettings, using default 2100ms", e);
				}
				vertx.setTimer(segmentDurationMs, (l) -> {
					convertHlsToMp4(broadcast, true, endTime);
				});

				return null;
			}, false);

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
		Object clipCreatorSettingsValue = appSettingsParameter.getCustomSetting("plugin."+ClipCreatorPlugin.PLUGIN_KEY);

		ClipCreatorSettings clipCreatorSettingsLocal = null;
		if (clipCreatorSettingsValue == null)
		{
			logger.info("Using default settings for Clip Creator because no Clip Creator settings in the AppSettings for app:{} ", appName);
			clipCreatorSettingsLocal = new ClipCreatorSettings();
		}
		else
		{
			try {
				// Don't go through toString(): the stored value is a structured object whose runtime type
				// depends on the persistence backend. In standalone it's a Map, but in cluster mode it's
				// an org.bson.Document (Morphia/BSON), whose toString() is "Document{{...}}" — not valid JSON.
				// Parsing that threw and we silently fell back to defaults (enabled=true), so the plugin kept
				// creating clips even when disabled. Convert structurally instead so every backend works.
				clipCreatorSettingsLocal = clipCreatorSettingsValue instanceof String
						? gson.fromJson((String) clipCreatorSettingsValue, ClipCreatorSettings.class)
						: gson.fromJson(gson.toJsonTree(clipCreatorSettingsValue), ClipCreatorSettings.class);
				logger.info("Using Clip Creator settings for app:{} ", appName);
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
		return convertHlsToMp4(broadcast, updateLastMp4CreateTime, System.currentTimeMillis());
	}

	// `synchronized` here is a webapp-wide mutex shared with convertHlsToMp4Range —
	// concurrent conversions across all streams serialize. Acceptable for v1; revisit
	// with a per-stream lock if range-endpoint throughput becomes a problem under load.
	public synchronized Mp4CreationResponse convertHlsToMp4(Broadcast broadcast, boolean updateLastMp4CreateTime, long endTime) {

		Long startTime = lastMp4CreateTimeMSForStream.get(broadcast.getStreamId());
		if (startTime == null) {
			startTime = endTime - (clipCreatorSettings.getMp4CreationIntervalSeconds() * 1000);
		}

		return doConvert(broadcast, startTime, endTime, updateLastMp4CreateTime);
	}

	/**
	 * Create an MP4 clip from HLS segments whose program-date-time falls between two UTC timestamps.
	 * Synchronous: blocks until ffmpeg finishes and the file is on disk. Used by the returnFile=true
	 * path where the caller wants the bytes back in the same request.
	 * Does NOT touch lastMp4CreateTimeMSForStream — range clips are out-of-band and must not interfere
	 * with the periodic recorder's bookkeeping.
	 */
	public synchronized Mp4CreationResponse convertHlsToMp4Range(Broadcast broadcast, long startTimeMs, long endTimeMs) {
		return doConvert(broadcast, startTimeMs, endTimeMs, false);
	}

	/**
	 * Asynchronous range clip. Validates the request and registers a placeholder VoD synchronously
	 * (so the caller gets the vodId back instantly), then runs the ffmpeg concat+trim in the
	 * background. On completion the VoD is filled in (the vodReady webhook fires) and its process
	 * status flips to finished; on failure it flips to failed. Designed for long (multi-hour) clips
	 * where the caller must not block on ffmpeg. returnFile=true callers use the synchronous path
	 * instead — you can't stream a file that doesn't exist yet.
	 *
	 * The returned response only carries the vodId; the file is not available yet. A non-success
	 * response means the cheap up-front validation failed (no playlist / no segments in range) and
	 * no background job was started nor placeholder VoD registered.
	 */
	public Mp4CreationResponse convertHlsToMp4RangeAsync(Broadcast broadcast, long startTimeMs, long endTimeMs) {
		String streamId = broadcast.getStreamId();

		ConversionInput input = prepareConversion(broadcast, startTimeMs, endTimeMs);
		if (!input.isReady()) {
			Mp4CreationResponse failure = new Mp4CreationResponse();
			failure.setMessage(input.failureMessage);
			return failure;
		}

		registerProcessingVod(broadcast, input.vodId, input.mp4FilePath, startTimeMs);

		vertx.executeBlocking(() -> {
			Mp4CreationResponse jobResult = runConversion(broadcast, input, startTimeMs, endTimeMs, false);

			String status = jobResult.isSuccess() ? VoD.PROCESS_STATUS_FINISHED : VoD.PROCESS_STATUS_FAILED;
			if (!dataStore.updateVoDProcessStatus(input.vodId, status)) {
				logger.warn("Could not update VoD {} process status to {} for stream {}", input.vodId, status, streamId);
			}
			if (!jobResult.isSuccess()) {
				logger.error("Async range MP4 creation failed for stream {} vodId {}: {}", streamId, input.vodId, jobResult.getMessage());
			}
			return null;
		}, false);

		Mp4CreationResponse response = new Mp4CreationResponse(null, input.vodId);
		response.setSuccess(true);
		response.setMessage("MP4 creation started for stream " + streamId);
		return response;
	}

	private Mp4CreationResponse doConvert(Broadcast broadcast, long startTime, long endTime, boolean updateLastMp4CreateTime) {
		ConversionInput input = prepareConversion(broadcast, startTime, endTime);
		if (!input.isReady()) {
			Mp4CreationResponse response = new Mp4CreationResponse();
			response.setMessage(input.failureMessage);
			return response;
		}
		return runConversion(broadcast, input, startTime, endTime, updateLastMp4CreateTime);
	}

	/**
	 * Fast, ffmpeg-free part of a conversion: locate the playlist, pick the segments in range and
	 * mint the vodId / output path. Returns a not-ready input (with failureMessage set) when there is
	 * no playlist or no segment covers the range.
	 */
	private ConversionInput prepareConversion(Broadcast broadcast, long startTime, long endTime) {
		String streamId = broadcast.getStreamId();
		ConversionInput input = new ConversionInput();

		File m3u8File = getM3u8File(streamId);
		if (m3u8File == null) {
			logger.error("No m3u8 file found for stream {}", streamId);
			input.failureMessage = "No m3u8 file found for stream " + streamId;
			return input;
		}

		MediaPlaylist playList = readPlaylist(m3u8File);
		if (playList == null) {
			logger.error("No HLS playlist found for stream {} from the file:{}", streamId, m3u8File.getAbsolutePath());
			input.failureMessage = "No HLS playlist found for stream " + streamId;
			return input;
		}

		ArrayList<File> tsFilesToMerge = getSegmentFilesWithinTimeRange(playList, startTime, endTime, m3u8File);
		if (tsFilesToMerge.isEmpty()) {
			logger.info("No segment file found for stream {} between {} and {}", streamId,
					dateFormat.format(new Date(startTime)), dateFormat.format(new Date(endTime)));
			input.failureMessage = "No segment file found for stream " + streamId + " between "
					+ dateFormat.format(new Date(startTime)) + " and " + dateFormat.format(new Date(endTime));
			return input;
		}

		input.m3u8File = m3u8File;
		input.tsFiles = tsFilesToMerge;
		input.vodId = RandomStringUtils.randomAlphanumeric(24);
		input.mp4FilePath = m3u8File.getParentFile().getAbsolutePath() + File.separator + input.vodId + ".mp4";
		return input;
	}

	/**
	 * Heavy part of a conversion: concat+trim the segments into the MP4 and register it as a VoD.
	 * synchronized == webapp-wide mutex shared with the periodic recorder, so conversions serialize.
	 * Acceptable for v1; revisit with a per-stream lock if range-endpoint throughput becomes a problem.
	 */
	private synchronized Mp4CreationResponse runConversion(Broadcast broadcast, ConversionInput input,
			long startTime, long endTime, boolean updateLastMp4CreateTime) {

		Mp4CreationResponse response = new Mp4CreationResponse();
		String streamId = broadcast.getStreamId();

		try {
			logger.info("number of ts files: {} for streamId:{} and startTime:{}", input.tsFiles.size(), streamId, dateFormat.format(new Date(startTime)));
			File tsFileListTextFile = writeTsFilePathsToTxt(input.m3u8File, input.tsFiles);

			if (ClipCreatorConverter.createMp4(tsFileListTextFile, input.mp4FilePath, startTime, endTime))
			{
				File mp4File = new File(input.mp4FilePath);

				long durationMs = RecordMuxer.getDurationInMs(mp4File, streamId);
				logger.info("New MP4 created from HLS playlist {} for stream {} for the time between {} and {} and duration:{}ms", input.mp4FilePath, streamId, dateFormat.format(new Date(startTime)), dateFormat.format(new Date(endTime)), durationMs);

				if (updateLastMp4CreateTime) {
					lastMp4CreateTimeMSForStream.put(streamId, endTime);
				}

				getApplication().muxingFinished(
						broadcast,
						streamId,
						mp4File,
						startTime,
						durationMs,
						ClipCreatorConverter.getResolutionHeight(input.mp4FilePath),
						null,
						input.vodId
						);
				createdMp4Count++;

				if (clipCreatorSettings.isDeleteHLSFilesAfterCreatedMp4()) {
					deleteFiles(input.tsFiles);
				}

				response = new Mp4CreationResponse(mp4File, input.vodId);
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

	/**
	 * Register a placeholder VoD in "processing" state so a caller holding the vodId can poll it
	 * immediately (and observe processing -> finished/failed) before the background ffmpeg finishes.
	 * The row is overwritten with the real metadata by muxingFinished when the clip completes.
	 */
	private void registerProcessingVod(Broadcast broadcast, String vodId, String mp4FilePath, long startTime) {
		String relativePath = AntMediaApplicationAdapter.getRelativePath(mp4FilePath);
		String vodName = new File(mp4FilePath).getName();
		String streamName = broadcast.getName() != null ? broadcast.getName() : broadcast.getStreamId();

		VoD placeholder = new VoD(streamName, broadcast.getStreamId(), relativePath, vodName,
				System.currentTimeMillis(), startTime, 0, 0, VoD.STREAM_VOD, vodId, null);
		placeholder.setProcessStatus(VoD.PROCESS_STATUS_PROCESSING);

		if (dataStore.addVod(placeholder) == null) {
			logger.warn("Could not register placeholder VoD {} for stream {}", vodId, broadcast.getStreamId());
		}
	}

	/** Prepared inputs for a conversion, computed synchronously before the ffmpeg step runs. */
	private static class ConversionInput {
		File m3u8File;
		ArrayList<File> tsFiles;
		String vodId;
		String mp4FilePath;
		String failureMessage;

		boolean isReady() {
			return failureMessage == null;
		}
	}

	public int deleteFiles(@Nonnull List<File> fileList) 
	{
		int t = 0;

		for (File file : fileList) 
		{
			try {
				boolean deleted = file.delete();
				if (!deleted) {
					logger.warn("File could not be deleted: {}", file.getAbsolutePath());
				}
				else {
					t++;
				}
			}
			catch (Exception e) {
				logger.error("Exception while deleting file: {}", file.getAbsolutePath());
			}
		}

		return t;

	}

	private void fillListWithMp4Files(File directory, List<File> mp4FileList) 
	{
		if (directory == null || !directory.isDirectory()) {
			return ;
		}

		File[] mp4Files = directory.listFiles(new FilenameFilter() 
		{
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".mp4");
			}
		});

		if (mp4Files != null) 
		{ 	
			//mp4 files can be null
			mp4FileList.addAll(Arrays.asList(mp4Files));
		}

		File[] directories = directory.listFiles(new DirectoryFilter());

		for (File dir : directories) {
			fillListWithMp4Files(dir, mp4FileList);
		}

	}

	public Result deleteMp4sNotInDB() 
	{	
		File streamsDirectory = new File(streamsFolder);

		List<File> mp4FileList = new ArrayList<>();
		fillListWithMp4Files(streamsDirectory, mp4FileList);

		long totalVoDNumber = getDataStore().getTotalVodNumber();

		List<VoD> fullVodList = new ArrayList<>();

		int batchCount = 25;
		for (int offset = 0;  offset < totalVoDNumber; offset += batchCount) 
		{
			List<VoD> vodList = getDataStore().getVodList(offset, batchCount, null, null, null, null);	
			fullVodList.addAll(vodList);
		}

		for (VoD vod : fullVodList) 
		{
			//this is relative path streams/vodId.mp4 
			File voDFileInDB = new File(IAntMediaStreamHandler.WEBAPPS_PATH + appName, vod.getFilePath());
			mp4FileList.remove(voDFileInDB);

		};

		int deletedFileCount = 0;
		List<String> notDeletedFiles = new ArrayList<>();
		if (mp4FileList.size() > 0) 
		{
			logger.info("There are {} files in the streams directory that are not in the database and in total there are {} vod records in db ", mp4FileList.size(), totalVoDNumber);
			deletedFileCount = deleteFiles(mp4FileList);

		}
		
		Result result = new Result(deletedFileCount == mp4FileList.size());
		if (deletedFileCount < mp4FileList.size()) 
		{	
			String notDeletedFilesString = String.join(",", notDeletedFiles);
			logger.info("Following files are not deleted: {}", notDeletedFilesString);
			result.setMessage("Following files are not deleted: " + notDeletedFilesString);
		} 
		else if (deletedFileCount == mp4FileList.size()) 
		{
			if (deletedFileCount > 0) 
			{
				logger.info("All files - not in the database - in the streams directory are deleted successfully for app: {} delete file count:{}", appName, deletedFileCount);
				result.setMessage("All files - not in the database - in the streams directory are deleted successfully for app:" + appName + " deleted file count:" + deletedFileCount); 					
			} 
			else 
			{
				//it meands deletedFileCount == 0 and mp4FileList.size() == 0
				logger.info("MP4 files and database is in sycn for app: {}", appName);
				result.setMessage("MP4 files and database is in synch");
			}

			result.setDataId(String.valueOf(deletedFileCount));
		}


		return result;
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

	@Override
	public void streamStarted(Broadcast broadcast) {

	}

	@Override
	public void streamFinished(Broadcast broadcast) {


		if (clipCreatorSettings.isEnabled()) 
		{
			vertx.executeBlocking(() -> {
				logger.info("stream finished for streamId:{}. It will create final recording", broadcast.getStreamId());

				convertHlsToMp4(broadcast, true, System.currentTimeMillis());
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

	public Vertx getVertx() {
		return vertx;
	}

	public void setVertx(Vertx vertx) {
		this.vertx = vertx;
	}

	public long getTimerId() {
		return timerId;
	}


}
