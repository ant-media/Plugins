package io.antmedia.plugin;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.app.TimecodeExtractor;
import io.antmedia.plugin.api.IFrameListener;
import io.antmedia.plugin.api.IStreamListener;
import io.vertx.core.Vertx;

@Component(value=TimecodeExtractorPlugin.COMPONENT_NAME)
public class TimecodeExtractorPlugin implements ApplicationContextAware, IStreamListener{

	public static final String COMPONENT_NAME = "plugin.TimecodeExtractorPlugin";
	
	public static final String BEAN_NAME = "web.handler";
	protected static Logger logger = LoggerFactory.getLogger(TimecodeExtractorPlugin.class);

	private Vertx vertx;
	private Map<String, TimecodeExtractor> timeCodeExtactorMap = new ConcurrentHashMap<>();

	private ApplicationContext applicationContext;


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");

		AntMediaApplicationAdapter app = getApplication();
		app.addStreamListener(this);
	}


	public AntMediaApplicationAdapter getApplication() {
		return (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}

	public IFrameListener createCustomBroadcast(String streamId) {
		AntMediaApplicationAdapter app = getApplication();
		return app.createCustomBroadcast(streamId);
	}

	@Override
	public void streamStarted(String streamId) {

		AntMediaApplicationAdapter app = getApplication();
		TimecodeExtractor timeCodeExtractor = new TimecodeExtractor(streamId, getApplication());
		boolean result = app.addPacketListener(streamId, timeCodeExtractor);
		if (result) 
		{
			logger.info("Packet listener is added to stream:{} for TimeCodeExraction", streamId);
			timeCodeExtactorMap.put(streamId,timeCodeExtractor);
		}
		else {
			logger.warn("Packet listener cannot be added to stream:{} for TimeCodeExraction", streamId);
		}


	}

	@Override
	public void streamFinished(String streamId) {
		TimecodeExtractor timecodeExtractor = timeCodeExtactorMap.remove(streamId);
		if (timecodeExtractor != null) 
		{
			//mux adaptor can already be removed at this stage, just make sure to call removing the listener
			getApplication().removePacketListener(streamId, timecodeExtractor);
		}

	}
	
	public Queue<String> getTimeCodes(String streamId) {
		TimecodeExtractor timecodeExtractor = timeCodeExtactorMap.get(streamId);
		if (timecodeExtractor != null) {
			return timecodeExtractor.getTimeCodeQueue();
		}
		//return empty queue
		return new LinkedList<>();
	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
		//No need to implement
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
		//No need to implement
	}

}
