package io.antmedia.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.EncoderSettings;
import io.antmedia.filter.FilterAdaptor;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.rest.model.Result;

@Component(value="filters.manager")
public class FiltersManager {

	public static final String BEAN_NAME = "filters.manager";
	private Map<String, FilterAdaptor> filterList = new LinkedHashMap<>();

	public void setFilterList(Map<String, FilterAdaptor> filterList) {
		this.filterList = filterList;
	}

	public Map<String, FilterAdaptor> getFilterAdaptorList() {
		return filterList;
	}

	/**
	 * Creates or updates the filter 
	 * @param filterConfiguration
	 * @param appAdaptor
	 */
	public Result createFilter(FilterConfiguration filterConfiguration, AntMediaApplicationAdapter appAdaptor) 
	{
		List<EncoderSettings> encoderSettings = appAdaptor.getAppSettings().getEncoderSettings();
		final boolean decodeStreams;
		if (encoderSettings == null || encoderSettings.isEmpty()) {
			decodeStreams = true;
		}
		else {
			decodeStreams = false;
		}
		String filterId = filterConfiguration.getFilterId();
		
		if (filterId == null || filterId.isBlank()) {
			filterId = RandomStringUtils.randomAlphanumeric(12);
			filterConfiguration.setFilterId(filterId);
		}
		
		FilterAdaptor filterAdaptor = filterList.computeIfAbsent(filterId, key-> new FilterAdaptor(decodeStreams));
		

		return filterAdaptor.startFilterProcess(filterConfiguration, appAdaptor);
	}

	/**
	 * Returns the active  filter configurations in the app
	 * @return
	 */
	public List<FilterConfiguration> getFilterConfigurations() {
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
	public Result delete(String id, AntMediaApplicationAdapter app) {
		Result result = new Result(false);
		FilterAdaptor filterAdaptor = filterList.get(id);
		if(filterAdaptor != null) {
			filterList.remove(id);
			result.setSuccess(filterAdaptor.close(app));
			return result;
		}
		return result;
	}

	public boolean hasFilter(String filterId) {
		return filterList.containsKey(filterId);
	}
}
