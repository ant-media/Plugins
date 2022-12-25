package io.antmedia.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.EncoderSettings;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.filter.FilterAdaptor;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.rest.model.Result;

@Component(value="filters.manager")
public class FiltersManager {

	public static final String BEAN_NAME = "filters.manager";
	private Map<String, FilterAdaptor> filterList = new ConcurrentHashMap<>();
	
	private static Logger logger = LoggerFactory.getLogger(FiltersManager.class);


	/**
	 * Creates or updates the filter 
	 * @param filterConfiguration
	 * @param appAdaptor
	 */
	public Result createFilter(FilterConfiguration filterConfiguration, AntMediaApplicationAdapter appAdaptor) {
		final boolean decodeStreams;
		AppSettings appSettings = appAdaptor.getAppSettings();
		if (appSettings != null) {
			List<EncoderSettings> encoderSettings = appSettings.getEncoderSettings(); 
			decodeStreams = encoderSettings == null || encoderSettings.isEmpty();
		}
		else {
			decodeStreams = true;
		}
		
		
		for(String streamId : filterConfiguration.getInputStreams()) 
		{
   			Broadcast broadcast = appAdaptor.getDataStore().get(streamId);
   			if(broadcast == null || !IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus())) {
   				logger.error("Cannot add filter because input stream id:{} in filter is not actively streaming", streamId);
   				return new Result(false, "Input stream ID: "+ streamId +" is not actively streaming");
   			}
		}
		
		String filterId = filterConfiguration.getFilterId();
		if (filterId == null || filterId.isBlank()) {
			filterId = RandomStringUtils.randomAlphanumeric(12);
			filterConfiguration.setFilterId(filterId);
		}
		
		return getFilterAdaptor(filterId, decodeStreams).createOrUpdateFilter(filterConfiguration, appAdaptor);
	}
	
	
	public FilterAdaptor getFilterAdaptor(String filterId, boolean decodeStreams) {
		return filterList.computeIfAbsent(filterId, key -> new FilterAdaptor(decodeStreams));
	}
	
	

	/**
	 * Returns the filters active in the app
	 * @return
	 */
	public List<FilterConfiguration> getfilters() {
		List<FilterConfiguration> filters = new ArrayList<>();
		for (FilterAdaptor filterAdaptor : filterList.values()) {
			filters.add(filterAdaptor.getCurrentFilterConfiguration());
		}
		return filters;
	}

	
	/**
	 * Delete the filter 
	 * 
	 * @param id is the filter id
	 * @param app
	 * @return
	 */
	public boolean delete(String id, AntMediaApplicationAdapter app) {
		FilterAdaptor filterAdaptor = filterList.get(id);
		if(filterAdaptor != null) {
			filterList.remove(id);
			return filterAdaptor.close(app);
		}
		return false;
	}

	public boolean hasFilter(String filterId) {
		return filterList.containsKey(filterId);
	}
	
	
}
