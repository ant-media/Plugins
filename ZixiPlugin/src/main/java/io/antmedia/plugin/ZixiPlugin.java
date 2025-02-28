package io.antmedia.plugin;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import jakarta.ws.rs.core.Response;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.StreamIdValidator;
import io.antmedia.app.SampleFrameListener;
import io.antmedia.app.SamplePacketListener;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.rest.RestServiceBase;
import io.antmedia.rest.model.Result;
import io.antmedia.zixi.ZixiClient;
import io.antmedia.zixi.ZixiFeeder;
import io.vertx.core.Vertx;

@Component(value="io.antmedia.zixi.ZixiPlugin")
public class ZixiPlugin implements ApplicationContextAware, IStreamListener{

	protected static Logger logger = LoggerFactory.getLogger(ZixiPlugin.class);

	private Vertx vertx;
	private SampleFrameListener frameListener = new SampleFrameListener();
	private SamplePacketListener packetListener = new SamplePacketListener();
	private ApplicationContext applicationContext;

	private AntMediaApplicationAdapter app;

	private final Map<String, ZixiClient> zixiClientMap = new ConcurrentHashMap<>();
	private final Map<String, Queue<ZixiFeeder>> zixiFeederMap = new ConcurrentHashMap<>();
	public static final String PUBLISH_TYPE_ZIXI_CLIENT = "ZixiClient";


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");
		app = getApplication();
	}

	public void setApp(AntMediaApplicationAdapter app) {
		this.app = app;
	}

	public void setVertx(Vertx vertx) {
		this.vertx = vertx;
	}


	public MuxAdaptor getMuxAdaptor(String streamId) 
	{
		AntMediaApplicationAdapter application = getApplication();
		MuxAdaptor selectedMuxAdaptor = null;

		if(application != null)
		{
			selectedMuxAdaptor = application.getMuxAdaptor(streamId);
		}

		return selectedMuxAdaptor;
	}

	public AntMediaApplicationAdapter getApplication() {
		return (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}

	public String getStats() {
		return frameListener.getStats() + "\t" + packetListener.getStats();
	}

	@Override
	public void streamStarted(String streamId) {
		//No need to implement
	}

	@Override
	public void streamFinished(String streamId) {
		zixiFeederMap.remove(streamId);
	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
		//No need to implement
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
		//No need to implement
	}

	public Result startClient(String streamId) 
	{
		Broadcast broadcast = app.getDataStore().get(streamId);
		Result result = null;
		//check the broadcast exists
		if (broadcast != null) 
		{
			//check the stream url is in correct format
			if (broadcast.getStreamUrl() != null && broadcast.getStreamUrl().startsWith("zixi")) 
			{
				//check it's already running
				if (zixiClientMap.get(broadcast.getStreamId()) == null) 
				{
					result = startZixiClientInternal(broadcast);
				}
				else 
				{
					result = new Result(false, "There is already a running zixi client for this stream Id:" + streamId);
				}
			}
			else 
			{
				result = new Result(false, "Broadcast with streamId:" + streamId + " has not a correct stream url. Its stream url is " + broadcast.getStreamUrl());
			} 
		}
		else 
		{
			result = new Result(false,"Broadcast with streamId:" + streamId + " is not found");
		}
		return result;
	}


	private Result startZixiClientInternal(Broadcast broadcast) 
	{
		ZixiClient zixiClient = new ZixiClient(vertx, getApplication(), broadcast.getStreamUrl(), broadcast.getStreamId());
		Result result = zixiClient.start();
		if (result.isSuccess()) 
		{
			zixiClientMap.put(broadcast.getStreamId(), zixiClient);
		}
		return result;
	}


	public Result startClient(Broadcast broadcast, boolean start) {
		//Check that there is a stream id 
		//  if there is a stream id, check if it's available

		if (broadcast == null) {
			return new Result(false, "Broadcast object is null");
		}

		//Check that broadcast stream url is in correct format
		if (broadcast.getStreamUrl() == null || !broadcast.getStreamUrl().startsWith("zixi")) {
			return new Result(false, "Stream url is not set correctly please use a zixi url");
		}


		//if stream id is defined, check if it's ok.
		if (broadcast.getStreamId() != null) 
		{
			try 
			{
				broadcast.setStreamId(broadcast.getStreamId().trim());

				if (!broadcast.getStreamId().isEmpty()) 
				{
					// make sure stream id is not set on rest service
					Broadcast broadcastTmp = app.getDataStore().get(broadcast.getStreamId());
					if (broadcastTmp != null) 
					{
						return new Result(false, "Stream id is already being used. Please change stream id or keep it empty");
					}
					else if (!StreamIdValidator.isStreamIdValid(broadcast.getStreamId())) 
					{
						return new Result(false, "Stream id is not valid.");
					}
				}
			}
			catch (Exception e) 
			{
				logger.error(ExceptionUtils.getStackTrace(e));
				return new Result(false, "Setting streamId has generated an exception");
			}
		}


		//save it to the database with correct format
		broadcast.setPublishType(PUBLISH_TYPE_ZIXI_CLIENT);
		broadcast = RestServiceBase.saveBroadcast(broadcast,
				IAntMediaStreamHandler.BROADCAST_STATUS_CREATED, app.getScope().getName(), app.getDataStore(),
				app.getAppSettings().getListenerHookURL(), app.getServerSettings(), 0, false);

		Result result = null;
		if (start) 
		{
			result = startZixiClientInternal(broadcast);
		}
		else 
		{
			result = new Result(true, broadcast.getStreamId(), "ZixiClient is saved to db");
		}
		return result;
	}

	public Result stopClient(String streamId) {
		ZixiClient zixiClient = zixiClientMap.remove(streamId);
		if (zixiClient != null) {
			return zixiClient.stop();
		}
		return new Result(false, "Zixi client is not found with this stream id:" + streamId);
	}

	public Result deleteClient(String streamId) {
		Result result = null;
		Broadcast broadcast = app.getDataStore().get(streamId);
		if (broadcast != null && broadcast.getStreamUrl() != null 
				&& broadcast.getStreamUrl().startsWith("zixi")) 
		{
			//stop client if it's running
			stopClient(streamId);
			boolean deleted = app.getDataStore().delete(streamId);
			result = new Result(deleted, "Broadcast is "+ (deleted ? "" : "not") + " deleted");
		}
		else {
			result = new Result(false, "Broadcast with streamId:" + streamId +" is not a zixi stream");
		}
		return result;
	}


	public synchronized Result startFeeder(String streamId, String zixiEndpointURL) {
		
		MuxAdaptor muxAdaptor = getMuxAdaptor(streamId);
		boolean result = false;
		String message = "";
		//TODO: Add to database for better compatibility
		if (muxAdaptor != null) 
		{
			Queue<ZixiFeeder> zixiFeederQueue = zixiFeederMap.get(streamId);
			boolean alreadyExist = false;
			if (zixiFeederQueue != null) 
			{
				for (ZixiFeeder feeder : zixiFeederQueue) {
					if (feeder.getOutputURL().equals(zixiEndpointURL)) {
						alreadyExist = true;
						break;
					}
				}
			}

			if (!alreadyExist) 
			{
				ZixiFeeder zixiFeeder = new ZixiFeeder(zixiEndpointURL, vertx);
				result = muxAdaptor.addMuxer(zixiFeeder);
				if (result) 
				{
					//if zixi feeder queue is null, it means there is no queue
					if (zixiFeederQueue == null) 
					{
						zixiFeederMap.put(streamId, new ConcurrentLinkedQueue<>());		
					}
					zixiFeederMap.get(streamId).add(zixiFeeder);
				}
				else 
				{
					message = "ZixiFeeder cannot be prepared by MuxerAdaptor";
					muxAdaptor.removeMuxer(zixiFeeder);
				}
			}
			else 
			{
				message = "There is already a zixi feeder for this url:" + zixiEndpointURL;
				logger.warn("There is already a zixi feeder for this url:{} and streamId:{}", zixiEndpointURL, streamId);
			}
		}
		else 
		{
			message = "There is no active stream with streamId: " + streamId;
		}
		return new Result(result, message);
	}

	public synchronized Result stopFeeder(String streamId, String zixiEndpointURL) 
	{
		
		boolean result = false;
		String message = "";
		Queue<ZixiFeeder> queue = zixiFeederMap.get(streamId);
		MuxAdaptor muxAdaptor = getMuxAdaptor(streamId);
		if (queue != null) 
		{
			ZixiFeeder foundZixiFeeder = null;
			for (ZixiFeeder zixiFeeder : queue) 
			{
				if (zixiEndpointURL != null) 
				{
					if (zixiFeeder.getOutputURL().equals(zixiEndpointURL)) 
					{
						foundZixiFeeder = zixiFeeder;
						queue.remove(zixiFeeder);
						if (muxAdaptor != null) 
						{
							result = muxAdaptor.removeMuxer(foundZixiFeeder);
						}
						else {
							//if there is no muxadaptor, it means that streaming has finished
							result = true;
							message = "MuxAdaptor is not found for streamId:" + streamId + ". It's likely the streaming has been stopped";
							logger.info("Mux adaptor is not found for streamId: {}. It's likely the streaming has been stopped.Zixi URL:{} ", streamId, zixiEndpointURL);
						}
						break;
					}
				}
				else 
				{
					//if there is not zixi endpoint url, remove all zixi feeder from the mux adaptor
					//REFACTOR: Code duplication
					queue.remove(zixiFeeder);
					if (muxAdaptor != null) 
					{
						result = muxAdaptor.removeMuxer(zixiFeeder);
					}
					else 
					{
						//if there is no muxadaptor, it means that streaming has finished
						result = true;
						message = "MuxAdaptor is not found for streamId:" + streamId + ". It's likely the streaming has been stopped";
						logger.info("Mux adaptor is not found for streamId: {}. It's likely the streaming has been stopped. Zixi URL is null", streamId);
						break;
					}
				}
			}
							
			if (queue.isEmpty()) {
				zixiFeederMap.remove(streamId);
			}			
		}
		else 
		{
			message = "There is no ZixiFeeder for streamId: " + streamId;
		}
	
		return new Result(result, message);
	}


	public Map<String, ZixiClient> getZixiClientMap() {
		return zixiClientMap;
	}




}
