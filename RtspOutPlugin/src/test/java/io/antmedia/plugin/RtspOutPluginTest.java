package io.antmedia.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
	private Handler<Long> tick;

	@SuppressWarnings("unchecked")
	@Before
	public void setUp() throws Exception {
		plugin = new RtspOutPlugin();

		ApplicationContext context = mock(ApplicationContext.class);
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
	public void testWiringAndSettings() {
		verify(app).addStreamListener(plugin);
		assertEquals("rtsp://10.0.0.1:8554/LiveApp/stream1", plugin.getRtspUrl(STREAM_ID));

		assertTrue(plugin.loadSettings().isEnabled());
		assertEquals(8554, plugin.loadSettings().getRtspPort());

		customSetting("{\"enabled\":false,\"rtspPort\":9000}");
		assertFalse(plugin.loadSettings().isEnabled());
		assertEquals(9000, plugin.loadSettings().getRtspPort());

		customSetting("{ not json");
		assertTrue(plugin.loadSettings().isEnabled());
		assertEquals(8554, plugin.loadSettings().getRtspPort());
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
		mediamtx.verify(() -> MediaMtxProcess.isListening(anyInt()), times(1));
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

	private void customSetting(String json) {
		when(appSettings.getCustomSetting(RtspOutPlugin.SETTINGS_KEY)).thenReturn(json);
	}

	private void mediaMtxUp(boolean up) {
		mediamtx.when(MediaMtxProcess::getPort).thenReturn(up ? 8554 : 0);
		mediamtx.when(() -> MediaMtxProcess.isListening(anyInt())).thenReturn(up);
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
