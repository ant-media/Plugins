package io.antmedia.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jakarta.servlet.ServletContext;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import com.google.gson.Gson;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.filter.FilterAdaptor;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.plugin.FiltersManager;
import io.antmedia.rest.FilterRestService;
import io.antmedia.rest.model.Result;

public class FilterRestServiceUnitTest {
	
	@Test
	public void testCreateFilterThrowException() {
		FilterRestService filterService = new FilterRestService();
		
		String filterString = "{\"filterId\":\"filter1\",\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"stream2\",\"stream3\"],\"videoFilter\":\"[in0]split=2[out0][out1]\",\"audioFilter\":\"[in0]asplit=2[out0][out1]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"true\"}";
		Gson gson = new Gson();
		FilterConfiguration filterConfiguration = gson.fromJson(filterString, FilterConfiguration.class);
		
		ServletContext servletContext = Mockito.mock(ServletContext.class);
		ApplicationContext appCtx = Mockito.mock(ApplicationContext.class);
		Mockito.when(servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE)).thenReturn(appCtx);
		Mockito.when(appCtx.getBean(FiltersManager.BEAN_NAME)).thenReturn(new FiltersManager());
		AntMediaApplicationAdapter appAdaptor = Mockito.mock(AntMediaApplicationAdapter.class);
		
		Mockito.when(appCtx.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(appAdaptor);
		Mockito.when(appAdaptor.getDataStore()).thenReturn(new InMemoryDataStore("test"));
		filterService.setServletContext(servletContext);
		
		Result result = filterService.create(filterConfiguration);
		assertFalse(result.isSuccess());
		
	}
	
	@Test
	public void testRemoveFilterThrowException() {
		
		FilterRestService filterService = new FilterRestService();
		
		String filterString = "{\"filterId\":\"filter1\",\"inputStreams\":[\"stream1\"],\"outputStreams\":[\"stream2\",\"stream3\"],\"videoFilter\":\"[in0]split=2[out0][out1]\",\"audioFilter\":\"[in0]asplit=2[out0][out1]\",\"videoEnabled\":\"true\",\"audioEnabled\":\"true\"}";
		Gson gson = new Gson();
		FilterConfiguration filterConfiguration = gson.fromJson(filterString, FilterConfiguration.class);
		
		ServletContext servletContext = Mockito.mock(ServletContext.class);
		ApplicationContext appCtx = Mockito.mock(ApplicationContext.class);
		Mockito.when(servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE)).thenReturn(appCtx);
		FiltersManager filtersManger = new FiltersManager();
		Mockito.when(appCtx.getBean(FiltersManager.BEAN_NAME)).thenReturn(filtersManger);
		AntMediaApplicationAdapter appAdaptor = Mockito.mock(AntMediaApplicationAdapter.class);
		
		Mockito.when(appCtx.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(appAdaptor);
		Mockito.when(appAdaptor.getDataStore()).thenReturn(new InMemoryDataStore("test"));
		filterService.setServletContext(servletContext);
		
		Result result = filterService.delete(filterConfiguration.getFilterId());
		assertFalse(result.isSuccess());
		
		FilterAdaptor filterAdaptor = filtersManger.getFilterAdaptor(filterConfiguration.getFilterId(), false);
		filterAdaptor.setFilterConfiguration(filterConfiguration);
		
		result = filterService.delete(filterConfiguration.getFilterId());
		assertTrue(result.isSuccess());
		
	}
	
	

}
