package io.antmedia.plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.filter.utils.MCUFilterTextGenerator;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.websocket.WebSocketConstants;

@Component(value="filters.mcu")
public class MCUManager implements ApplicationContextAware, IStreamListener{
	public static final String BEAN_NAME = "filters.mcu";

	private Queue<String> conferenceRoomsUpdated = new ConcurrentLinkedQueue<>(); //room to change availibility map
	public static final long CONFERENCE_INFO_POLL_PERIOD_MS = 5000;
	public static final String MERGED_SUFFIX = "Merged";
	private long roomUpdateTimer = -1L;
	private ApplicationContext applicationContext;
	private AntMediaApplicationAdapter appAdaptor;
	private FiltersManager filtersManager;
	private String pluginType = FilterConfiguration.ASYNCHRONOUS;
	private static Logger logger = LoggerFactory.getLogger(MCUManager.class);
	private Queue<String> roomsHasCustomFilters = new ConcurrentLinkedQueue<>();
	private Queue<String> customRooms = new ConcurrentLinkedQueue<>();
	


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		AntMediaApplicationAdapter app = getApplication();
		app.addStreamListener(this);

		roomUpdateTimer = getApplication().getVertx().setPeriodic(CONFERENCE_INFO_POLL_PERIOD_MS , t->{

			Iterator<String> iterator = conferenceRoomsUpdated.iterator(); 
			while (iterator.hasNext()) {
				updateRoomFilter(iterator.next());
				iterator.remove();
			}
		});
	}

	public void customFilterAdded(String roomId) {
		if (!roomsHasCustomFilters.contains(roomId)) 
		{
			roomsHasCustomFilters.add(roomId);
		}
	}

	public boolean customFilterRemoved(String roomId) {
		roomsHasCustomFilters.remove(roomId);
		return updateRoomFilter(roomId);
	}

	public AntMediaApplicationAdapter getApplication() {
		if(appAdaptor == null) {
			appAdaptor = (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
		}
		return appAdaptor;
	}

	public FiltersManager getFiltersManager() {
		if(filtersManager == null) {
			filtersManager = (FiltersManager) applicationContext.getBean(FiltersManager.BEAN_NAME);
		}
		return filtersManager;
	}

	public synchronized boolean updateRoomFilter(String roomId) 
	{
		DataStore datastore = getApplication().getDataStore();
		Broadcast room = datastore.get(roomId);
		boolean result = false;
		if(room == null) 
		{
			result = getFiltersManager().delete(roomId, getApplication());
		}
		else if (!roomsHasCustomFilters.contains(roomId)) 
		{
			//Update room filter if there is no custom filter
			try {
				List<String> streams = new ArrayList<>();
				streams.addAll(room.getSubTrackStreamIds());

				for (String streamId : room.getSubTrackStreamIds()) {
					Broadcast broadcast = datastore.get(streamId);
					if(broadcast == null || !broadcast.getStatus().equals(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING)) {
						streams.remove(streamId);
					}
				}
				//
				if (!streams.isEmpty()) 
				{
					FilterConfiguration filterConfiguration = new FilterConfiguration();
					filterConfiguration.setFilterId(roomId);
					filterConfiguration.setInputStreams(streams);
					List<String> outputStreams = new ArrayList<>();
					outputStreams.add(roomId+MERGED_SUFFIX);
					filterConfiguration.setOutputStreams(outputStreams);
					filterConfiguration.setVideoFilter(MCUFilterTextGenerator.createVideoFilter(streams.size()));
					filterConfiguration.setAudioFilter(MCUFilterTextGenerator.createAudioFilter(streams.size()));
					filterConfiguration.setVideoEnabled(!room.getConferenceMode().equals(WebSocketConstants.AMCU));
					filterConfiguration.setAudioEnabled(true);
					filterConfiguration.setType(pluginType);
	
					result = getFiltersManager().createFilter(filterConfiguration, getApplication()).isSuccess();
				}
				else 
				{
					result = getFiltersManager().delete(roomId, getApplication());
				}
			}
			catch (Exception e) {
				//handle any unexpected exception to not have any problem in outer loop
				logger.error(ExceptionUtils.getStackTrace(e));
			}
		}
		else if(!room.isZombi()) {
			/*
			 * This is to delete merged broadcast in case of non-zombi room and custom filter
			 * delete if the all broadcasts are in non broadcasting status
			 */
			boolean deleteRoom = true;
			for (String streamId : room.getSubTrackStreamIds()) {
				Broadcast broadcast = datastore.get(streamId);
				if(broadcast != null && broadcast.getStatus().equals(IAntMediaStreamHandler.BROADCAST_STATUS_BROADCASTING)) {
					deleteRoom = false;
				}
			} 
			if(deleteRoom) {
				result = getFiltersManager().delete(roomId, getApplication());
			}	
		}

		return result;
	}

	private void roomHasChange(String roomId) {
		DataStore datastore = getApplication().getDataStore();
		Broadcast room = datastore.get(roomId);	
		if ((room == null || room.getConferenceMode().equals(WebSocketConstants.MCU)
				|| room.getConferenceMode().equals(WebSocketConstants.AMCU)
				|| customRooms.contains(roomId)) 
				&& !conferenceRoomsUpdated.contains(roomId)) 
		{
			conferenceRoomsUpdated.add(roomId); 
		}
	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
		 triggerUpdate(roomId, false);
		
	}
	
	public void triggerUpdate(String roomId, boolean immediately) {
		if(immediately) {
			getApplication().getVertx().executeBlocking( l-> {
				updateRoomFilter(roomId);
			}, null);
		}
		else {
			roomHasChange(roomId);
			//call again after the period time to not encounter any problem
			getApplication().getVertx().setTimer(CONFERENCE_INFO_POLL_PERIOD_MS, id -> {
				roomHasChange(roomId);
			});
		}
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
		//since this is left event roomFilter should be available
		if(getFiltersManager().hasFilter(roomId)) {
			triggerUpdate(roomId, true);
		}

	}

	@Override
	public void streamStarted(String streamId) {
		//No need to implement for MCU
	}

	@Override
	public void streamFinished(String streamId) {
		//No need to implement for MCU
	}

	public void setPluginType(String type) {
		this.pluginType = type;
	}

	public void addCustomRoom(String roomId) {
		customRooms.add(roomId);	
		triggerUpdate(roomId, false);
	}

	public void removeCustomRoom(String roomId) {
		customRooms.remove(roomId);	
		filtersManager.delete(roomId, appAdaptor);
	}
}
