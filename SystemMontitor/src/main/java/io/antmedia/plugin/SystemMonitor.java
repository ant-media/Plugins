package io.antmedia.plugin;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.cluster.ClusterNode;
import io.antmedia.cluster.IClusterNotifier;
import io.antmedia.cluster.IClusterStore;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.datastore.db.types.BroadcastUpdate;
import io.vertx.core.Vertx;

@Component(value="plugin.system-monitor")
public class SystemMonitor implements ApplicationContextAware {

	private static final int MONITORING_PERIOD = 10000;
	public static final String BEAN_NAME = "web.handler";
	protected static Logger logger = LoggerFactory.getLogger(SystemMonitor.class);
	
	private Vertx vertx;
	private ApplicationContext applicationContext;
	private DataStore dataStore;
	private long monitorTask;
	private IClusterStore clusterDataStore;
	private int MAX_NODE_COUNT = 1000;
	private int nextLiveNode = 0;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		vertx = (Vertx) applicationContext.getBean("vertxCore");
		
		AntMediaApplicationAdapter app = getApplication();
		dataStore = app.getDataStore();
		
		if(app.isClusterMode()) {
			IClusterNotifier clusterNotifier = (IClusterNotifier) applicationContext.getBean(IClusterNotifier.BEAN_NAME);
			clusterDataStore = clusterNotifier.getClusterStore();
			monitorTask = vertx.setPeriodic(MONITORING_PERIOD, id -> monitorSystemStatus());
		}
		
	}
		
	public void monitorSystemStatus() {
		handleOrphanStreams();
	}

	private void handleOrphanStreams() {
		logger.debug("Check Orphan Streams");
		List<ClusterNode> nodes = clusterDataStore.getClusterNodes(0, MAX_NODE_COUNT);
		
		List<String> deadNodes = new ArrayList<String>();
		List<String> liveNodes = new ArrayList<String>();

		deadNodes.add(AntMediaApplicationAdapter.NOT_ASSIGNED);
		
		for (ClusterNode clusterNode : nodes) {
			if(clusterNode.getStatus().equals(ClusterNode.DEAD)) {
				deadNodes.add(clusterNode.getIp());
			}
			else {
				liveNodes.add(clusterNode.getIp());
			}
		}
		
		for (String address : deadNodes) {
			logger.debug("Checking orphan streams for {}", address);
			checkAndTransferOrphanStreams(address, liveNodes);
		}
		
		
	}

	private void checkAndTransferOrphanStreams(String originAddress, List<String> liveNodes) {
		List<Broadcast> orphanBroadcasts = dataStore.getBroadcastListByHost(originAddress);
		for (Broadcast broadcast : orphanBroadcasts) {
			if(broadcast.getType().equals(AntMediaApplicationAdapter.STREAM_SOURCE)) {
				logger.info("Orphan stream found {} on origin {}", broadcast.getStreamId(), originAddress);
				
				broadcast.setOriginAdress(liveNodes.get(nextLiveNode++ % liveNodes.size()));
				getApplication().startStreaming(broadcast);
				
				logger.info("Orphan stream found 2 {} on origin {}", broadcast.getStreamId(), originAddress);

			}
		}
	}

	public AntMediaApplicationAdapter getApplication() {
		return (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}
}
