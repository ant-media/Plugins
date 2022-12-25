package io.antmedia.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.mockito.Mockito;

import com.google.gson.Gson;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.filter.FilterAdaptor;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.plugin.FiltersManager;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.rest.model.Result;

public class FilterManagerUnitTest {
	
	
	/**
	 * Fix for this issue https://github.com/ant-media/Ant-Media-Server/issues/4605
	 */
	@Test
	public void testReturnsErrorForNnonStreamingCase() {
		FiltersManager filtersManager = spy(new FiltersManager());
		String filterString = "{\"filterId\":\"CHVStreamFilter\",\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"stream2\",\"stream3\"],\"videoFilter\":\"[in0]split=2[out0][out1]\",\"audioFilter\":\"[in0]asplit=2[out0][out1]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"true\"}";
		Gson gson = new Gson();

		FilterConfiguration filterConfiguration = gson.fromJson(filterString, FilterConfiguration.class);
		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		when(app.getAppSettings()).thenReturn(new AppSettings());
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore );
		
		Result result = filtersManager.createFilter(filterConfiguration, app);
		assertFalse(result.isSuccess());
		
	}
	
	/**
	 * Fix for the below issues
	 * https://github.com/ant-media/Ant-Media-Server/issues/4580
	 * https://github.com/ant-media/Ant-Media-Server/issues/4541
	 */
	@Test
	public void testReturnErrorForUnActiveFilter() {
		FiltersManager filtersManager = spy(new FiltersManager());
		
		String filterString = "{\"filterId\":\"filter1\",\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"stream2\",\"stream3\"],\"videoFilter\":\"[in0]split=2[out0][out1]\",\"audioFilter\":\"[in0]asplit=2[out0][out1]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"true\"}";
		Gson gson = new Gson();
		FilterConfiguration filterConfiguration = gson.fromJson(filterString, FilterConfiguration.class);
		
		FilterAdaptor filterAdaptor = filtersManager.getFilterAdaptor(filterConfiguration.getFilterId(), false);
		
		FilterAdaptor filterAdaptor2 = filtersManager.getFilterAdaptor(filterConfiguration.getFilterId(), false);
		assertEquals(filterAdaptor, filterAdaptor2);
		
		filterAdaptor.setFilterConfiguration(filterConfiguration);
		
		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		
		boolean result = filtersManager.delete(filterConfiguration.getFilterId(), app);
		assertTrue(result);
		
		result = filtersManager.delete(filterConfiguration.getFilterId(), app);
		assertFalse(result);
	}
	
	
	
	@Test
	public void testCheckResultandFilterId() {
		FiltersManager filtersManager = spy(new FiltersManager());

		AntMediaApplicationAdapter app = mock(AntMediaApplicationAdapter.class);
		String filterString = "{\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"stream2\",\"stream3\"],\"videoFilter\":\"[in0]split=2[out0][out1]\",\"audioFilter\":\"[in0]asplit=2[out0][out1]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"true\"}";
		Gson gson = new Gson();

		FilterConfiguration filterConfiguration = gson.fromJson(filterString, FilterConfiguration.class);
		assertNull(filterConfiguration.getFilterId());
		when(app.getAppSettings()).thenReturn(new AppSettings());
		DataStore dataStore = new InMemoryDataStore("test");
		when(app.getDataStore()).thenReturn(dataStore);
		
		Broadcast broadcast = new Broadcast();
		try {
			broadcast.setStreamId("stream1");
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		broadcast.setStatus(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING);
		dataStore.save(broadcast);
		
		when(app.createCustomBroadcast("stream2")).thenReturn(Mockito.mock(IFrameListener.class));
		when(app.createCustomBroadcast("stream3")).thenReturn(Mockito.mock(IFrameListener.class));
		FilterAdaptor filterAdaptor = Mockito.mock(FilterAdaptor.class);
		Mockito.doReturn(filterAdaptor).when(filtersManager).getFilterAdaptor(Mockito.anyString(), Mockito.anyBoolean());
		Mockito.when(filterAdaptor.createOrUpdateFilter(Mockito.any(), Mockito.any())).thenReturn(new Result(true));
		
		
		Result result = filtersManager.createFilter(filterConfiguration, app);
		assertTrue(result.isSuccess());
		assertNotNull(filterConfiguration.getFilterId());

	}

}
