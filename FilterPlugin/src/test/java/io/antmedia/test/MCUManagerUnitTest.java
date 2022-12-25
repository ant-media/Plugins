package io.antmedia.test;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacpp.Pointer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mockito.Mockito;

import com.google.gson.Gson;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.datastore.db.types.ConferenceRoom;
import io.antmedia.filter.FilterAdaptor;
import io.antmedia.filter.utils.Filter;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.filter.utils.FilterGraph;
import io.antmedia.filter.utils.IFilteredFrameListener;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.plugin.FiltersManager;
import io.antmedia.plugin.MCUManager;
import io.antmedia.rest.FilterRestService;
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
		
		doReturn(true).when(filtersManager).createFilter(any(), any());

		
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
	
}
