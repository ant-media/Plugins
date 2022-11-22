package io.antmedia.test.zixi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import io.antmedia.filter.StreamAcceptFilter;
import io.antmedia.plugin.ZixiPlugin;
import io.antmedia.settings.ServerSettings;
import io.antmedia.zixi.ZixiClient;
import io.vertx.core.Vertx;

public class ZixiClientTest {

    Vertx vertx = Vertx.vertx();

    AntMediaApplicationAdapter appAdaptor;

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
        InMemoryDataStore dtStore = new InMemoryDataStore("junit");
        appAdaptor.setDataStore(dtStore);

        IContext context = Mockito.mock(IContext.class);

        ApplicationContext appContext = Mockito.mock(ApplicationContext.class);
        Mockito.when(context.getApplicationContext()).thenReturn(appContext);

        Mockito.when(scope.getContext()).thenReturn(context);

        IDataStoreFactory dsf = Mockito.mock(IDataStoreFactory.class); 
        Mockito.when(context.getBean(IDataStoreFactory.BEAN_NAME)).thenReturn(dsf);
        Mockito.when(dsf.getDataStore()).thenReturn(dtStore);

        ApplicationContext appCtx = context.getApplicationContext();
        Mockito.when(appContext.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(appAdaptor);
        

        Mockito.when(appContext.getBean(AppSettings.BEAN_NAME)).thenReturn(appSettings);
        Mockito.when(appContext.containsBean(AppSettings.BEAN_NAME)).thenReturn(true);

       // Mockito.when(context.getResource(Mockito.anyString()).thenReturn();
    }

    @Test
    public void testConnectAndDisconnect(){
       
        //this test requires there is a stream available with stream1 in ZB 

        ZixiClient client = new ZixiClient(vertx, appAdaptor, "zixi://127.0.0.1:2077/stream1","stream1");
       // assertTrue(client.init());

        assertTrue(client.connect());

        assertTrue(client.disconnect());
    }


    @Test
    public void testConnectAndDisconnectNotExistStream(){
       
        //this test requires there is a stream available with stream1 in ZB 

        ZixiClient client = new ZixiClient(vertx, appAdaptor, "zixi://127.0.0.1:2077/stream_not_exists","stream1");
       // assertTrue(client.init());
        assertFalse(client.start().isSuccess());

        assertFalse(client.disconnect());
    }


    
    @Test
    public void testStartStop() {

        //this test requires pushing the stream to ZB 


        ZixiClient client = new ZixiClient(vertx, appAdaptor, "zixi://127.0.0.1:2077/stream1", "stream1");
       
        assertEquals(0, ZixiClient.getSocketqueuemap().size());

        assertTrue(client.start().isSuccess());

       
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
            return client.getPrepared().get();
        });

        assertEquals(1, ZixiClient.getSocketqueuemap().size());
        //assertEquals(client.getSoc)

        assertTrue(client.stop().isSuccess());
        assertTrue(client.getStopRequested().get());

        assertFalse(client.getStopped().get());
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> {
            return client.getStopped().get();
        });
        assertEquals(0, ZixiClient.getSocketqueuemap().size());
       

    }


    //test eof

    //test no input is coming

}
