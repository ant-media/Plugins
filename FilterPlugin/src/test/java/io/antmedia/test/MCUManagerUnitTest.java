package io.antmedia.test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.cluster.IStreamInfo;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.datastore.db.types.ConferenceRoom;
import io.antmedia.datastore.db.types.StreamInfo;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.plugin.FiltersManager;
import io.antmedia.plugin.MCUManager;
import io.antmedia.rest.model.Result;
import io.antmedia.webrtc.VideoCodec;
import io.antmedia.webrtc.api.IWebRTCAdaptor;
import io.antmedia.websocket.WebSocketConstants;
import io.vertx.core.Vertx;

public class MCUManagerUnitTest {
	
	@Rule
	public TestRule watcher = new TestWatcher() {
		protected void starting(Description description) {
			System.out.println("Starting test: " + description.getMethodName());
		}

		protected void failed(Throwable e, Description description) {
			System.out.println("Failed test: " + description.getMethodName() + " e: " + ExceptionUtils.getStackTrace(e));
		};

		protected void finished(Description description) {
			System.out.println("Finishing test: " + description.getMethodName());
		};
	};
	
	
	
	@Test
	public void testLeftTheRoomNotification() {
		MCUManager mcuManager = spy(new MCUManager());
		doNothing().when(mcuManager).triggerUpdate(anyString(), anyBoolean());
		
		FiltersManager filtersManager = spy(new FiltersManager());
		
		doReturn(filtersManager).when(mcuManager).getFiltersManager();
		
		String room1 = "room1";
		String room2 = "room2";
		
		String stream1 = "stream1";
		String stream2 = "stream2";

		doReturn(true).when(filtersManager).hasFilter(room1);
		doReturn(false).when(filtersManager).hasFilter(room2);

		mcuManager.leftTheRoom(room1, stream1);
		verify(mcuManager, times(1)).triggerUpdate(room1, true);
		
		mcuManager.leftTheRoom(room2, stream2);
		verify(mcuManager, never()).triggerUpdate(eq(room2), anyBoolean());
	}
	
	
	@Test
	public void testRemoveNonZombiRoomFilter() {
		String roomId = "room"+RandomUtils.nextInt();
		MCUManager mcuManager = spy(new MCUManager());
		FiltersManager filtersManager = spy(new FiltersManager());
		
		doReturn(filtersManager).when(mcuManager).getFiltersManager();
		
		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore );
		doReturn(app).when(mcuManager).getApplication();

		
		ConferenceRoom room = new ConferenceRoom();
		room.setRoomId(roomId);
		room.setMode(WebSocketConstants.MCU);
		
		dataStore.createConferenceRoom(room);
		
		mcuManager.updateRoomFilter(roomId);
		
		verify(filtersManager, times(1)).delete(roomId, app);


	}
	
	@Test
	public void testMCUWithOtherRooms() {
		
		String roomId = "room"+RandomUtils.nextInt();
		MCUManager mcuManager = spy(new MCUManager());
		FiltersManager filtersManager = spy(new FiltersManager());
		
		doReturn(filtersManager).when(mcuManager).getFiltersManager();
		
		doReturn(new Result(true)).when(filtersManager).createFilter(any(), any());

		
		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore );
		when(app.getVertx()).thenReturn(Vertx.vertx());
		doReturn(app).when(mcuManager).getApplication();

		String streamId = "stream"+RandomUtils.nextInt();
		Broadcast broadcast = new Broadcast();
		try {
			broadcast.setStreamId(streamId);
		} catch (Exception e) {
			e.printStackTrace();
		}
		broadcast.setStatus(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING);
		dataStore.save(broadcast);
		
		ConferenceRoom room = new ConferenceRoom();
		room.setRoomId(roomId);
		room.setMode(WebSocketConstants.LEGACY);
		room.setRoomStreamList(Arrays.asList(streamId));
		dataStore.createConferenceRoom(room);
		
		mcuManager.setApplicationContext(null);
		
		mcuManager.addCustomRoom(roomId);
		
		verify(filtersManager, timeout(MCUManager.CONFERENCE_INFO_POLL_PERIOD_MS*4000).times(1)).createFilter(any(), eq(app));

	}
	
	/*
	 * This test tests to generated filter text for the rooms who has both audio only and normal participants
	 */
	@Test
	public void testFilterTextForMixedRoom() {

		ApplicationContext applicationContext = mock(ApplicationContext.class);

		String roomId = "room"+RandomUtils.nextInt();
		MCUManager mcuManager = spy(new MCUManager());
		FiltersManager filtersManager = mock(FiltersManager.class);
		doReturn(filtersManager).when(mcuManager).getFiltersManager();
		Result result = new Result(true);
		when(filtersManager.createFilter(any(), any())).thenReturn(result );

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		when(applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(app);

		DataStore dataStore = mock(DataStore.class);
		when(app.getDataStore()).thenReturn(dataStore);
		doNothing().when(app).addStreamListener(mcuManager);

		doReturn(app).when(mcuManager).getApplication();
		Vertx vertx = mock(Vertx.class);
		when(vertx.setPeriodic(anyLong(), any())).thenReturn(5l);

		when(app.getVertx()).thenReturn(vertx );

		IWebRTCAdaptor webRTCAdaptor = mock(IWebRTCAdaptor.class);
		when(applicationContext.getBean(IWebRTCAdaptor.BEAN_NAME)).thenReturn(webRTCAdaptor);

		mcuManager.setApplicationContext(applicationContext);


		String s1 = "stream1";
		List<IStreamInfo> siList1 = new ArrayList<IStreamInfo>();
		IStreamInfo si1 = mock(IStreamInfo.class);
		when(si1.getVideoCodec()).thenReturn(VideoCodec.H264);
		siList1.add(si1);
		Broadcast broadcast1 = new Broadcast(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING, s1);
		try {
			broadcast1.setStreamId(s1);
		} catch (Exception e) {
			e.printStackTrace();
		}
		when(dataStore.get(s1)).thenReturn(broadcast1);



		String s2 = "stream2";
		List<IStreamInfo> siList2 = new ArrayList<IStreamInfo>();
		IStreamInfo si2 = mock(IStreamInfo.class);
		when(si2.getVideoCodec()).thenReturn(VideoCodec.NOVIDEO);
		siList2.add(si2);
		Broadcast broadcast2 = new Broadcast(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING, s2);
		try {
			broadcast1.setStreamId(s2);
		} catch (Exception e) {
			e.printStackTrace();
		}
		when(dataStore.get(s2)).thenReturn(broadcast2);

		String s3 = "stream3";
		List<IStreamInfo> siList3 = new ArrayList<IStreamInfo>();
		IStreamInfo si3 = mock(IStreamInfo.class);
		when(si3.getVideoCodec()).thenReturn(VideoCodec.H264);
		siList3.add(si3);
		Broadcast broadcast3 = new Broadcast(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING, s3);
		try {
			broadcast3.setStreamId(s3);
		} catch (Exception e) {
			e.printStackTrace();
		}
		when(dataStore.get(s3)).thenReturn(broadcast3);

		when(webRTCAdaptor.getStreamInfo(s1)).thenReturn(siList1);
		when(webRTCAdaptor.getStreamInfo(s2)).thenReturn(siList2);
		when(webRTCAdaptor.getStreamInfo(s3)).thenReturn(siList3);


		ConferenceRoom room = new ConferenceRoom();
		room.setMode(WebSocketConstants.MCU);
		room.setRoomId(roomId);
		when(dataStore.getConferenceRoom(roomId)).thenReturn(room);
		ArgumentCaptor<FilterConfiguration> filterConf = ArgumentCaptor.forClass(FilterConfiguration.class);

		room.getRoomStreamList().add(s1);
		mcuManager.updateRoomFilter(roomId);
		verify(filtersManager, times(1)).createFilter(filterConf.capture(), eq(app));
		assertTrue(filterConf.getValue().getVideoFilter().contains("in0"));

		room.getRoomStreamList().add(s2);
		mcuManager.updateRoomFilter(roomId);
		verify(filtersManager, times(2)).createFilter(filterConf.capture(), eq(app));
		assertTrue(filterConf.getValue().getVideoFilter().contains("in0"));
		assertFalse(filterConf.getValue().getVideoFilter().contains("in1"));

		room.getRoomStreamList().add(s3);
		mcuManager.updateRoomFilter(roomId);
		verify(filtersManager, times(3)).createFilter(filterConf.capture(), eq(app));
		assertTrue(filterConf.getValue().getVideoFilter().contains("in0"));
		assertFalse(filterConf.getValue().getVideoFilter().contains("in1"));
		assertTrue(filterConf.getValue().getVideoFilter().contains("in2"));

	}
	
}
