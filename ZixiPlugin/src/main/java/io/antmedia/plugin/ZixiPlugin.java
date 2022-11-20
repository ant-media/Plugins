package io.antmedia.plugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.zixi.client.ZIXI_BITRATE_CHANGED_FUNC;
import org.bytedeco.zixi.client.ZIXI_CALLBACKS;
import org.bytedeco.zixi.client.ZIXI_LOG_FUNC;
import org.bytedeco.zixi.client.ZIXI_NEW_STREAM_FUNC;
import org.bytedeco.zixi.client.ZIXI_STATUS_FUNC;
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
import io.antmedia.enterprise.srt.SRTStream;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.rest.RestServiceBase;
import io.antmedia.rest.model.Result;
import io.antmedia.zixi.ZixiClient;
import io.vertx.core.Vertx;
import static org.bytedeco.zixi.global.client.*;
import org.bytedeco.zixi.global.client;

@Component(value="io.antmedia.zixi.client")
public class ZixiPlugin implements ApplicationContextAware, IStreamListener{

	protected static Logger logger = LoggerFactory.getLogger(ZixiPlugin.class);

	private Vertx vertx;
	private SampleFrameListener frameListener = new SampleFrameListener();
	private SamplePacketListener packetListener = new SamplePacketListener();
	private ApplicationContext applicationContext;

	private AntMediaApplicationAdapter app;

	private static final Map<String, ZixiClient> zixiClientMap = new ConcurrentHashMap<>();



	public static final String STREAM_TYPE_ZIXI_CLIENT = "zixiClient";



	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");

		app = getApplication();

	}


	public MuxAdaptor getMuxAdaptor(String streamId) 
	{
		AntMediaApplicationAdapter application = getApplication();
		MuxAdaptor selectedMuxAdaptor = null;

		if(application != null)
		{
			List<MuxAdaptor> muxAdaptors = application.getMuxAdaptors();
			for (MuxAdaptor muxAdaptor : muxAdaptors) 
			{
				if (streamId.equals(muxAdaptor.getStreamId())) 
				{
					selectedMuxAdaptor = muxAdaptor;
					break;
				}
			}
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
		System.out.println("***************");
		System.out.println("Stream Started:"+streamId);
		System.out.println("***************");
	}

	@Override
	public void streamFinished(String streamId) {
		System.out.println("***************");
		System.out.println("Stream Finished:"+streamId);
		System.out.println("***************");
	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {

	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {

	}




	public Result startClient(Broadcast broadcast) {
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
		broadcast.setType(STREAM_TYPE_ZIXI_CLIENT);
		broadcast = RestServiceBase.saveBroadcast(broadcast,
				IAntMediaStreamHandler.BROADCAST_STATUS_CREATED, app.getScope().getName(), app.getDataStore(),
				app.getAppSettings().getListenerHookURL(), app.getServerSettings(), 0);


		ZixiClient zixiClient = new ZixiClient(vertx, getApplication(), broadcast.getStreamUrl());
		zixiClientMap.put(broadcast.getStreamId(), zixiClient);

		return zixiClient.start();
	}




	public Result stopClient(String streamId) {
		ZixiClient zixiClient = zixiClientMap.remove(streamId);
		if (zixiClient != null) {
			return zixiClient.stop();
		}
		return new Result(false, "Zixi client is not found with this stream id:{}", streamId);
	}




	public Response startFeeder(String streamId, String zixiEndpointURL) {
		return null;
	}




	public Response stopFeeder(String streamId, String zixiEndpointURL) {
		return null;
	}



}
