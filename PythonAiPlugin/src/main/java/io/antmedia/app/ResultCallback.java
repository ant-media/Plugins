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

	public void onResult(String appName, String streamId, String jsonData) {
		String app = appName != null ? appName : "";
		logger.info("Python result [{}] app={} stream {}: {}", tableName, app, streamId, jsonData);
		db.insertResult(tableName, app, streamId, jsonData);
	}
}
