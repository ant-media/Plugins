package io.antmedia.plugin;

import com.google.gson.Gson;
import io.antmedia.app.RTCPStatsPluginSettings;
import io.antmedia.datastore.db.types.Broadcast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.app.RTCPStatsPacketListener;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.plugin.api.IStreamListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component(value="plugin.rtcp-stats")
public class RTCPStatsPlugin implements ApplicationContextAware, IStreamListener {

	private static Logger logger = LoggerFactory.getLogger(RTCPStatsPlugin.class);

	public final static String RTCP_SENDER_REPORT_EVENT = "rtcpSr";

	private ApplicationContext applicationContext;
	private RTCPStatsPluginSettings settings;

	private final Map<String, RTCPStatsPacketListener> packetListeners = new ConcurrentHashMap<>();

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;

		IAntMediaStreamHandler app = getApplication();
		app.addStreamListener(this);

		refreshSettings();
		logger.info("Initialized!");
	}

	@Override
	public void streamStarted(String streamId) {
		logger.info("Stream started: {}", streamId);

		String streamUrl = getStreamUrl(streamId);
        if (streamUrl == null || (!streamUrl.startsWith("rtsp://") && !streamUrl.startsWith("rtp://"))) {
            return;
        }

		AntMediaApplicationAdapter app = getApplication();
		if (!app.isDataChannelEnabled()) {
			return;
		}

		RTCPStatsPacketListener packetListener = new RTCPStatsPacketListener(streamId, app, settings);
		if (app.addPacketListener(streamId, packetListener)) {
			packetListeners.put(streamId, packetListener);
			logger.info("Registered rtcp stats listener for stream: {}", streamId);
		} else {
			logger.warn("Failed to register rtcp stats listener for stream: {}", streamId);
		}
    }

	@Override
	public void streamFinished(String streamId) {
		RTCPStatsPacketListener packetListener = packetListeners.remove(streamId);
        if (packetListener == null) {
            return;
        }

        IAntMediaStreamHandler app = getApplication();
        app.removePacketListener(streamId, packetListener);
        logger.info("Removed rtcp stats listener for stream: {}", streamId);
    }

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
	}

	private AntMediaApplicationAdapter getApplication() {
		return (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}

	private String getStreamUrl(String streamId) {
		AntMediaApplicationAdapter antApp = getApplication();
		Broadcast broadcast = antApp.getDataStore().get(streamId);
		return broadcast != null ? broadcast.getStreamUrl() : null;
	}
	
	private void refreshSettings() {
		try {
			Object customSetting = getApplication().getAppSettings().getCustomSetting("plugin.rtcp-stats");
			if (customSetting != null) {
				Gson gson = new Gson();
				this.settings = gson.fromJson(customSetting.toString(), RTCPStatsPluginSettings.class);
			}
		} catch (Exception e) {
			logger.error("Error refreshing plugin settings, using defaults", e);
		} finally {
			if (this.settings == null) {
				this.settings = new RTCPStatsPluginSettings();
			}
		}
	}
}