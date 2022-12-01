package io.antmedia.test.zixi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.buffer.IoBuffer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.red5.codec.IAudioStreamCodec;
import org.red5.codec.IVideoStreamCodec;
import org.red5.codec.StreamCodecInfo;
import org.red5.io.ITag;
import org.red5.io.flv.impl.FLVReader;
import org.red5.server.api.IContext;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.stream.AudioCodecFactory;
import org.red5.server.stream.ClientBroadcastStream;
import org.red5.server.stream.VideoCodecFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.IDataStoreFactory;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.filter.StreamAcceptFilter;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.settings.ServerSettings;
import io.antmedia.zixi.ZixiFeeder;
import io.vertx.core.Vertx;

public class ZixiFeederTest {
    
    Vertx vertx = Vertx.vertx();
    private AntMediaApplicationAdapter appAdaptor;
    private InMemoryDataStore dtStore;
    private AppSettings appSettings;

    private static Logger log = LoggerFactory.getLogger(ZixiFeederTest.class);

    public class StreamPacket implements IStreamPacket {

		private ITag readTag;
		private IoBuffer data;

		public StreamPacket(ITag tag) {
			readTag = tag;
			data = readTag.getBody();
		}

		@Override
		public int getTimestamp() {
			return readTag.getTimestamp();
		}

		@Override
		public byte getDataType() {
			return readTag.getDataType();
		}

		@Override
		public IoBuffer getData() {
			return data;
		}

		public void setData(IoBuffer data) {
			this.data = data;
		}
	}

    @Before
    public void before() {
        appAdaptor = new AntMediaApplicationAdapter();
        appSettings = new AppSettings();
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
		Mockito.when(context.getResource(Mockito.anyString())).thenReturn(Mockito.mock(Resource.class));

        IDataStoreFactory dsf = Mockito.mock(IDataStoreFactory.class); 
        Mockito.when(context.getBean(IDataStoreFactory.BEAN_NAME)).thenReturn(dsf);
        Mockito.when(dsf.getDataStore()).thenReturn(dtStore);

        Mockito.when(appContext.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(appAdaptor);
        

        Mockito.when(appContext.getBean(AppSettings.BEAN_NAME)).thenReturn(appSettings);
        Mockito.when(appContext.containsBean(AppSettings.BEAN_NAME)).thenReturn(true);

        Mockito.when(appContext.containsBean(IAntMediaStreamHandler.VERTX_BEAN_NAME)).thenReturn(true);
		Mockito.when(appContext.getBean(IAntMediaStreamHandler.VERTX_BEAN_NAME)).thenReturn(vertx);
        
    }


    @Test
    public void testLoggingConfigure() {
        ZixiFeeder zixiFeeder = new ZixiFeeder("url", vertx);

        assertTrue(zixiFeeder.configureLogLevel(3));
    }

    @Test
    public void testParseURL() {
        ZixiFeeder zixiFeeder = new ZixiFeeder("zixi://127.0.12.12:2066/test678", vertx);

        assertEquals("127.0.12.12", zixiFeeder.getHost());
        assertEquals(2066, zixiFeeder.getPort());
        assertEquals("test678", zixiFeeder.getChannel());
    }

    @Test
    public void testConnect() {
        //this test requires ZixiBroacaster running on 127.0.0.1 
        // and "stream1" should be added as push type in the Inputs
        ZixiFeeder zixiFeeder = new ZixiFeeder("zixi://127.0.0.1:2088/stream1", vertx);

        assertEquals("127.0.0.1", zixiFeeder.getHost());
        assertEquals(2088, zixiFeeder.getPort());
        assertEquals("stream1", zixiFeeder.getChannel());

        assertTrue(zixiFeeder.connect());

        assertTrue(zixiFeeder.disconnect());
    }


    @Test
    public void testConnectFail() {
        ZixiFeeder zixiFeeder = new ZixiFeeder("zisssxi://127.0.0.1:2088/stream1", vertx);

        assertFalse(zixiFeeder.connect());

        assertFalse(zixiFeeder.disconnect());
    }

    @Test
    public void testPushStreamtoZixiBroadcaster(){

        log.info("testPushStreamtoZixiBroadcaster");
        String name = "streamAny";
		appSettings.setMp4MuxingEnabled(false);
		appSettings.setAddDateTimeToMp4FileName(false);
		appSettings.setHlsMuxingEnabled(false);
		

		ClientBroadcastStream clientBroadcastStream = new ClientBroadcastStream();
		StreamCodecInfo info = new StreamCodecInfo();
		clientBroadcastStream.setCodecInfo(info);

		MuxAdaptor muxAdaptor = MuxAdaptor.initializeMuxAdaptor(clientBroadcastStream, false, appAdaptor.getScope());

		File file = null;
		try {

            log.info("testPushStreamtoZixiBroadcaster 1");
			file = new File("src/test/resources/zixi_sdks-antmedia-linux64-14.13.44304/test/test.flv"); //ResourceUtils.getFile(this.getClass().getResource("test.flv"));
			final FLVReader flvReader = new FLVReader(file);

            log.info("testPushStreamtoZixiBroadcaster 2");

			assertTrue(file.exists());
			Broadcast broadcast = new Broadcast();
			try {
				broadcast.setStreamId(name);
			} catch (Exception e) {
				e.printStackTrace();
			}

			muxAdaptor.setBroadcast(broadcast);
			boolean result = muxAdaptor.init(appAdaptor.getScope(), name, false);
			assert (result);

			muxAdaptor.start();

			
			new Thread() {
				public void run() {
					feedMuxAdaptor(flvReader, Arrays.asList(muxAdaptor), info);
				};
			}.start();
		

			Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> muxAdaptor.isRecording());

			ZixiFeeder zixiFeeder = new ZixiFeeder("zixi://127.0.0.1:2088/stream1", vertx);

			assertFalse(zixiFeeder.getIsRunning().get());

			muxAdaptor.addMuxer(zixiFeeder);

			Awaitility.await().atMost(10, TimeUnit.SECONDS)
					.until(() -> zixiFeeder.getIsRunning().get());

			Thread.sleep(500000);

			muxAdaptor.stop(true);

			flvReader.close();

			Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> !muxAdaptor.isRecording());

		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}


    }


    public void feedMuxAdaptor(FLVReader flvReader,List<MuxAdaptor> muxAdaptorList, StreamCodecInfo info)
	{
		boolean firstAudioPacketReceived = false;
		boolean firstVideoPacketReceived = false;
        long now = System.currentTimeMillis();
        long firstTimestamp = -1;
		while (flvReader.hasMoreTags())
		{
			ITag readTag = flvReader.readTag();
             
			StreamPacket streamPacket = new StreamPacket(readTag);
			if (!firstAudioPacketReceived && streamPacket.getDataType() == Constants.TYPE_AUDIO_DATA)
			{
				IAudioStreamCodec audioStreamCodec = AudioCodecFactory.getAudioCodec(streamPacket.getData().position(0));
				info.setAudioCodec(audioStreamCodec);
				audioStreamCodec.addData(streamPacket.getData().position(0));
				info.setHasAudio(true);
				firstAudioPacketReceived = true;
			}
			else if (!firstVideoPacketReceived && streamPacket.getDataType() == Constants.TYPE_VIDEO_DATA)
			{
				IVideoStreamCodec videoStreamCodec = VideoCodecFactory.getVideoCodec(streamPacket.getData().position(0));
				videoStreamCodec.addData(streamPacket.getData().position(0));
				info.setVideoCodec(videoStreamCodec);
				info.setHasVideo(true);
				firstVideoPacketReceived = true;
			}

            if (firstTimestamp == -1) {
                firstTimestamp = readTag.getTimestamp();
            }
            
            long realTimeDifference = System.currentTimeMillis() - now;
            long timeToWaitMs = readTag.getTimestamp() - firstTimestamp - realTimeDifference;
            
            if (timeToWaitMs > 0) {
                try {
              //      log.info("Waiting for {}ms real time difference: {} current time stamp:{} first time stamp:{}", timeToWaitMs, realTimeDifference, readTag.getTimestamp(), firstTimestamp);
                    Thread.sleep(timeToWaitMs);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }



           
			for (MuxAdaptor muxAdaptor : muxAdaptorList) {

				streamPacket = new StreamPacket(readTag);
                
				int bodySize = streamPacket.getData().position(0).limit();
				byte[] data = new byte[bodySize];
				streamPacket.getData().get(data);

				streamPacket.setData(IoBuffer.wrap(data));

				muxAdaptor.packetReceived(null, streamPacket);

			}



           
            
		}
	}

   
}
