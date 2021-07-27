package io.antmedia.plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bytedeco.javacpp.Pointer;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.IApplicationAdaptorFactory;
import io.antmedia.IFiltersManager;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.datastore.db.types.ConferenceRoom;
import io.antmedia.filter.FilterAdaptor;
import io.antmedia.filter.FilterConfiguration;
import io.antmedia.filter.FilterUtils;
import io.antmedia.filter.MCUFilterConfiguration;

@Component(value="filters.manager")
public class FiltersManager implements IFiltersManager{
	public static final long CONFERENCE_INFO_POLL_PERIOD_MS = 5000;

	public static final String BEAN_NAME = "filters.manager";
	private Map<String, FilterAdaptor> filterList = new LinkedHashMap<String, FilterAdaptor>();

	private List<String> conferenceRooms = new ArrayList<String>();

	private long roomUpdateTimer = -1L;

	public void createFilter(FilterConfiguration filterConfiguration, AntMediaApplicationAdapter app) {
		String filter = filterConfiguration.getFilterId();
		FilterAdaptor filterAdaptor = filterList.get(filter);
		if(filterAdaptor == null) {
			filterAdaptor = new FilterAdaptor();
			filterList.put(filter, filterAdaptor);
		}

		filterAdaptor.createFilter(filterConfiguration, app);
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

	@Override
	public void conference(String roomId, ApplicationContext appContext) {
		if(!conferenceRooms.contains(roomId)) {
			conferenceRooms.add(roomId);
		}
		
		if(roomUpdateTimer == -1) {
			AntMediaApplicationAdapter adaptor = ((IApplicationAdaptorFactory) appContext.getBean(AntMediaApplicationAdapter.BEAN_NAME)).getAppAdaptor();

			roomUpdateTimer = adaptor.getVertx().setPeriodic(CONFERENCE_INFO_POLL_PERIOD_MS , t->{
				
				for (String room : conferenceRooms) {
					updateRoomFilter(room, adaptor);
				}
			});
		}
	}

	private void updateRoomFilter(String roomId, AntMediaApplicationAdapter app) {
		DataStore datastore = app.getDataStore();
		ConferenceRoom room = datastore.getConferenceRoom(roomId);
		List<String> streams = new ArrayList();
		streams.addAll(room.getRoomStreamList());
		
		for (String streamId : room.getRoomStreamList()) {
			Broadcast broadcast = datastore.get(streamId);
			if(broadcast == null || !broadcast.getStatus().equals(AntMediaApplicationAdapter.BROADCAST_STATUS_BROADCASTING)) {
				streams.remove(streamId);
			}
		}
		
		if(streams.size()>1) {
			MCUFilterConfiguration filterConfiguration = new MCUFilterConfiguration();
			filterConfiguration.setFilterId(roomId);
			filterConfiguration.setInputStreams(streams);
			List<String> outputStreams = new ArrayList<>();
			outputStreams.add(roomId);
			filterConfiguration.setOutputStreams(outputStreams);
			filterConfiguration.setVideoFilter(FilterUtils.createVideoFilter(streams.size()));
			filterConfiguration.setAudioFilter(FilterUtils.createAudioFilter(streams.size()));
			
			createFilter(filterConfiguration, app);
		}
	}

	
}
