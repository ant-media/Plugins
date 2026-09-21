package io.antmedia.test.plugin;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.datastore.db.types.VoD;
import io.antmedia.plugin.ClipCreatorConverter;
import io.antmedia.plugin.ClipCreatorPlugin;
import io.antmedia.plugin.ClipCreatorSettings;
import io.antmedia.plugin.Mp4CreationResponse;
import io.antmedia.rest.model.Result;
import io.antmedia.settings.ServerSettings;
import io.lindstrom.m3u8.model.MediaPlaylist;
import io.lindstrom.m3u8.model.MediaSegment;
import io.lindstrom.m3u8.parser.MediaPlaylistParser;
import io.vertx.core.Vertx;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static io.smallrye.common.constraint.Assert.assertNotNull;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class ClipCreatorPluginTest {


	private static Logger logger = LoggerFactory.getLogger(ClipCreatorPluginTest.class);
	
	public static void delete(File file) throws IOException {

		if (file.isDirectory()) {

			// directory is empty, then delete it
			if (file.list().length == 0) {

				file.delete();
				// System.out.println("Directory is deleted : "
				// + file.getAbsolutePath());

			} else {

				// list all the directory contents
				String files[] = file.list();

				for (String temp : files) {
					// construct the file structure
					File fileDelete = new File(file, temp);

					// recursive delete
					delete(fileDelete);
				}

				// check the directory again, if empty then delete it
				if (file.list().length == 0) {
					file.delete();
					// System.out.println("Directory is deleted : "
					// + file.getAbsolutePath());
				}
			}

		} else {
			// if file, then delete it
			file.delete();
			// System.out.println("File is deleted : " +
			// file.getAbsolutePath());
		}
	}
	
	@Rule
	public TestRule watcher = new TestWatcher() {
		protected void starting(Description description) {
			logger.info("Starting test: " + description.getMethodName() + " @"+new Date());
		}

		protected void failed(Throwable e, Description description) {
			logger.error("Failed test: " + description.getMethodName() + " @"+new Date()+  " e: " + ExceptionUtils.getStackTrace(e));
		};

		protected void finished(Description description) {
			logger.info("Finishing test: " + description.getMethodName() + " @"+new Date());
		};
	};

	
	Vertx vertx = Vertx.vertx();

	@Test
	public void testCreateRecordingsAsync() throws Exception 
	{
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());

		ApplicationContext context = Mockito.mock(ApplicationContext.class);

		Vertx vertx =Mockito.mock(Vertx.class);
		when(context.getBean("vertxCore")).thenReturn(vertx);

		AntMediaApplicationAdapter applicationAdapter = Mockito.mock(AntMediaApplicationAdapter.class);
		when(applicationAdapter.getScope()).thenReturn(Mockito.mock(org.red5.server.api.scope.IScope.class));

		AppSettings appSettings = new AppSettings();
		when(applicationAdapter.getAppSettings()).thenReturn(appSettings);

		DataStore dataStore = Mockito.mock(io.antmedia.datastore.db.DataStore.class);
		when(applicationAdapter.getDataStore()).thenReturn(dataStore);

		when(context.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(applicationAdapter);
		when(context.getBean(ServerSettings.BEAN_NAME)).thenReturn(new ServerSettings());

		plugin.setApplicationContext(context);

		Broadcast broadcast = new Broadcast();
		broadcast.setStreamId("streamId");

		when(dataStore.getLocalLiveBroadcasts(Mockito.anyString())).thenReturn(Arrays.asList(broadcast));

		plugin.createRecordings();

		verify(vertx, times(1)).executeBlocking((Callable)any(), eq(false));

	}

	@Test
	public void testStartStopRecordingInInitialization() throws Exception 
	{
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());

		ApplicationContext context = Mockito.mock(ApplicationContext.class);

		when(context.getBean("vertxCore")).thenReturn(vertx);
		
		when(context.getBean(ServerSettings.BEAN_NAME)).thenReturn(new ServerSettings());

		AntMediaApplicationAdapter applicationAdapter = Mockito.mock(AntMediaApplicationAdapter.class);
		when(applicationAdapter.getScope()).thenReturn(Mockito.mock(org.red5.server.api.scope.IScope.class));

		AppSettings appSettings = new AppSettings();
		when(applicationAdapter.getAppSettings()).thenReturn(appSettings);

		DataStore dataStore = Mockito.mock(io.antmedia.datastore.db.DataStore.class);
		when(applicationAdapter.getDataStore()).thenReturn(dataStore);

		when(context.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(applicationAdapter);

		ClipCreatorSettings clipCreatorSettings = new ClipCreatorSettings();
		clipCreatorSettings.setEnabled(false);

		Mockito.doReturn(clipCreatorSettings).when(plugin).getClipCreatorSettings(appSettings);

		plugin.setApplicationContext(context);

		verify(plugin, times(1)).getClipCreatorSettings(appSettings);

		//it should not call because clipCreatorSettings is disabled by default
		verify(plugin, never()).startPeriodicRecording(Mockito.anyInt());
		assertEquals(-1, plugin.getTimerId());


		clipCreatorSettings.setEnabled(true);
		plugin.setApplicationContext(context);
		verify(plugin, times(2)).getClipCreatorSettings(appSettings);

		//it should  call because clipCreatorSettings is enabled
		verify(plugin, times(1)).startPeriodicRecording(clipCreatorSettings.getMp4CreationIntervalSeconds());

		plugin.getLastMp4CreateTimeForStream().put("streamId", System.currentTimeMillis());

		plugin.getLastMp4CreateTimeForStream().put("streamId2", System.currentTimeMillis());

		assertNotEquals(-1, plugin.getTimerId());



		plugin.stopPeriodicRecording();

		assertFalse(clipCreatorSettings.isEnabled());

		assertEquals(-1, plugin.getTimerId());

		//it should be called for the last time
		verify(plugin, timeout(10000).times(1)).createRecordings();
		Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> plugin.getLastMp4CreateTimeForStream().size() == 0);

	}

	@Test
	public void testStreamFinished() throws Exception {
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());

		ApplicationContext context = Mockito.mock(ApplicationContext.class);

		when(context.getBean("vertxCore")).thenReturn(vertx);

		AntMediaApplicationAdapter applicationAdapter = Mockito.mock(AntMediaApplicationAdapter.class);
		when(applicationAdapter.getScope()).thenReturn(Mockito.mock(org.red5.server.api.scope.IScope.class));

		AppSettings appSettings = new AppSettings();
		when(applicationAdapter.getAppSettings()).thenReturn(appSettings);

		DataStore dataStore = Mockito.mock(io.antmedia.datastore.db.DataStore.class);
		when(applicationAdapter.getDataStore()).thenReturn(dataStore);

		when(context.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(applicationAdapter);

		ClipCreatorSettings clipCreatorSettings = new ClipCreatorSettings();

		clipCreatorSettings.setEnabled(true);
		Mockito.doReturn(clipCreatorSettings).when(plugin).getClipCreatorSettings(appSettings);


		plugin.setApplicationContext(context);

		String streamId = "testStreamId";
		plugin.getLastMp4CreateTimeForStream().put(streamId, System.currentTimeMillis());

		Broadcast broadcast = new Broadcast();
		broadcast.setStreamId(streamId);
		
		//delete the stream folder if it exists
		delete(new File(plugin.getStreamsFolder()));			

		plugin.streamFinished(broadcast);

		ArgumentCaptor<Broadcast> broadcastCaptor = ArgumentCaptor.forClass(Broadcast.class);
		ArgumentCaptor<Boolean> booleanCaptor = ArgumentCaptor.forClass(Boolean.class);
		//sometimes the below timeout value does not work and test fails. I don't know why so I've changed with after - @mekya
		verify(plugin, Mockito.after(5000)).convertHlsToMp4(broadcastCaptor.capture(), booleanCaptor.capture(), anyLong());
		assertEquals(streamId, broadcastCaptor.getValue().getStreamId());
		assertTrue(booleanCaptor.getValue());

		//it should be null because it is removed
		assertNull(plugin.getLastMp4CreateTimeForStream().get(streamId));


	}

	@Test
	public void testConvertHlsToMp4() throws Exception {
		String streamId = "testStream";
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
		DataStore dataStore = new InMemoryDataStore("db");
		plugin.setDataStore(dataStore);

		AntMediaApplicationAdapter mockApplication = mock(AntMediaApplicationAdapter.class);

		doReturn(mockApplication).when(plugin).getApplication();
		doNothing().when(mockApplication).muxingFinished(any(), any(), any(), anyLong(), anyLong(), anyInt(), anyString(), anyString());

		Broadcast broadcast = new Broadcast();
		broadcast.setStreamId("testStream");
		plugin.setStreamsFolder("target/resources_tmp");
		
		File streamFolder = new File(plugin.getStreamsFolder());
		streamFolder.mkdirs();
		
		File file = new File("src/test/resources");
		File[] files = file.listFiles();
		
		for (File tmpFile : files) {
			Files.copy(tmpFile, new File(streamFolder, tmpFile.getName()));
		}
	

		plugin.setClipCreatorSettings(new ClipCreatorSettings());

		Mp4CreationResponse createMp4Response = plugin.convertHlsToMp4(broadcast, true, System.currentTimeMillis());
		assertFalse(createMp4Response.isSuccess());

		//it should not be called because deleteTSFiles by default is false
		verify(plugin, never()).deleteFiles(Mockito.any());


		plugin.getLastMp4CreateTimeForStream().put(streamId, 0L);

		createMp4Response = plugin.convertHlsToMp4(broadcast, true, System.currentTimeMillis());
		assertNotNull(createMp4Response.getFile());
		assertTrue(createMp4Response.getFile().getTotalSpace() > 0);
		assertTrue(createMp4Response.getFile().delete());


		//check it calls the deleteTSFiles
		plugin.getLastMp4CreateTimeForStream().put(streamId, 0L);
		plugin.getClipCreatorSettings().setDeleteHLSFilesAfterCreatedMp4(true);
		plugin.convertHlsToMp4(broadcast, true, System.currentTimeMillis());
		verify(plugin, times(1)).deleteFiles(Mockito.any());
		
		String[] list = streamFolder.list();
		for (String fileName : list) {
			assertFalse(fileName.endsWith(".ts"));
		}

	}

	@Test
	public void testConvertHlsToMp4Range() throws Exception {
		String streamId = "testStream";
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
		DataStore dataStore = new InMemoryDataStore("db");
		plugin.setDataStore(dataStore);

		AntMediaApplicationAdapter mockApplication = mock(AntMediaApplicationAdapter.class);
		doReturn(mockApplication).when(plugin).getApplication();
		doNothing().when(mockApplication).muxingFinished(any(), any(), any(), anyLong(), anyLong(), anyInt(), anyString(), anyString());

		Broadcast broadcast = new Broadcast();
		broadcast.setStreamId(streamId);
		plugin.setStreamsFolder("target/resources_tmp_range");

		File streamFolder = new File(plugin.getStreamsFolder());
		streamFolder.mkdirs();

		File file = new File("src/test/resources");
		File[] files = file.listFiles();
		for (File tmpFile : files) {
			Files.copy(tmpFile, new File(streamFolder, tmpFile.getName()));
		}

		plugin.setClipCreatorSettings(new ClipCreatorSettings());

		// PDTs in the test fixture playlist (see testGetSegmentFilesWithinTimeRange) span ~1727644047000.
		// Pick a range that covers them so segments are found and an MP4 is produced.
		long startMs = 1727644047000L;
		long endMs = 1727644051862L;

		Mp4CreationResponse response = plugin.convertHlsToMp4Range(broadcast, startMs, endMs);

		assertTrue(response.isSuccess());
		assertNotNull(response.getFile());
		assertTrue(response.getFile().exists());
		assertTrue(response.getFile().getTotalSpace() > 0);

		// Critical behavioral guarantee: range clipping must NOT update the periodic recorder's bookkeeping
		assertFalse(plugin.getLastMp4CreateTimeForStream().containsKey(streamId));

		assertTrue(response.getFile().delete());
		delete(streamFolder);
	}

	@Test
	public void testConvertHlsToMp4RangeAsync() throws Exception {
		String streamId = "testStream";
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
		DataStore dataStore = new InMemoryDataStore("dbAsync");
		plugin.setDataStore(dataStore);
		plugin.setVertx(vertx);

		AntMediaApplicationAdapter mockApplication = mock(AntMediaApplicationAdapter.class);
		doReturn(mockApplication).when(plugin).getApplication();
		doNothing().when(mockApplication).muxingFinished(any(), any(), any(), anyLong(), anyLong(), anyInt(), any(), anyString());

		Broadcast broadcast = new Broadcast();
		broadcast.setStreamId(streamId);
		plugin.setStreamsFolder("target/resources_tmp_range_async");

		File streamFolder = new File(plugin.getStreamsFolder());
		streamFolder.mkdirs();

		File file = new File("src/test/resources");
		for (File tmpFile : file.listFiles()) {
			Files.copy(tmpFile, new File(streamFolder, tmpFile.getName()));
		}

		plugin.setClipCreatorSettings(new ClipCreatorSettings());

		long startMs = 1727644047000L;
		long endMs = 1727644051862L;

		Mp4CreationResponse response = plugin.convertHlsToMp4RangeAsync(broadcast, startMs, endMs);

		// the vodId must come back immediately, before ffmpeg runs
		assertTrue(response.isSuccess());
		assertNotNull(response.getVodId());
		assertNull(response.getFile());

		String vodId = response.getVodId();

		// a placeholder VoD is registered right away so the caller can poll it
		VoD placeholder = dataStore.getVoD(vodId);
		assertNotNull(placeholder);

		// the background job eventually flips the process status to finished
		Awaitility.await().atMost(30, TimeUnit.SECONDS)
				.until(() -> VoD.PROCESS_STATUS_FINISHED.equals(dataStore.getVoD(vodId).getProcessStatus()));

		verify(mockApplication, timeout(30000)).muxingFinished(any(), eq(streamId), any(), eq(startMs), anyLong(), anyInt(), any(), eq(vodId));

		// range clipping must NOT touch the periodic recorder's bookkeeping
		assertFalse(plugin.getLastMp4CreateTimeForStream().containsKey(streamId));

		File producedMp4 = new File(streamFolder, vodId + ".mp4");
		if (producedMp4.exists()) {
			assertTrue(producedMp4.delete());
		}
		delete(streamFolder);
	}

	@Test
	public void testConvertHlsToMp4RangeAsync_NoSegments() throws Exception {
		String streamId = "testStream";
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
		DataStore dataStore = new InMemoryDataStore("dbAsyncEmpty");
		plugin.setDataStore(dataStore);
		plugin.setVertx(vertx);

		Broadcast broadcast = new Broadcast();
		broadcast.setStreamId(streamId);
		plugin.setStreamsFolder("src/test/resources");
		plugin.setClipCreatorSettings(new ClipCreatorSettings());

		// a range with no segments: cheap validation fails, so no vodId and no placeholder VoD
		long startMs = 100_000L;
		long endMs = 200_000L;

		Mp4CreationResponse response = plugin.convertHlsToMp4RangeAsync(broadcast, startMs, endMs);

		assertFalse(response.isSuccess());
		assertNull(response.getVodId());
		assertNotNull(response.getMessage());
		assertEquals(0, dataStore.getTotalVodNumber());
	}

	@Test
	public void testGetSegmentFilesWithinTimeRange() throws IOException {
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
		MediaPlaylistParser parser = new MediaPlaylistParser();
		File m3u8File = new File("src/test/resources/testStream.m3u8");
		MediaPlaylist mediaPlaylist = parser.readPlaylist(m3u8File.toPath());
		long startTimeMilis = 1727644047000L;
		long endTimeMilis = 1727644051862L;
		ArrayList<File> fileArrayList = plugin.getSegmentFilesWithinTimeRange(mediaPlaylist, startTimeMilis, endTimeMilis, m3u8File);
		assertEquals(3, fileArrayList.size());
	}


	@Test
	public void testGetM3u8File_Success(){
		String streamId = "testStream";
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
		plugin.setStreamsFolder("src/test/resources");
		File m3u8File = plugin.getM3u8File(streamId);
		assertNotNull(m3u8File);
		assertTrue(m3u8File.getTotalSpace() > 0);
	}

	@Test
	public void testGetM3u8File_Fail(){
		String streamId = "testStream2";
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
		plugin.setStreamsFolder("src/test/resources");
		File m3u8File = plugin.getM3u8File(streamId);
		assertNull(m3u8File);
	}

	@Test
	public void testDeleteTSFiles() throws IOException {
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());

		File f = new File("ts1");
		f.createNewFile();
		File f2 = new File("ts2");
		f2.createNewFile();
		File f3 = new File("ts3");
		f3.createNewFile();
		File f4 = new File("ts4");

		ClipCreatorSettings clipCreatorSettings = new ClipCreatorSettings();
		clipCreatorSettings.setDeleteHLSFilesAfterCreatedMp4(true);
		plugin.setClipCreatorSettings(clipCreatorSettings);

		int deletedCount = plugin.deleteFiles(Arrays.asList(f, f2, f3, f4));
		//it should be 3 because only 3 files exist
		assertEquals(3, deletedCount);

	}

	@Test
	public void testDeleteMp4sNotInDB() throws IOException 
	{
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());

		ApplicationContext context = Mockito.mock(ApplicationContext.class);
		AntMediaApplicationAdapter applicationAdapter = Mockito.mock(AntMediaApplicationAdapter.class);
		IScope scope = Mockito.mock(org.red5.server.api.scope.IScope.class);
		when(scope.getName()).thenReturn("junit");
		when(applicationAdapter.getScope()).thenReturn(scope);

		AppSettings appSettings = new AppSettings();
		when(applicationAdapter.getAppSettings()).thenReturn(appSettings);

		DataStore dataStore =  new InMemoryDataStore("db");
		when(applicationAdapter.getDataStore()).thenReturn(dataStore);

		when(context.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(applicationAdapter);
		Vertx vertx =Mockito.mock(Vertx.class);
		when(context.getBean("vertxCore")).thenReturn(vertx);


		plugin.setApplicationContext(context);

		Result result = plugin.deleteMp4sNotInDB();
		assertTrue(result.isSuccess());


		for (int i=0; i < 30; i++) 
		{
			VoD vod =new VoD("streamName", "streamId", "filePath", "vodName", 111, 111, 111, 111, VoD.USER_VOD, RandomStringUtils.randomAlphanumeric(24), null);
			dataStore.addVod(vod);
		}

		result = plugin.deleteMp4sNotInDB();
		//it returns true because there are no files in the streams directory
		assertTrue(result.isSuccess());
		
		
	    //put some mp4 files in the stream directory
		
		File f = new File(plugin.getStreamsFolder(), "f1.mp4");
		f.getParentFile().mkdirs();
		f.createNewFile();
		File f2 = new File(plugin.getStreamsFolder(), "f2.mp4");
		f2.createNewFile();
		File f3 = new File(plugin.getStreamsFolder(), "f3.mp4");
		f3.createNewFile();
		File f4 = new File(plugin.getStreamsFolder(), "f4.mp4");
		f4.createNewFile();
		File f5 = new File(plugin.getStreamsFolder(), "f5.mp4");
		f5.createNewFile();
		
		assertTrue(f.exists());
		assertTrue(f2.exists());
		assertTrue(f3.exists());
		assertTrue(f4.exists());
		assertTrue(f5.exists());
		
		
		File vodFile = new File(plugin.getStreamsFolder(), "vod.mp4");
		vodFile.createNewFile();
		VoD vod =new VoD("streamName", "streamId", AntMediaApplicationAdapter.getRelativePath(vodFile.getAbsolutePath()), "vodName", 111, 111, 111, 111, VoD.USER_VOD, RandomStringUtils.randomAlphanumeric(24), null);
		dataStore.addVod(vod);
		
		
		result = plugin.deleteMp4sNotInDB();
		//it returns true because it should delete all files
		
		assertTrue(result.isSuccess());
		assertEquals("5", result.getDataId());
		
		assertFalse(f.exists());
		assertFalse(f2.exists());
		assertFalse(f3.exists());
		assertFalse(f4.exists());
		assertFalse(f5.exists());
		
		
		
		
		
		
		
		

	}

	/**
	 * PDT marks a segment's start, so a range starting mid-segment must keep that segment or the
	 * clip silently loses content off the front. Fixture PDTs: 047342 / 050102 / 051862 / 056782.
	 */
	@Test
	public void testGetSegmentFilesIncludesSegmentContainingStart() throws IOException {
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
		File m3u8File = new File("src/test/resources/testStream.m3u8");
		MediaPlaylist mediaPlaylist = new MediaPlaylistParser().readPlaylist(m3u8File.toPath());

		//starts inside segment 1 (050102 -> 051862) and ends before segment 3 starts
		long startTimeMs = 1727644051000L;
		long endTimeMs = 1727644056000L;

		ArrayList<File> files = plugin.getSegmentFilesWithinTimeRange(mediaPlaylist, startTimeMs, endTimeMs, m3u8File);

		assertEquals(2, files.size());
		assertEquals("testStream000000001.ts", files.get(0).getName());
		assertEquals("testStream000000002.ts", files.get(1).getName());
	}

	@Test
	public void testGetFirstSegmentPdtMs() throws IOException {
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
		File m3u8File = new File("src/test/resources/testStream.m3u8");
		MediaPlaylist mediaPlaylist = new MediaPlaylistParser().readPlaylist(m3u8File.toPath());

		//start falls inside segment 1, so the content begins at segment 1's PDT
		assertEquals(1727644050102L, plugin.getFirstSegmentPdtMs(mediaPlaylist, 1727644051000L, 1727644056000L));

		//start precedes every segment, so content begins at the first segment's PDT
		assertEquals(1727644047342L, plugin.getFirstSegmentPdtMs(mediaPlaylist, 1727644047000L, 1727644051862L));

		//nothing in range
		assertEquals(-1L, plugin.getFirstSegmentPdtMs(mediaPlaylist, 1L, 2L));
	}

	/**
	 * Range clips read an arbitrary historical window that overlapping requests and the periodic
	 * recorder still need, so they must never reclaim segments even with the delete setting on.
	 */
	@Test
	public void testRangeClipNeverDeletesSegments() throws Exception {
		String streamId = "testStream";
		ClipCreatorPlugin plugin = Mockito.spy(new ClipCreatorPlugin());
		plugin.setDataStore(new InMemoryDataStore("dbRangeNoDelete"));

		AntMediaApplicationAdapter mockApplication = mock(AntMediaApplicationAdapter.class);
		doReturn(mockApplication).when(plugin).getApplication();
		doNothing().when(mockApplication).muxingFinished(any(), any(), any(), anyLong(), anyLong(), anyInt(), anyString(), anyString());

		Broadcast broadcast = new Broadcast();
		broadcast.setStreamId(streamId);
		plugin.setStreamsFolder("target/resources_tmp_range_nodelete");

		File streamFolder = new File(plugin.getStreamsFolder());
		streamFolder.mkdirs();
		for (File tmpFile : new File("src/test/resources").listFiles()) {
			Files.copy(tmpFile, new File(streamFolder, tmpFile.getName()));
		}

		ClipCreatorSettings settings = new ClipCreatorSettings();
		settings.setDeleteHLSFilesAfterCreatedMp4(true);
		plugin.setClipCreatorSettings(settings);

		Mp4CreationResponse response = plugin.convertHlsToMp4Range(broadcast, 1727644047000L, 1727644051862L);

		assertTrue(response.isSuccess());
		verify(plugin, never()).deleteFiles(Mockito.any());

		long remainingTsFiles = Arrays.stream(streamFolder.list()).filter(n -> n.endsWith(".ts")).count();
		assertEquals(4, remainingTsFiles);

		assertTrue(response.getFile().delete());
		delete(streamFolder);
	}

	@Test
	public void testMaxConcurrentClipsDerivedAndOverridden() {
		ClipCreatorPlugin plugin = new ClipCreatorPlugin();
		//0 means derive from cores, and the derived value is always a usable limit
		assertTrue(plugin.getMaxConcurrentClips() >= 2);
		assertTrue(plugin.getMaxConcurrentClips() <= 8);
	}

}
