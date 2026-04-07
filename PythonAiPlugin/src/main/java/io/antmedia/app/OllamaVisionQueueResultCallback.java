package io.antmedia.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OllamaVisionQueueResultCallback extends ResultCallback {

	private static final Logger logger = LoggerFactory.getLogger(OllamaVisionQueueResultCallback.class);

	public static final String TABLE_NAME = "ollama_vision_queue_results";

	public OllamaVisionQueueResultCallback() {
		super(TABLE_NAME);
	}

	@Override
	public void onResult(String streamId, String jsonData) {
		super.onResult(streamId, jsonData);
		try {
			onOllamaVisionQueueResult(streamId, jsonData);

		} catch (Exception e) {
			logger.error("onOllamaVisionQueueResult failed for stream {}", streamId, e);
		}
	}

	protected void onOllamaVisionQueueResult(String streamId, String jsonData) {
		
	}
}
