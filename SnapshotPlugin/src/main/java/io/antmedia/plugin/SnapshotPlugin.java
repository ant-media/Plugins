package io.antmedia.plugin;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.app.FrameListener;
import io.antmedia.app.SnapshotSettings;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.plugin.api.IStreamListener;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component(value = "plugin." + SnapshotPlugin.PLUGIN_KEY)
public class SnapshotPlugin implements ApplicationContextAware, IStreamListener {

	public static final String PLUGIN_KEY = "snapshot-plugin";
	protected static Logger logger = LoggerFactory.getLogger(SnapshotPlugin.class);

	private Vertx vertx;
	private Map<String, FrameListener> frameListeners = new ConcurrentHashMap<>();
	private ApplicationContext applicationContext;
	private AppSettings appSettings;
	private Gson gson = new Gson();

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		this.vertx = (Vertx) applicationContext.getBean("vertxCore");

		IAntMediaStreamHandler app = getApplication();
		app.addStreamListener(this);
		
		this.appSettings = app.getAppSettings();

		logger.info("Snapshot plugin loaded!");
	}

	public MuxAdaptor getMuxAdaptor(String streamId) {
		IAntMediaStreamHandler application = getApplication();
		MuxAdaptor selectedMuxAdaptor = null;

		if (application != null) {
			selectedMuxAdaptor = application.getMuxAdaptor(streamId);
		}

		return selectedMuxAdaptor;
	}
	
	private FrameListener getOrCreateListener(String streamId) {
		return this.frameListeners.computeIfAbsent(streamId, id -> {
			String snapshotDir = getSnapshotDirectory();
			FrameListener listener = new FrameListener(snapshotDir, vertx);
			
			// Load and set initial settings
			listener.updateSettings(loadSettings());
			
			IAntMediaStreamHandler app = getApplication();
			app.addFrameListener(id, listener);
			
			return listener;
		});
	}

	private String getSnapshotDirectory() {
		try {
			AntMediaApplicationAdapter adapter = (AntMediaApplicationAdapter) getApplication();
			String streamsDir = adapter.getScope().getContext().getResource("streams").getFile().getAbsolutePath();
			return streamsDir + File.separator + "snapshots";
		} catch (Exception e) {
			logger.error("Could not get streams directory, falling back to /streams/snapshots/", e);
			return "/streams/snapshots/";
		}
	}

	// Returns new copy of settings every time, cache if accessing to often
	private SnapshotSettings loadSettings() {
		Object settingsString = appSettings.getCustomSetting("plugin." + PLUGIN_KEY);
		if (settingsString == null) {
			logger.info("Using default settings for Snapshot Plugin because no settings found in AppSettings");
			return new SnapshotSettings();
		} else {
			try {
				return gson.fromJson(settingsString.toString(), SnapshotSettings.class);
			} catch (Exception e) {
				logger.error("Invalid Snapshot Plugin settings, using default settings", e);
				return new SnapshotSettings();
			}
		}
	}

	public boolean takeSnapshot(String streamId) {
		if (getMuxAdaptor(streamId) == null) {
			logger.warn("Stream {} is not active, cannot take snapshot", streamId);
			return false;
		}

		FrameListener listener = getOrCreateListener(streamId);
		// Reload settings to ensure we have the latest config
		listener.updateSettings(loadSettings());
		listener.scheduleSnapshot();
		return true;
	}
	
	public boolean startTimelapse(String streamId) {
		if (getMuxAdaptor(streamId) == null) {
			logger.warn("Stream {} is not active, cannot start timelapse", streamId);
			return false;
		}
		
		FrameListener listener = getOrCreateListener(streamId);
		SnapshotSettings settings = loadSettings();
		settings.setAutoSnapshotEnabled(true); // Force enable for explicit start command
		listener.updateSettings(settings);
		return true;
	}
	
	public boolean stopTimelapse(String streamId) {
		FrameListener listener = this.frameListeners.get(streamId);
		if (listener != null) {
			// We modify the current settings to disable auto snapshot
			// We do not reload from global because we want to stop *this* instance regardless of global config
			SnapshotSettings current = listener.getSettings();
			if (current == null) current = new SnapshotSettings();
			current.setAutoSnapshotEnabled(false);
			listener.updateSettings(current);
			return true;
		}
		return false;
	}

	public boolean getTimelapseStatus(String streamId) {
		FrameListener listener = this.frameListeners.get(streamId);
		if (listener != null) {
			SnapshotSettings current = listener.getSettings();
			if (current != null) {
				return current.isAutoSnapshotEnabled();
			}
		}
		return false;
	}

	public IAntMediaStreamHandler getApplication() {
		return (IAntMediaStreamHandler) this.applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}

	@Override
	public void streamStarted(String streamId) {
		logger.info("*************** Stream Started: {} ***************", streamId);
		SnapshotSettings settings = loadSettings();
		if (settings.isAutoSnapshotEnabled()) {
			startTimelapse(streamId);
		}
	}

	@Override
	public void streamFinished(String streamId) {
		logger.info("*************** Stream Finished: {} ***************", streamId);
		this.frameListeners.remove(streamId);
	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
		logger.info("*************** Stream Id:{} joined the room:{} ***************", streamId, roomId);
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
		logger.info("*************** Stream Id:{} left the room:{} ***************", streamId, roomId);
	}

}
