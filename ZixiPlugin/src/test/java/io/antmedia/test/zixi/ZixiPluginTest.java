package io.antmedia.test.zixi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.red5.server.api.IContext;
import org.red5.server.api.scope.IScope;
import org.springframework.context.ApplicationContext;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.IDataStoreFactory;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.filter.StreamAcceptFilter;
import io.antmedia.plugin.ZixiPlugin;
import io.antmedia.rest.model.Result;
import io.antmedia.settings.ServerSettings;
import io.antmedia.zixi.ZixiClient;
import io.vertx.core.Vertx;

public class ZixiPluginTest {

    Vertx vertx = Vertx.vertx();

    AntMediaApplicationAdapter appAdaptor;

    private Process push2UDP;

    private Process push2ZixiBC;

    private InMemoryDataStore dtStore;

    /*
     * Zixi Broadcaster should be running in the local computer for these tests
     */

    @Before
    public void before() {
        appAdaptor = new AntMediaApplicationAdapter();
        AppSettings appSettings = new AppSettings();
        appSettings.setHlsMuxingEnabled(false);
        appAdaptor.setAppSettings(appSettings);
        appAdaptor.setVertx(vertx);
        appAdaptor.setStreamAcceptFilter(new StreamAcceptFilter());
        IScope scope = Mockito.mock(IScope.class);
        Mockito.when(scope.getName()).thenReturn("junit");
        appAdaptor.setScope(scope);
        appAdaptor.setServerSettings(new ServerSettings());
        dtStore = new InMemoryDataStore("junit");
        appAdaptor.setDataStore(dtStore);

        IContext context = Mockito.mock(IContext.class);

        ApplicationContext appContext = Mockito.mock(ApplicationContext.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);

        Mockito.when(scope.getContext()).thenReturn(context);

        IDataStoreFactory dsf = Mockito.mock(IDataStoreFactory.class); 
        Mockito.when(context.getBean(IDataStoreFactory.BEAN_NAME)).thenReturn(dsf);
        Mockito.when(dsf.getDataStore()).thenReturn(dtStore);

        Mockito.when(appContext.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(appAdaptor);
        

        Mockito.when(appContext.getBean(AppSettings.BEAN_NAME)).thenReturn(appSettings);
        Mockito.when(appContext.containsBean(AppSettings.BEAN_NAME)).thenReturn(true);

       // Mockito.when(context.getResource(Mockito.anyString()).thenReturn();
    }


    private static Process tmpExec;

	public static Process execute(final String command) {
		tmpExec = null;
		new Thread() {

			public void run() {
				try 
				{
					tmpExec = Runtime.getRuntime().exec(command);
					InputStream errorStream = tmpExec.getErrorStream();
					byte[] data = new byte[1024];
					int length = 0;

					while ((length = errorStream.read(data, 0, data.length)) > 0) {
						System.out.println(new String(data, 0, length));
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			};
		}.start();

		while (tmpExec == null) {
			try {
				System.out.println("Waiting for exec get initialized...");
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		return tmpExec;
	}


    public void startPushingToZixiBroadcaster() {
        //start 
        String push2UDPCommand = "src/test/resources/zixi_sdks-antmedia-linux64-14.13.44304/test/push2UDP.sh";
        push2UDP = execute(push2UDPCommand);

        String push2ZixiBCCommand = "src/test/resources/zixi_sdks-antmedia-linux64-14.13.44304/test/push2ZixiBC.sh";
        push2ZixiBC = execute(push2ZixiBCCommand);
    }

    public void stopPushingToZixiBroadcaster() {
        push2UDP.destroy();
        push2ZixiBC.destroy();
    }

    @Test
    public void testStartStopDeleteClientWithoutRealStart(){
        ZixiPlugin zixiPlugin = new ZixiPlugin();
        zixiPlugin.setApp(appAdaptor);
        zixiPlugin.setVertx(vertx);

        
        Result result = zixiPlugin.startClient("stream1");
        //it should be false because there is no stream with stream1
        assertFalse(result.isSuccess());

        Broadcast broadcast = new Broadcast();
        result = zixiPlugin.startClient(broadcast, false);
        //it should be false because broadcast object has no streamUrl
        assertFalse(result.isSuccess());

        broadcast.setStreamUrl("srt://");
        result = zixiPlugin.startClient(broadcast, false);
        //it should be false because broadcast object' streamUrl starts wth srt. It should be zixi
        assertFalse(result.isSuccess());


        broadcast.setStreamUrl("zixi://127.0.0.1:2077/stream1");
        result = zixiPlugin.startClient(broadcast, false);
        //it should be true
        assertTrue(result.isSuccess());

        Broadcast broadcastTmp = dtStore.get(result.getDataId());
        assertEquals(broadcast.getStreamUrl(), broadcastTmp.getStreamUrl());
        assertEquals(ZixiPlugin.PUBLISH_TYPE_ZIXI_CLIENT, broadcast.getPublishType());

        //it should be zero because "start" was false
        assertEquals(0, zixiPlugin.getZixiClientMap().size());

        String streamId = broadcastTmp.getStreamId();
        result = zixiPlugin.stopClient(streamId);
        //it should be false because it's not started
        assertFalse(result.isSuccess());
        assertNotNull(dtStore.get(streamId));

        result = zixiPlugin.deleteClient(streamId);
        //it should be true because there is a broadcast in the datastore
        assertTrue(result.isSuccess());


        result = zixiPlugin.stopClient("stream1");
        //it should be false because there is no stream with stream1
        assertFalse(result.isSuccess());


        String streamIdTmp = dtStore.save(new Broadcast());
        assertNotNull(streamIdTmp);
        result = zixiPlugin.deleteClient(streamIdTmp);
        //it should be false because streamIdTmp is not zixi type
        assertFalse(result.isSuccess());

    }




    //test eof

    //test no input is coming

}
