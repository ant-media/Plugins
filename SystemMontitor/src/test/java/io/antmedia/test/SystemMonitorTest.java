package io.antmedia.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.cluster.ClusterNode;
import io.antmedia.cluster.IClusterNotifier;
import io.antmedia.cluster.IClusterStore;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.plugin.SystemMonitor;
import io.vertx.core.Vertx;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.Collections;

public class SystemMonitorTest {

    private SystemMonitor systemMonitor;
    
    private IClusterNotifier clusterNotifier;
    private IClusterStore clusterStore;
    private DataStore dataStore;
    private Vertx vertx;
    private AntMediaApplicationAdapter app;


    @Before
    public void setUp() {
    	
    	app = mock(AntMediaApplicationAdapter.class);
    	vertx = mock(Vertx.class);
    	
        systemMonitor = new SystemMonitor();
        
        ApplicationContext appContext = mock(ApplicationContext.class);
        
        when(appContext.getBean("vertxCore")).thenReturn(vertx);
        when(appContext.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(app);
        
        clusterNotifier = mock(IClusterNotifier.class);
        clusterStore = mock(IClusterStore.class);
        dataStore = new InMemoryDataStore("test");

        when(app.getDataStore()).thenReturn(dataStore);
        when(app.isClusterMode()).thenReturn(true);
        when(appContext.getBean(IClusterNotifier.BEAN_NAME)).thenReturn(clusterNotifier);
        when(clusterNotifier.getClusterStore()).thenReturn(clusterStore);

		systemMonitor.setApplicationContext(appContext);
    }
   

    @Test
    public void testHandleOrphanStreamsTransfersToLiveNode() {
    	try {
    		long now = System.currentTimeMillis();

    		// Setup cluster nodes
    		ClusterNode deadNode = new ClusterNode();
    		deadNode.setIp("192.168.1.1");
    		deadNode.setLastUpdateTime(now - ClusterNode.NODE_UPDATE_PERIOD * 5); //dead

    		ClusterNode liveNode = new ClusterNode();
    		liveNode.setIp("192.168.1.2");
    		liveNode.setLastUpdateTime(now - ClusterNode.NODE_UPDATE_PERIOD * 1); //alive

    		when(clusterStore.getClusterNodes(anyInt(), anyInt()))
    		.thenReturn(Arrays.asList(deadNode, liveNode));

    		// Setup orphan stream
    		Broadcast orphan = new Broadcast();
    		orphan.setStreamId("orphan-stream");

    		orphan.setType(AntMediaApplicationAdapter.STREAM_SOURCE);
    		orphan.setOriginAdress("192.168.1.1");

    		when(dataStore.getBroadcastListByHost("192.168.1.1"))
    		.thenReturn(Collections.singletonList(orphan));

    		doNothing().when(app).startStreaming(any(Broadcast.class));

    		systemMonitor.monitorSystemStatus();

    		// Capture updated broadcast and verify origin changed
    		ArgumentCaptor<Broadcast> captor = ArgumentCaptor.forClass(Broadcast.class);
    		verify(app).startStreaming(captor.capture());

    		Broadcast updated = captor.getValue();
    		assertEquals("192.168.1.2", updated.getOriginAdress());
    		assertEquals("orphan-stream", updated.getStreamId());
    	} catch (Exception e) {
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    	}
    }

    
    @Test
    public void testHandleOrphanStreamsNoOrphans() {
		long now = System.currentTimeMillis();

        // Only one live node, no orphans
        ClusterNode liveNode = new ClusterNode();
        liveNode.setIp("192.168.1.2");
		liveNode.setLastUpdateTime(now - ClusterNode.NODE_UPDATE_PERIOD * 1);

        when(clusterStore.getClusterNodes(anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(liveNode));

		systemMonitor.monitorSystemStatus();

        verify(app, never()).startStreaming(any());
    }
    
}
