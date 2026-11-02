package io.antmedia.test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

import java.util.Arrays;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.filter.utils.MCUFilterTextGenerator;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.plugin.FiltersManager;
import io.antmedia.plugin.MCUManager;
import io.antmedia.rest.model.Result;
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
	public void testRemoveNonZombiRoomFilter() throws Exception {
		String roomId = "room"+RandomUtils.nextInt();
		MCUManager mcuManager = spy(new MCUManager());
		FiltersManager filtersManager = spy(new FiltersManager());
		
		doReturn(filtersManager).when(mcuManager).getFiltersManager();
		
		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore );
		doReturn(app).when(mcuManager).getApplication();
		when(app.getVertx()).thenReturn(Vertx.vertx());

		
		Broadcast room = new Broadcast();
		room.setStreamId(roomId);
		
		mcuManager.addCustomRoom(roomId, true, true);
		
		dataStore.save(room);
		
		mcuManager.updateRoomFilter(roomId);
		
		verify(filtersManager, times(1)).delete(roomId, app);
		
		mcuManager.removeCustomRoom(roomId);


	}
	
	@Test
	public void testDeleteCustomRoom() throws Exception {
		String roomId = "room"+RandomUtils.nextInt();
		MCUManager mcuManager = spy(new MCUManager());
		FiltersManager filtersManager = spy(new FiltersManager());
		
		doReturn(filtersManager).when(mcuManager).getFiltersManager();
		
		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore );
		doReturn(app).when(mcuManager).getApplication();
		when(app.getVertx()).thenReturn(Vertx.vertx());
		
		Broadcast room = new Broadcast();
		room.setStreamId(roomId);
		dataStore.save(room);
		
		mcuManager.addCustomRoom(roomId, true, true);
		
		mcuManager.customFilterAdded(roomId);
		
		mcuManager.updateRoomFilter(roomId);

		//it should be deleted because there is no active subtrack
		verify(filtersManager, times(1)).delete(roomId, app);
		
	}
	
	@Test
	public void testMCUWithOtherRooms() throws Exception {
		
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
		broadcast.setUpdateTime(System.currentTimeMillis());
		broadcast.setStatus(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING);
		dataStore.save(broadcast);
		
		Broadcast room = new Broadcast();
		room.setStreamId(roomId);
		dataStore.save(room);
		
		//create a broadcast with streamId and maintrack roomId and save to the datastore
		Broadcast broadcast2 = new Broadcast();
		broadcast2.setStreamId(roomId);
		broadcast2.setStatus(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING);
		broadcast2.setUpdateTime(System.currentTimeMillis());
		broadcast2.setMainTrackStreamId(roomId);
		dataStore.save(broadcast2);
		

		mcuManager.addCustomRoom(roomId, true, true);
		
		mcuManager.setApplicationContext(null);
		
		mcuManager.addCustomRoom(roomId, true, true);
		
		verify(filtersManager, timeout(MCUManager.CONFERENCE_INFO_POLL_PERIOD_MS*4000).times(1)).createFilter(any(), eq(app));

	}

	@Test
	public void testCreateAudioFilter() {
		assertEquals("[in0]acopy[out0]", MCUFilterTextGenerator.createAudioFilter(1));
		assertEquals("[in0][in1][in2]amix=inputs=3[out0]", MCUFilterTextGenerator.createAudioFilter(3));
	}

	@Test
	public void testCreateVideoFilter() {
		assertEquals("[in0]copy[out0]", MCUFilterTextGenerator.createVideoFilter(1));

		//single row
		assertEquals("[in0]scale=354:234:force_original_aspect_ratio=decrease,pad=360:240:3:3:color=black[s0];"
				+ "[in1]scale=354:234:force_original_aspect_ratio=decrease,pad=360:240:3:3:color=black[s1];"
				+ "[s0][s1]hstack=inputs=2,pad=720:480:(ow-iw)/2:(oh-ih)/2[out0]",
				MCUFilterTextGenerator.createVideoFilter(2));

		//last row has a single stream, it is padded without hstack
		assertTrue(MCUFilterTextGenerator.createVideoFilter(3).endsWith(
				"[s0][s1]hstack=inputs=2[l0];[s2]pad=720:240:180[l1];[l0][l1]vstack=inputs=2,pad=720:480:(ow-iw)/2:(oh-ih)/2[out0]"));

		//full grid, no padding for the last row
		assertTrue(MCUFilterTextGenerator.createVideoFilter(4).endsWith(
				"[s0][s1]hstack=inputs=2[l0];[s2][s3]hstack=inputs=2[l1];[l0][l1]vstack=inputs=2,pad=720:480:(ow-iw)/2:(oh-ih)/2[out0]"));

		//last row is shorter than the others, it is stacked and then padded
		String fiveStreams = MCUFilterTextGenerator.createVideoFilter(5);
		assertTrue(fiveStreams.startsWith("[in0]scale=234:154:force_original_aspect_ratio=decrease,pad=240:160:3:3:color=black[s0];"));
		assertTrue(fiveStreams.endsWith(
				"[s0][s1][s2]hstack=inputs=3[l0];[s3][s4]hstack=inputs=2[l1];[l1]pad=720:160:120[l1];[l0][l1]vstack=inputs=2,pad=720:480:(ow-iw)/2:(oh-ih)/2[out0]"));
	}

}
