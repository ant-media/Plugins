package io.antmedia.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
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
	public Result createFilter(FilterConfiguration filterConfiguration, AntMediaApplicationAdapter appAdaptor) 
	{
		final boolean defaultDecodeStreamValue;
		AppSettings appSettings = appAdaptor.getAppSettings();
		if (appSettings != null) 
		{
			List<EncoderSettings> encoderSettings = appSettings.getEncoderSettings(); 
			defaultDecodeStreamValue = encoderSettings == null || encoderSettings.isEmpty();
		}
		else {
			defaultDecodeStreamValue = true;
		}
				
		Map<String, Boolean> decodeStreamMap = new ConcurrentHashMap<>();
		
		for(String streamId : filterConfiguration.getInputStreams()) 
		{
   			Broadcast broadcast = appAdaptor.getDataStore().get(streamId);
   			if(broadcast == null || !IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus())
   					|| StringUtils.isBlank(broadcast.getOriginAdress())) 
   			{
   				String status = broadcast != null ? broadcast.getStatus() : "null";
   				String originAddress = broadcast != null ? broadcast.getOriginAdress() : "null";
   				logger.error("Cannot add filter because input stream id:{} in filter is not actively streaming or not origin address. It's status is {} and origin address is {}", 
   						streamId, status, originAddress);
   				return new Result(false, "Input stream ID: "+ streamId +" is not actively streaming");
   			}
   			
   			//if origin stream is not in this instance, we are going to decode stream locally
   			if (!StringUtils.equals(appAdaptor.getServerSettings().getHostAddress(), broadcast.getOriginAdress())) 
   			{
   				logger.info("Origin stream is not in this instance. We are going to decode stream locally for stream id {} stream origin is {}", streamId, broadcast.getOriginAdress());
   				decodeStreamMap.put(streamId, true);
   			}
   			else 
   			{
   				//if origin stream is in this instance, we are going to decode stream locally
   				boolean decodeStream = defaultDecodeStreamValue;
   				if (broadcast.getEncoderSettingsList() != null) 
   				{
   					if (broadcast.getEncoderSettingsList().isEmpty()) {
   	   					logger.debug("Encoder settings list is empty. We are going to decode stream locally for stream id {}", streamId);
   						decodeStream = true;
   					}
   					else {
   						decodeStream = false;
   					}
   				}
   				
   				logger.debug("Putting decode stream value {} for stream id {}", decodeStream, streamId);
				decodeStreamMap.put(streamId, decodeStream);
   			}
		}
		
		String filterId = filterConfiguration.getFilterId();
		if (filterId == null || filterId.isBlank()) {
			filterId = RandomStringUtils.randomAlphanumeric(12);
			filterConfiguration.setFilterId(filterId);
		}
		
		logger.info("Creating filter with id: {} and input streams: {} and this:{}", filterId, filterConfiguration.getInputStreams(), this.hashCode());
		FilterAdaptor filterAdaptor = getFilterAdaptor(filterId);
		//Always update the decode stream map because it may be changed
		filterAdaptor.setDecodeStreamMap(decodeStreamMap);
		
		return filterAdaptor.createOrUpdateFilter(filterConfiguration, appAdaptor);
	}
	
	
	public FilterAdaptor getFilterAdaptor(String filterId) {
		return filterList.computeIfAbsent(filterId, key -> new FilterAdaptor(filterId));
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
		boolean result = false;
		FilterAdaptor filterAdaptor = filterList.get(id);
		if(filterAdaptor != null) {
			filterList.remove(id);
			filterAdaptor.close(app);
			result = true;
		}
		return result;
	}

	public boolean hasFilter(String filterId) {
		return filterList.containsKey(filterId);
	}
	
	
}
