package io.antmedia.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.filter.FilterAdaptor;
import io.antmedia.filter.utils.FilterConfiguration;

@Component(value="filters.manager")
public class FiltersManager {

	public static final String BEAN_NAME = "filters.manager";
	private Map<String, FilterAdaptor> filterList = new LinkedHashMap<>();

	/**
	 * Creates or updates the filter 
	 * @param filterConfiguration
	 * @param appAdaptor
	 */
	public boolean createFilter(FilterConfiguration filterConfiguration, AntMediaApplicationAdapter appAdaptor) {
		boolean decodeStreams = appAdaptor.getAppSettings().getEncoderSettings().isEmpty();
		int frameRate = appAdaptor.getAppSettings().getWebRTCFrameRate();

		String filter = filterConfiguration.getFilterId();
		FilterAdaptor filterAdaptor = filterList.get(filter);
		if(filterAdaptor == null) {
			filterAdaptor = new FilterAdaptor(decodeStreams, frameRate);
			filterList.put(filter, filterAdaptor);
		}

		return filterAdaptor.createFilter(filterConfiguration, appAdaptor);
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
