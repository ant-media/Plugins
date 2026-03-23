package io.antmedia.plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.nimbusds.jose.shaded.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.plugin.mutedstreamreplicator.MutedStreamManager;
import io.antmedia.rest.model.Result;

@Component(value="plugin.muted-stream-replicator")
public class MutedStreamReplicatorPlugin implements ApplicationContextAware, IStreamListener{

	public static final String PLUGIN_BEAN_NAME = "plugin.muted-stream-replicator";
	/** @deprecated Use settings instead. Kept for backwards compatibility. */
	@Deprecated
	public static final String DEFAULT_MUTED_SUFFIX = "-muted";

	private static final Logger logger = LoggerFactory.getLogger(MutedStreamReplicatorPlugin.class);
	private static final Gson gson = new Gson();

	private ApplicationContext applicationContext;
	private AntMediaApplicationAdapter appAdapter;
	private final Map<String, MutedStreamManager> mutedStreamManagers = new ConcurrentHashMap<>();

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
		this.appAdapter = (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
		this.appAdapter.addStreamListener(this);
	}

	public MuxAdaptor getMuxAdaptor(String streamId) {
		IAntMediaStreamHandler application = getApplication();
		return application != null ? application.getMuxAdaptor(streamId) : null;
	}
	
	public IAntMediaStreamHandler getApplication() {
		if (appAdapter != null) {
			return appAdapter;
		}
		return applicationContext != null
				? (IAntMediaStreamHandler) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME)
				: null;
	}

	@Override
	public void streamStarted(Broadcast broadcast) {
		if (broadcast == null || StringUtils.isBlank(broadcast.getStreamId())) {
			logger.warn("Ignoring stream start event because broadcast or stream id is missing");
			return;
		}
		
		String streamId = broadcast.getStreamId();
		if (isMutedReplicaStream(streamId)) {
			handleMutedReplicaStarted(streamId);
		}
		else {
			handleSourceStreamStarted(streamId);
		}
	}

	private void handleMutedReplicaStarted(String mutedReplicaStreamId) {
		logger.info("Muted replica stream started: {}", mutedReplicaStreamId);

		String sourceStreamId = getSourceStreamId(mutedReplicaStreamId);
		MuxAdaptor targetAdaptor = getMuxAdaptor(mutedReplicaStreamId);
		MuxAdaptor sourceAdaptor = getMuxAdaptor(sourceStreamId);

		if (!(sourceAdaptor instanceof io.antmedia.enterprise.adaptive.EncoderAdaptor)
				|| !(targetAdaptor instanceof io.antmedia.enterprise.adaptive.EncoderAdaptor)) {
			logger.warn("Cannot wire muted replica stream {} because source adaptor ({}) or target adaptor ({}) is not an EncoderAdaptor",
					mutedReplicaStreamId, getAdaptorName(sourceAdaptor), getAdaptorName(targetAdaptor));
			return;
		}

		MutedStreamManager previousManager = mutedStreamManagers.remove(mutedReplicaStreamId);
		if (previousManager != null) {
			previousManager.stop();
		}

		MutedStreamManager mutedStreamManager = new MutedStreamManager(
				(io.antmedia.enterprise.adaptive.EncoderAdaptor) sourceAdaptor,
				(io.antmedia.enterprise.adaptive.EncoderAdaptor) targetAdaptor);
		if (mutedStreamManager.start()) {
			mutedStreamManagers.put(mutedReplicaStreamId, mutedStreamManager);
		}
		else {
			logger.warn("Muted replica manager could not be started for {}", mutedReplicaStreamId);
		}
	}

	public MutedStreamReplicatorSettings loadSettings() {
		Object raw = getApplication().getAppSettings().getCustomSetting(PLUGIN_BEAN_NAME);
		if (raw != null) {
			try {
				return gson.fromJson(raw.toString(), MutedStreamReplicatorSettings.class);
			} catch (Exception e) {
				logger.error("Invalid MutedStreamReplicator settings, using defaults: {}", e.getMessage());
			}
		}
		return new MutedStreamReplicatorSettings();
	}

	private void handleSourceStreamStarted(String sourceStreamId) {
		MuxAdaptor sourceAdaptor = getMuxAdaptor(sourceStreamId);
		if (sourceAdaptor == null) {
			logger.warn("Cannot start muted replica for {} because source mux adaptor is not available yet", sourceStreamId);
			return;
		}

		String mutedReplicaStreamId = getMutedStreamId(sourceStreamId);
		String replicaEndpointUrl = getReplicaRtmpUrl(mutedReplicaStreamId);

		IAntMediaStreamHandler app = getApplication();
		if (app.getAppSettings().isAcceptOnlyStreamsInDataStore() && app.getDataStore().get(mutedReplicaStreamId) == null) {
			Broadcast b = AntMediaApplicationAdapter.createZombiBroadcast(
					mutedReplicaStreamId, mutedReplicaStreamId,
					IAntMediaStreamHandler.BROADCAST_STATUS_CREATED,
					"MutedStreamReplicatorPlugin",
					"", "{}", "");
			b.setType(AntMediaApplicationAdapter.LIVE_STREAM);

			app.getDataStore().save(b);
		}

		Result result = sourceAdaptor.startEndpointStreaming(replicaEndpointUrl, 0);

		if (result != null && result.isSuccess()) {
			logger.info("Started muted replica stream: {}", mutedReplicaStreamId);
		}
		else {
			String reason = result != null ? result.getMessage() : "No result returned from startEndpointStreaming";
			logger.error("Failed to start muted replica stream {}. Reason: {}", mutedReplicaStreamId, reason);
		}
	}

	private String getMutedStreamId(String sourceStreamId) {
		MutedStreamReplicatorSettings settings = loadSettings();
		return settings.getMutedStreamPrefix() + sourceStreamId + settings.getMutedStreamSuffix();
	}

	private boolean isMutedReplicaStream(String streamId) {
		MutedStreamReplicatorSettings settings = loadSettings();
		String prefix = settings.getMutedStreamPrefix();
		String suffix = settings.getMutedStreamSuffix();
		boolean matchesPrefix = StringUtils.isBlank(prefix) || streamId.startsWith(prefix);
		boolean matchesSuffix = StringUtils.isBlank(suffix) || streamId.endsWith(suffix);
		return matchesPrefix && matchesSuffix && streamId.length() > prefix.length() + suffix.length();
	}

	private String getSourceStreamId(String mutedReplicaStreamId) {
		MutedStreamReplicatorSettings settings = loadSettings();
		String result = mutedReplicaStreamId;
		if (!StringUtils.isBlank(settings.getMutedStreamPrefix())) {
			result = result.substring(settings.getMutedStreamPrefix().length());
		}
		if (!StringUtils.isBlank(settings.getMutedStreamSuffix())) {
			result = result.substring(0, result.length() - settings.getMutedStreamSuffix().length());
		}
		return result;
	}

	private String getAdaptorName(MuxAdaptor muxAdaptor) {
		return muxAdaptor != null ? muxAdaptor.getClass().getSimpleName() : "null";
	}

	/**
	 * The muted replica is published back to the same application over the local RTMP listener.
	 */
	private String getReplicaRtmpUrl(String streamId) {
		AntMediaApplicationAdapter application = (AntMediaApplicationAdapter) getApplication();
		return "rtmp://127.0.0.1:" + application.getServerSettings().getRtmpPort() + "/"
				+ application.getScope().getName() + "/" + streamId;
	}

	@Override
	public void streamFinished(Broadcast broadcast) {
		if (broadcast == null || StringUtils.isBlank(broadcast.getStreamId())) {
			return;
		}

		String streamId = broadcast.getStreamId();

		MutedStreamManager mutedStreamManager = mutedStreamManagers.remove(streamId);
		if (mutedStreamManager != null) {
			mutedStreamManager.stop();
		}

		if (!isMutedReplicaStream(streamId) && !loadSettings().isKeepMutedStreamsAfterEnd()) {
			String mutedStreamId = getMutedStreamId(streamId);
			IAntMediaStreamHandler app = getApplication();
			if (app.getDataStore().get(mutedStreamId) != null) {
				app.getDataStore().delete(mutedStreamId);
				logger.info("Deleted muted replica broadcast {} after source stream {} ended", mutedStreamId, streamId);
			}
		}
	}

	@Override
	public void joinedTheRoom(String roomId, String streamId) {
	}

	@Override
	public void leftTheRoom(String roomId, String streamId) {
	}
}
