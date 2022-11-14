package io.antmedia.plugin;

import java.util.List;

import org.bytedeco.zixi.client.ZIXI_LOG_FUNC;
import org.bytedeco.zixi.global.feeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.app.SampleFrameListener;
import io.antmedia.app.SamplePacketListener;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.IStreamListener;
import io.vertx.core.Vertx;
import static org.bytedeco.zixi.global.client.*;
import static org.bytedeco.zixi.global.feeder.*;

@Component(value="io.antmedia.zixi.receiver")
public class ZixiClient implements ApplicationContextAware, IStreamListener{

	protected static Logger logger = LoggerFactory.getLogger(ZixiClient.class);
	
	private Vertx vertx;
	private SampleFrameListener frameListener = new SampleFrameListener();
	private SamplePacketListener packetListener = new SamplePacketListener();
	private ApplicationContext applicationContext;
	
	private int logLevel = 0;
	

	private static ZIXI_LOG_FUNC loggerFunction = new ZIXI_LOG_FUNC() 
	{
		@Override
		public void call(org.bytedeco.javacpp.Pointer userData, int level, org.bytedeco.javacpp.BytePointer msg) {
			logger.info("zixi log: {}", msg.getString());
			
		}
	};

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");
		
		AntMediaApplicationAdapter app = getApplication();
		app.addStreamListener(this);
		
		
		
	}
	
	public void initZixi() {
		int result = zixi_init();
		if (result == feeder.ZIXI_ERROR_OK) {
			logger.info("Zixi is initialized successfully");
		}
		else {
			logger.warn("Zixi initialization has failed");
		}
	}
	
	public void start() {
		
		zixi_client_configure_logging(logLevel, loggerFunction, null);
		
		
		
		zixi_init_connection_handle(null);
		
		zixi_configure_id(null, (String)null, (String)null);
		
		zixi_configure_error_correction(null, ZIXI_LATENCY_INCREASING, ZIXI_LATENCY_DYNAMIC, ZIXI_FAILOVER_MODE_CONTENT, AF_CHAOS, AF_CCITT, false, false, AF_APPLETALK, false);
	
		zixi_connect_url(null, (String)null, false, null, false, false, false);
		
	//	zixi_accept(null, 0, null, false, ZIXI_PROTOCOL_HTTP)
		
		zixi_read(null, (byte[])null, _SS_ALIGNSIZE, null, (boolean[])null, null, false, (int[])null);
		
		zixi_disconnect(null);
		
		zixi_delete_connection_handle(null);
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
	
	public void register(String streamId) {
		AntMediaApplicationAdapter app = getApplication();
		app.addFrameListener(streamId, frameListener);		
		app.addPacketListener(streamId, packetListener);
	}
	
	public AntMediaApplicationAdapter getApplication() {
		return (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}
	
	public IFrameListener createCustomBroadcast(String streamId) {
		AntMediaApplicationAdapter app = getApplication();
		return app.createCustomBroadcast(streamId);
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

	

}
