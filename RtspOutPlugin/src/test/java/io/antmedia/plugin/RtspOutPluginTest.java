package io.antmedia.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.red5.server.api.scope.IScope;
import org.springframework.context.ApplicationContext;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.EncoderSettings;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.muxer.Muxer;
import io.antmedia.muxer.RtspMuxer;
import io.antmedia.security.ITokenService;
import io.antmedia.settings.ServerSettings;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class RtspOutPluginTest {

	private static final String STREAM_ID = "stream1";

	private RtspOutPlugin plugin;
	private Vertx vertx;
	private IAntMediaStreamHandler app;
	private AppSettings appSettings;
	private MuxAdaptor muxAdaptor;
	private MockedStatic<MediaMtxProcess> mediamtx;
	private ApplicationContext context;
	private Handler<Long> tick;

	@SuppressWarnings("unchecked")
	@Before
	public void setUp() throws Exception {
		plugin = new RtspOutPlugin();

		context = mock(ApplicationContext.class);
		IScope scope = mock(IScope.class);
		ServerSettings serverSettings = mock(ServerSettings.class);
		vertx = mock(Vertx.class);
		app = mock(IAntMediaStreamHandler.class);
		appSettings = mock(AppSettings.class);
		muxAdaptor = mock(MuxAdaptor.class);

		when(context.getBean(IAntMediaStreamHandler.VERTX_BEAN_NAME)).thenReturn(vertx);
		when(context.getBean(ServerSettings.BEAN_NAME)).thenReturn(serverSettings);
		when(context.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(app);
		when(app.getAppSettings()).thenReturn(appSettings);
		when(app.getScope()).thenReturn(scope);
		when(app.getMuxAdaptor(STREAM_ID)).thenReturn(muxAdaptor);
		when(scope.getName()).thenReturn("LiveApp");
		when(serverSettings.getHostAddress()).thenReturn("10.0.0.1");
		when(appSettings.getCustomSetting(RtspOutPlugin.SETTINGS_KEY)).thenReturn(null);
		when(muxAdaptor.addMuxer(any(Muxer.class), anyInt())).thenReturn(true);

		when(vertx.setPeriodic(anyLong(), any())).thenReturn(1L);
		// the reconcile pass runs inline, there is no worker pool here
		when(vertx.executeBlocking(any(Callable.class), anyBoolean())).thenAnswer(invocation -> {
			invocation.<Callable<?>>getArgument(0).call();
			return null;
		});

		mediamtx = mockStatic(MediaMtxProcess.class);
		mediaMtxUp(true);
		generation(1);

		plugin.setApplicationContext(context);

		ArgumentCaptor<Handler<Long>> timer = ArgumentCaptor.forClass(Handler.class);
		verify(vertx).setPeriodic(anyLong(), timer.capture());
		tick = timer.getValue();
	}

	@After
	public void tearDown() {
		mediamtx.close();
	}

	@Test
	public void testAuthEndpointFollowsTheMasterSwitch() throws Exception {
		// setUp ran with the defaults, so the endpoint is up for this app
		assertTrue(AuthHttpServer.isRunning());

		AuthHttpServer.stop();
		customSetting("{\"enabled\":false}");
		new RtspOutPlugin().setApplicationContext(context);

		assertFalse("a disabled app must not leave a listening socket behind", AuthHttpServer.isRunning());
	}

	@Test
	public void testWiringAndSettings() {
		verify(app).addStreamListener(plugin);
		assertEquals("rtsp://10.0.0.1:8554/LiveApp/stream1", plugin.getRtspUrl(STREAM_ID));

		assertTrue(plugin.loadSettings().isEnabled());
		assertTrue(plugin.loadSettings().isAuthEnabled());

		customSetting("{\"enabled\":false,\"authEnabled\":false}");
		assertFalse(plugin.loadSettings().isEnabled());
		assertFalse(plugin.loadSettings().isAuthEnabled());

		customSetting("{ not json");
		assertTrue(plugin.loadSettings().isEnabled());
		assertTrue(plugin.loadSettings().isAuthEnabled());
	}

	@Test
	public void testSourceAndRenditionEndpoints() {
		when(muxAdaptor.getEncoderSettingsList())
				.thenReturn(List.of(new EncoderSettings(480, 500000, 64000, true),
						new EncoderSettings(720, 1500000, 128000, true)));

		plugin.streamStarted(broadcast());

		assertEquals(List.of(STREAM_ID, STREAM_ID + "_480p", STREAM_ID + "_720p"), endpoints());
		verify(muxAdaptor, times(3)).addMuxer(any(Muxer.class), anyInt());
	}

	@Test
	public void testEnableAndDisableOverrides() {
		plugin.disable(STREAM_ID);
		plugin.streamStarted(broadcast());
		verify(muxAdaptor, never()).addMuxer(any(Muxer.class), anyInt());
		assertTrue(endpoints().isEmpty());

		// the override wins the other way too, on a server that opts streams out by default
		customSetting("{\"enabledByDefault\":false}");
		plugin.enable(STREAM_ID);
		plugin.streamStarted(broadcast());
		assertEquals(List.of(STREAM_ID), endpoints());
	}

	@Test
	public void testNothingIsSpentWhileMediaMtxIsUnusable() {
		mediamtx.when(MediaMtxProcess::isGivenUp).thenReturn(true);
		plugin.streamStarted(broadcast());
		ticks(5);
		verify(muxAdaptor, never()).addMuxer(any(Muxer.class), anyInt());

		mediamtx.when(MediaMtxProcess::isGivenUp).thenReturn(false);
		mediaMtxUp(false);
		ticks(15);
		verify(muxAdaptor, never()).addMuxer(any(Muxer.class), anyInt());

		// 20 passes later the budget is still untouched, so the stream still gets its endpoint
		mediaMtxUp(true);
		ticks(1);
		assertEquals(List.of(STREAM_ID), endpoints());
	}

	@Test
	public void testGivesUpAfterMaxAttempts() {
		when(muxAdaptor.addMuxer(any(Muxer.class), anyInt())).thenReturn(false);

		plugin.streamStarted(broadcast());
		ticks(15);

		verify(muxAdaptor, times(10)).addMuxer(any(Muxer.class), anyInt());
		// a failed addMuxer leaves the muxer on the encoders, attach() has to take it back off
		verify(muxAdaptor, times(10)).removeMuxer(any(Muxer.class));
		assertTrue(endpoints().isEmpty());
	}

	@Test
	public void testSettledStreamIsNotWalkedAgain() {
		plugin.streamStarted(broadcast());
		ticks(5);

		verify(muxAdaptor, times(1)).addMuxer(any(Muxer.class), anyInt());
		// the pass returns before the port probe, which is the point of the settled flag
		mediamtx.verify(MediaMtxProcess::isListening, times(1));
	}

	@Test
	public void testRebuildsDeadAndStaleEndpoints() throws Exception {
		plugin.streamStarted(broadcast());

		// the publish leg died while the stream stayed up
		attachedMuxer().writeTrailer();
		ticks(1);
		verify(muxAdaptor, times(1)).removeMuxer(any(Muxer.class));
		verify(muxAdaptor, times(2)).addMuxer(any(Muxer.class), anyInt());

		// MediaMTX restarted under it, so everything it holds is stale
		generation(2);
		ticks(1);
		verify(muxAdaptor, times(2)).removeMuxer(any(Muxer.class));
		verify(muxAdaptor, times(3)).addMuxer(any(Muxer.class), anyInt());
		assertEquals(List.of(STREAM_ID), endpoints());
	}

	@Test
	public void testTeardownBeatsAttach() {
		when(muxAdaptor.isStopRequestExist()).thenReturn(true);
		plugin.streamStarted(broadcast());
		verify(muxAdaptor, never()).addMuxer(any(Muxer.class), anyInt());
		plugin.streamFinished(broadcast());

		when(muxAdaptor.isStopRequestExist()).thenReturn(false);
		plugin.streamStarted(broadcast());
		plugin.streamFinished(broadcast());

		verify(muxAdaptor, times(1)).removeMuxer(any(Muxer.class));
		assertTrue(endpoints().isEmpty());

		// a pass firing after streamFinished must not open a path nothing would ever close
		ticks(5);
		verify(muxAdaptor, times(1)).addMuxer(any(Muxer.class), anyInt());
	}

	/** The auth callback gets a MediaMTX path and has to get back to the stream it belongs to. */
	@Test
	public void testStreamIdIsReadBackFromAMediaMtxPath() {
		plugin.streamStarted(broadcast());

		assertEquals(STREAM_ID, plugin.streamIdOf("LiveApp/" + STREAM_ID));
		assertEquals("one token has to cover the renditions too",
				STREAM_ID, plugin.streamIdOf("LiveApp/" + STREAM_ID + "_480p"));

		// a stream id that genuinely ends in _720p is its own stream, not a rendition of "cam"
		Broadcast oddName = mock(Broadcast.class);
		when(oddName.getStreamId()).thenReturn("cam_720p");
		when(app.getMuxAdaptor("cam_720p")).thenReturn(muxAdaptor);
		plugin.streamStarted(oddName);

		assertEquals("cam_720p", plugin.streamIdOf("LiveApp/cam_720p"));
		// and one we never published is left alone either way
		assertEquals("ghost_360p", plugin.streamIdOf("LiveApp/ghost_360p"));
	}

	@Test
	public void testAuthorizeRoutesOnTheAppInThePath() {
		RtspAuthRequest request = new RtspAuthRequest();
		request.setAction("read");
		request.setIp("10.0.0.9");

		request.setPath("LiveApp/" + STREAM_ID);
		assertTrue(RtspOutPlugin.authorize(request).isSuccess());

		request.setPath("SomeOtherApp/" + STREAM_ID);
		assertFalse(RtspOutPlugin.authorize(request).isSuccess());
	}

	@Test
	public void testDuplicateStartIsIgnored() {
		plugin.streamStarted(broadcast());
		plugin.streamStarted(broadcast());

		verify(muxAdaptor, times(1)).addMuxer(any(Muxer.class), anyInt());
		assertEquals(List.of(STREAM_ID), endpoints());
	}

	/** The normal path: both ingest paths drop the MuxAdaptor before streamFinished reaches us. */
	@Test
	public void testEndpointsCloseWhenTheAdaptorIsAlreadyGone() throws Exception {
		plugin.streamStarted(broadcast());
		RtspMuxer muxer = attachedMuxer();
		assertTrue(muxer.isWritable());

		when(app.getMuxAdaptor(STREAM_ID)).thenReturn(null);
		plugin.streamFinished(broadcast());

		verify(muxAdaptor, never()).removeMuxer(any(Muxer.class));
		assertFalse("the muxer has to be trailered by hand when the adaptor is gone", muxer.isWritable());
		assertTrue(endpoints().isEmpty());
	}

	/** An audio only bind is a bug worth logging, but the endpoint still works and must not be retried. */
	@Test
	public void testAudioOnlyBindKeepsTheEndpoint() {
		when(muxAdaptor.isEnableVideo()).thenReturn(true);

		plugin.streamStarted(broadcast());
		ticks(3);

		assertEquals(List.of(STREAM_ID), endpoints());
		verify(muxAdaptor, times(1)).addMuxer(any(Muxer.class), anyInt());
	}

	/** WebRTC ingest has no direct muxing, and height 0 there binds audio only. */
	@Test
	public void testBindHeightFallsBackToZeroWithoutVideoParameters() {
		when(muxAdaptor.directMuxingSupported()).thenReturn(false);
		when(muxAdaptor.getVideoCodecParameters()).thenReturn(null);

		plugin.streamStarted(broadcast());

		verify(muxAdaptor).addMuxer(any(Muxer.class), eq(0));
	}

	/** The rung a muxer binds to. The wrong height here is a rendition endpoint carrying audio only. */
	@Test
	public void testBindHeightComesFromTheVideoCodecParameters() {
		AVCodecParameters videoParameters = new AVCodecParameters();
		videoParameters.height(720);
		when(muxAdaptor.directMuxingSupported()).thenReturn(false);
		when(muxAdaptor.getVideoCodecParameters()).thenReturn(videoParameters);

		plugin.streamStarted(broadcast());

		verify(muxAdaptor).addMuxer(any(Muxer.class), eq(720));
	}

	/** Direct muxing takes the stream at any resolution, and asking for one would bind nothing. */
	@Test
	public void testDirectMuxingBindsAtAnyHeight() {
		when(muxAdaptor.directMuxingSupported()).thenReturn(true);

		plugin.streamStarted(broadcast());

		verify(muxAdaptor).addMuxer(any(Muxer.class), eq(0));
	}

	@Test
	public void testClosingAStreamWeNeverServedIsHarmless() {
		plugin.streamFinished(broadcast());

		verify(muxAdaptor, never()).removeMuxer(any(Muxer.class));
		assertTrue(endpoints().isEmpty());
	}

	/** Serving the RTSP port unauthenticated because a socket failed is not a trade worth making. */
	@Test
	public void testMediaMtxIsNotStartedWithoutTheAuthEndpoint() throws Exception {
		try (MockedStatic<AuthHttpServer> auth = mockStatic(AuthHttpServer.class)) {
			auth.when(AuthHttpServer::start).thenReturn(0);

			new RtspOutPlugin().setApplicationContext(context);
		}

		// still only the call setUp made, the app that could not open its endpoint did not start anything
		mediamtx.verify(() -> MediaMtxProcess.start(any()), times(1));
	}

	/** Undeploying one app must not take MediaMTX or the other apps with it. */
	@Test
	public void testUndeployClosesThisAppsEndpointsOnly() throws Exception {
		plugin.streamStarted(broadcast());
		RtspMuxer muxer = attachedMuxer();

		plugin.shutdown();

		verify(vertx).cancelTimer(1L);
		verify(app).removeStreamListener(plugin);
		// the adaptor is still registered here, so it is the one that trailers the muxer
		verify(muxAdaptor).removeMuxer(muxer);
		assertTrue(endpoints().isEmpty());
		mediamtx.verify(MediaMtxProcess::stop, never());
	}

	@Test
	public void testAStartThatBlowsUpIsLeftToTheReconciler() {
		doThrow(new IllegalStateException("boom")).when(app).getMuxAdaptor(STREAM_ID);

		// the other stream listeners still have to get their turn
		plugin.streamStarted(broadcast());
		assertTrue(endpoints().isEmpty());

		doReturn(muxAdaptor).when(app).getMuxAdaptor(STREAM_ID);
		ticks(1);
		assertEquals(List.of(STREAM_ID), endpoints());
	}

	/** Enterprise only bean. It may not be up yet when the first reader arrives. */
	@Test
	public void testTokenServiceIsLookedUpLazily() {
		String beanName = ITokenService.BeanName.TOKEN_SERVICE.toString();
		assertNull("community edition has no token service", plugin.getTokenService());

		ITokenService tokenService = mock(ITokenService.class);
		when(context.containsBean(beanName)).thenReturn(true);
		when(context.getBean(beanName)).thenReturn(tokenService);

		assertSame(tokenService, plugin.getTokenService());
	}

	private void customSetting(String json) {
		when(appSettings.getCustomSetting(RtspOutPlugin.SETTINGS_KEY)).thenReturn(json);
	}

	private void mediaMtxUp(boolean up) {
		mediamtx.when(MediaMtxProcess::isListening).thenReturn(up);
	}

	private void generation(int value) {
		mediamtx.when(MediaMtxProcess::getGeneration).thenReturn(value);
	}

	private void ticks(int count) {
		for (int i = 0; i < count; i++) {
			tick.handle(1L);
		}
	}

	private Broadcast broadcast() {
		Broadcast broadcast = mock(Broadcast.class);
		when(broadcast.getStreamId()).thenReturn(STREAM_ID);
		return broadcast;
	}

	@SuppressWarnings("unchecked")
	private List<String> endpoints() {
		Map<String, Object> streams = (Map<String, Object>) plugin.getStatus().get("activeStreams");
		return (List<String>) streams.getOrDefault(STREAM_ID, List.of());
	}

	@SuppressWarnings("unchecked")
	private RtspMuxer attachedMuxer() throws Exception {
		Object session = ((Map<String, Object>) field(plugin, "sessions")).get(STREAM_ID);
		return ((Map<String, RtspMuxer>) field(session, "attached")).get(STREAM_ID);
	}

	private static Object field(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}
}
