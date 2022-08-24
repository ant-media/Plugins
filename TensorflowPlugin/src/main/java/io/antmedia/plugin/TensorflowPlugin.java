package io.antmedia.plugin;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.plugin.tensorflow.detection.TensorFlowDetector;
import io.vertx.core.Vertx;

@Component(value="plugin.tensorflow")
public class TensorflowPlugin implements ApplicationContextAware, IStreamListener{
	protected static Logger logger = LoggerFactory.getLogger(TensorflowPlugin.class);
	
	private Vertx vertx;
	private ApplicationContext applicationContext;

	private TensorflowFrameListener frameListener;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");
		
		AntMediaApplicationAdapter app = getApplication();
		app.addStreamListener(this);
	}
		
	public boolean startDetection(String streamId, boolean realTime) 
	{
		AntMediaApplicationAdapter app = getApplication();

		TensorFlowDetector tensorFlowDetector = null;
		try {
			tensorFlowDetector = new TensorFlowDetector("lib/detection/", vertx);

			frameListener = new TensorflowFrameListener(vertx, tensorFlowDetector, app, realTime);
			app.addFrameListener(streamId, frameListener);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return false;
	}

	public boolean stopDetection(String streamId) {
		AntMediaApplicationAdapter app = getApplication();
		boolean result = false;
		if(frameListener != null) {
			//TODO: Refactor we need to return a value in the interface method
			app.removeFrameListener(streamId, frameListener);
			result = true;
		}
		return result;
	}
	
	public AntMediaApplicationAdapter getApplication() {
		return (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}

	@Override
	public void streamStarted(String streamId) {
	}

	@Override
	public void streamFinished(String streamId) {
		stopDetection(streamId);
	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
	}

	

}
