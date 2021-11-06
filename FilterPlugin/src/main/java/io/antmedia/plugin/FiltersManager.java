package io.antmedia.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.filter.FilterAdaptor;
import io.antmedia.filter.FilterConfiguration;

@Component(value="filters.manager")
public class FiltersManager {

	public static final String BEAN_NAME = "filters.manager";
	private Map<String, FilterAdaptor> filterList = new LinkedHashMap<String, FilterAdaptor>();

	public void createFilter(FilterConfiguration filterConfiguration, AntMediaApplicationAdapter appAdaptor) {
		boolean decodeStreams = appAdaptor.getAppSettings().getEncoderSettings().isEmpty();
		String filter = filterConfiguration.getFilterId();
		FilterAdaptor filterAdaptor = filterList.get(filter);
		if(filterAdaptor == null) {
			filterAdaptor = new FilterAdaptor(decodeStreams);
			filterList.put(filter, filterAdaptor);
		}

		filterAdaptor.createFilter(filterConfiguration, appAdaptor);
	}

	public List<FilterConfiguration> getfilters() {
		List<FilterConfiguration> filters = new ArrayList<FilterConfiguration>();
		for (FilterAdaptor filterAdaptor : filterList.values()) {
			filters.add(filterAdaptor.getCurrentFilterConfiguration());
		}
		return filters;
	}

	public boolean delete(String id, AntMediaApplicationAdapter app) {
		FilterAdaptor filterAdaptor = filterList.get(id);
		if(filterAdaptor != null) {
			filterList.remove(id);
			return filterAdaptor.close(app);
		}
		return false;
	}
}
