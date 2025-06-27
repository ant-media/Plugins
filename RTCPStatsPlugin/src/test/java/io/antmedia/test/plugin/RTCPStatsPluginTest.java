package io.antmedia.test.plugin;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.app.RTCPStatsPacketListener;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.enterprise.webrtc.WebRTCApplication;
import io.antmedia.enterprise.webrtc.datachannel.DataChannelRouter;
import io.antmedia.plugin.RTCPStatsPlugin;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avcodec.AVProducerReferenceTime;
import org.bytedeco.javacpp.BytePointer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RTCPStatsPluginTest {

	private RTCPStatsPlugin plugin;
	private ApplicationContext applicationContext;
	private WebRTCApplication app;
	private AppSettings appSettings;
	private DataStore dataStore;
	private DataChannelRouter dataChannelRouter;

	@Before
	public void setUp() {
		plugin = Mockito.spy(new RTCPStatsPlugin());
		applicationContext = Mockito.mock(ApplicationContext.class);
		app = Mockito.mock(WebRTCApplication.class);
		appSettings = new AppSettings();
		dataStore = Mockito.mock(DataStore.class);
		dataChannelRouter = Mockito.mock(DataChannelRouter.class);

		when(applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(app);
		when(app.getAppSettings()).thenReturn(appSettings);
		when(app.getDataStore()).thenReturn(dataStore);
		when(app.getDataChannelRouter()).thenReturn(dataChannelRouter);

		plugin.setApplicationContext(applicationContext);
	}

	@Test
	public void testStreamStartedWithDifferentUrls() {
		when(app.isDataChannelEnabled()).thenReturn(true);
		when(app.addPacketListener(anyString(), any(RTCPStatsPacketListener.class))).thenReturn(true);

		Broadcast rtspBroadcast = new Broadcast();
		rtspBroadcast.setStreamUrl("rtsp://example.com/stream");

		Broadcast rtpBroadcast = new Broadcast();
		rtpBroadcast.setStreamUrl("rtp://example.com/stream");

		Broadcast httpBroadcast = new Broadcast();
		httpBroadcast.setStreamUrl("http://example.com/stream");

		when(dataStore.get("httpStream")).thenReturn(httpBroadcast);
		when(dataStore.get("rtspStream")).thenReturn(rtspBroadcast);
		when(dataStore.get("rtpStream")).thenReturn(rtpBroadcast);

		// Test RTSP - should register packet listener
		plugin.streamStarted("rtspStream");
		verify(app, times(1)).addPacketListener(eq("rtspStream"), any(RTCPStatsPacketListener.class));

		// Test RTP - should register packet listener
		plugin.streamStarted("rtpStream");
		verify(app, times(1)).addPacketListener(eq("rtpStream"), any(RTCPStatsPacketListener.class));

		// Test non-RTSP/RTP URL - should NOT register packet listener
		plugin.streamStarted("httpStream");
		verify(app, times(0)).addPacketListener(eq("httpStream"), any(RTCPStatsPacketListener.class));
	}

	@Test
	public void testStreamStartedWithNullUrl() {
		when(dataStore.get("testStream")).thenReturn(null);

		plugin.streamStarted("testStream");

		verify(app, times(0)).addPacketListener(anyString(), any(RTCPStatsPacketListener.class));
	}

	@Test
	public void testStreamStartedWithDataChannelDisabled() {
		Broadcast broadcast = new Broadcast();
		broadcast.setStreamUrl("rtsp://example.com/stream");

		when(app.isDataChannelEnabled()).thenReturn(false);
		when(dataStore.get("testStream")).thenReturn(broadcast);

		plugin.streamStarted("testStream");

		verify(app, times(0)).addPacketListener(anyString(), any(RTCPStatsPacketListener.class));
	}

	@Test
	public void testStreamFinished() {
		Broadcast broadcast = new Broadcast();
		broadcast.setStreamUrl("rtsp://example.com/stream");

		when(app.isDataChannelEnabled()).thenReturn(true);
		when(dataStore.get("testStream")).thenReturn(broadcast);
		when(app.addPacketListener(anyString(), any(RTCPStatsPacketListener.class))).thenReturn(true);

		plugin.streamStarted("testStream");
		verify(app, times(1)).addPacketListener(anyString(), any(RTCPStatsPacketListener.class));

		plugin.streamFinished("testStream");
		verify(app, times(1)).removePacketListener(anyString(), any(RTCPStatsPacketListener.class));

		plugin.streamFinished("notExistingStrem");
		// Times should basically be 0, here, but we put 1, since verify will count
		// all the calls from start of the test
		verify(app, times(1)).removePacketListener(anyString(), any(RTCPStatsPacketListener.class));
	}

	@Test
	public void testGetStreamUrl() {
		Broadcast broadcast = new Broadcast();
		broadcast.setStreamUrl("rtsp://example.com/stream");

		when(dataStore.get("testStream")).thenReturn(broadcast);

		// should trigger getUrl
		plugin.streamStarted("testStream");

		verify(dataStore, times(1)).get("testStream");
	}

	@Test
	public void testGetStreamUrlWithNoBroadcast() {
		when(dataStore.get("testStream")).thenReturn(null);

		plugin.streamStarted("testStream");

		verify(dataStore, times(1)).get("testStream");
		verify(app, times(0)).addPacketListener(anyString(), any(RTCPStatsPacketListener.class));
	}

	@Test
	public void testRefreshSettingsWithCustomSettings() {
		appSettings.setCustomSetting("plugin.rtcp-stats", "{\"updateOnlyOnNewSR\":false}");

		RTCPStatsPlugin newPlugin = Mockito.spy(new RTCPStatsPlugin());
		newPlugin.setApplicationContext(applicationContext);

		// Settings are refreshed during initialization
		// 2 times, once for setUp, once for this test
		verify(app, times(2)).getAppSettings();
	}

	@Test
	public void testOnVideoPacketTest() {
		// Packet data mocking stuff...
		AVPacket packet = new AVPacket();
		av_new_packet(packet, 600);
		packet.stream_index(0);
		packet.pts(12345L);

		AVProducerReferenceTime prft = new AVProducerReferenceTime();
		prft.last_rtcp_ntp_time(656899696L);
		prft.last_rtcp_reception_time(777117717L);
		prft.last_rtcp_packet_count(66);

		BytePointer sideDataPtr = new BytePointer(prft);
		av_packet_add_side_data(packet, AV_PKT_DATA_PRFT, sideDataPtr, prft.sizeof());


		when(app.isDataChannelEnabled()).thenReturn(true);
		io.antmedia.app.RTCPStatsPluginSettings settings = new io.antmedia.app.RTCPStatsPluginSettings();
		RTCPStatsPacketListener packetListener = new RTCPStatsPacketListener("testStream", app, settings);

		// Verify if msg publish was called for video
		packetListener.onVideoPacket("testStream", packet);
		verify(dataChannelRouter, times(1)).publisherMessageReceived(eq("testStream"), any(byte[].class), eq(false));

		// And for audio
		prft.last_rtcp_ntp_time(996899696L);
		prft.last_rtcp_reception_time(997117717L);
		prft.last_rtcp_packet_count(99);
		packetListener.onAudioPacket("testStream", packet);
		verify(dataChannelRouter, times(2)).publisherMessageReceived(eq("testStream"), any(byte[].class), eq(false));
	}
} 