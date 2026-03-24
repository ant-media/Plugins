package io.antmedia.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResultCallback {

	private static final Logger logger = LoggerFactory.getLogger(ResultCallback.class);

	private final PluginResultDatabase db;
	private final String tableName;

	public ResultCallback(String tableName) {
		this.tableName = tableName;
		this.db = PluginResultDatabase.getInstance();
		this.db.ensureTable(tableName);
	}

	public void onResult(String streamId, String jsonData) {
		logger.info("Python result [{}] for stream {}: {}", tableName, streamId, jsonData);
		db.insertResult(tableName, streamId, jsonData);
	}
}
