package io.antmedia.plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.app.SEItoID3Converter;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.IStreamListener;
import io.vertx.core.Vertx;

@Component(value="plugin.id3converter")
public class ID3Converter implements ApplicationContextAware, IStreamListener{

	public static final String BEAN_NAME = "web.handler";
	protected static Logger logger = LoggerFactory.getLogger(ID3Converter.class);
	
	private Vertx vertx;
	private ApplicationContext applicationContext;
	
	private Map<String, SEItoID3Converter> sei2ID3ConverterMap = new ConcurrentHashMap<>();


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");
		

		
		IAntMediaStreamHandler app = getApplication();
		
		logger.info("ID3Converter is initializing for {}", ((AntMediaApplicationAdapter)app).getName());

		app.addStreamListener(this);
	}
		
	public MuxAdaptor getMuxAdaptor(String streamId) 
	{
		IAntMediaStreamHandler application = getApplication();
		MuxAdaptor selectedMuxAdaptor = null;

		if(application != null)
		{
			selectedMuxAdaptor = application.getMuxAdaptor(streamId);
		}

		return selectedMuxAdaptor;
	}

	
	public IAntMediaStreamHandler getApplication() {
		return (IAntMediaStreamHandler) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}
	
	public IFrameListener createCustomBroadcast(String streamId) {
		IAntMediaStreamHandler app = getApplication();
		return app.createCustomBroadcast(streamId);
	}


	

	@Override
	public void streamStarted(String streamId) {
		IAntMediaStreamHandler app = getApplication();
		SEItoID3Converter seiToID3Converter = new SEItoID3Converter(streamId, getApplication());
		boolean result = app.addPacketListener(streamId, seiToID3Converter);
		if (result) 
		{
			logger.info("Packet listener is added to stream:{} ", streamId);
			sei2ID3ConverterMap.put(streamId, seiToID3Converter);
		}
		else {
			logger.warn("Packet listener cannot be added to stream:{}", streamId);
		}
		
	}

	@Override
	public void streamFinished(String streamId) {
		SEItoID3Converter timecodeExtractor = sei2ID3ConverterMap.remove(streamId);
		if (timecodeExtractor != null) 
		{
			//mux adaptor can already be removed at this stage, just make sure to call removing the listener
			getApplication().removePacketListener(streamId, timecodeExtractor);
		}
	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
		//No need to do anything
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
		//No need to do anything
	}

}
