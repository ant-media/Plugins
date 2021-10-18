package io.antmedia.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.IApplicationAdaptorFactory;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.datastore.db.types.ConferenceRoom;
import io.antmedia.filter.FilterConfiguration;
import io.antmedia.filter.FilterUtils;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.websocket.WebSocketConstants;

@Component(value="filters.mcu")
public class MCUManager implements ApplicationContextAware, IStreamListener{
	public static final String BEAN_NAME = "filters.mcu";
	
	private Map<String, Boolean> conferenceRooms = new ConcurrentHashMap<String, Boolean>(); //room to change availibility map
	public static final long CONFERENCE_INFO_POLL_PERIOD_MS = 5000;
	private long roomUpdateTimer = -1L;
	private ApplicationContext applicationContext;
	private AntMediaApplicationAdapter appAdaptor;
	private FiltersManager filtersManager;
	private String pluginType = FilterConfiguration.ASYNCHRONOUS;
	private static Logger logger = LoggerFactory.getLogger(MCUManager.class);

	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		AntMediaApplicationAdapter app = getApplication();
		app.addStreamListener(this);
		
		roomUpdateTimer = getApplication().getVertx().setPeriodic(CONFERENCE_INFO_POLL_PERIOD_MS , t->{
			for (String roomId : conferenceRooms.keySet()) {
				// update if room has change (joined or left participants)
				if(conferenceRooms.get(roomId)) {
					updateRoomFilter(roomId);
					conferenceRooms.put(roomId, false);
				}
			}
		});
	}
	
	public AntMediaApplicationAdapter getApplication() {
		if(appAdaptor == null) {
			appAdaptor = ((IApplicationAdaptorFactory) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME)).getAppAdaptor();
		}
		return appAdaptor;
	}
	
	public FiltersManager getFiltersManager() {
		if(filtersManager == null) {
			filtersManager = (FiltersManager) applicationContext.getBean(FiltersManager.BEAN_NAME);
		}
		return filtersManager;
	}

	private void updateRoomFilter(String roomId) {
		DataStore datastore = getApplication().getDataStore();
		ConferenceRoom room = datastore.getConferenceRoom(roomId);
		if(room == null) {
			conferenceRooms.remove(roomId);
			getFiltersManager().delete(roomId, getApplication());
		}
		else {
			List<String> streams = new ArrayList();
			streams.addAll(room.getRoomStreamList());

			for (String streamId : room.getRoomStreamList()) {
				Broadcast broadcast = datastore.get(streamId);
				if(broadcast == null || !broadcast.getStatus().equals(AntMediaApplicationAdapter.BROADCAST_STATUS_BROADCASTING)) {
					streams.remove(streamId);
				}
			}

			if(streams.size() > 0) {
				FilterConfiguration filterConfiguration = new FilterConfiguration();
				filterConfiguration.setFilterId(roomId);
				filterConfiguration.setInputStreams(streams);
				List<String> outputStreams = new ArrayList<>();
				outputStreams.add(roomId);
				filterConfiguration.setOutputStreams(outputStreams);
				filterConfiguration.setVideoFilter(FilterUtils.createVideoFilter(streams.size()));
				filterConfiguration.setAudioFilter(FilterUtils.createAudioFilter(streams.size()));
				filterConfiguration.setVideoEnabled(!room.getMode().equals(WebSocketConstants.AMCU));
				filterConfiguration.setAudioEnabled(true);
				filterConfiguration.setType(pluginType);

				getFiltersManager().createFilter(filterConfiguration, getApplication());
			}
			else if(!room.isZombi()) {
				conferenceRooms.remove(roomId);
				getFiltersManager().delete(roomId, getApplication());
			}
		}
	}

	private void roomHasChange(String roomId) {
		DataStore datastore = getApplication().getDataStore();
		ConferenceRoom room = datastore.getConferenceRoom(roomId);	
		if(room == null && conferenceRooms.containsKey(roomId)) {
			//all participants are left the room and room has been deleted
			conferenceRooms.put(roomId, true);
		} 
		else if((room != null) && (room.getMode().equals(WebSocketConstants.MCU) || room.getMode().equals(WebSocketConstants.AMCU))) {
			if(!conferenceRooms.containsKey(roomId) && getApplication().getAppSettings().getEncoderSettings().isEmpty()) {
				//first participant for the room but no adaptive settings
				logger.warn("You should add at least one adaptivesettings to use MCU");
			}
			else {
				conferenceRooms.put(roomId, true);
			}
		}
	}
	
	@Override
	public void joinedTheRoom(String roomId, String streamId) {
		roomHasChange(roomId);
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
		roomHasChange(roomId);
	}
	
	@Override
	public void streamStarted(String streamId) {
	}

	@Override
	public void streamFinished(String streamId) {
	}
	
	public void setPluginType(String type) {
		this.pluginType = type;
	}
}
