package io.antmedia.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.junit.Test;
import org.red5.server.api.scope.IScope;
import org.springframework.context.ApplicationContext;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.enterprise.adaptive.EncoderAdaptor;
import io.antmedia.rest.model.Result;
import io.antmedia.settings.ServerSettings;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.muxer.Muxer;
import io.vertx.core.Vertx;

public class MutedStreamReplicatorPluginUnitTest {

	@Test
	public void testSetApplicationContextRegistersStreamListener() {
		ApplicationContext context = mock(ApplicationContext.class);
		AntMediaApplicationAdapter appAdapter = mock(AntMediaApplicationAdapter.class);
		MutedStreamReplicatorPlugin plugin = new MutedStreamReplicatorPlugin();

		when(context.getBean(AntMediaApplicationAdapter.BEAN_NAME)).thenReturn(appAdapter);

		plugin.setApplicationContext(context);

		verify(appAdapter).addStreamListener(plugin);
	}

	@Test
	public void testGetMuxAdaptorReturnsNullWhenApplicationIsMissing() {
		MutedStreamReplicatorPlugin plugin = new MutedStreamReplicatorPlugin();
		assertTrue(plugin.getMuxAdaptor("stream1") == null);
	}

	@Test
	public void testSourceStreamStartedStartsReplicaEndpoint() throws Exception {
		MutedStreamReplicatorPlugin plugin = spy(new MutedStreamReplicatorPlugin());
		EncoderAdaptor sourceAdaptor = mock(EncoderAdaptor.class);
		AntMediaApplicationAdapter appAdapter = mock(AntMediaApplicationAdapter.class);
		AppSettings appSettings = mock(AppSettings.class);
		ServerSettings serverSettings = mock(ServerSettings.class);
		IScope scope = mock(IScope.class);
		Broadcast broadcast = new Broadcast();
		broadcast.setStreamId("stream1");

		when(serverSettings.getRtmpPort()).thenReturn(1935);
		when(scope.getName()).thenReturn("LiveApp");
		when(appAdapter.getServerSettings()).thenReturn(serverSettings);
		when(appAdapter.getScope()).thenReturn(scope);
		when(appAdapter.getAppSettings()).thenReturn(appSettings);
		when(appSettings.getCustomSetting(any())).thenReturn(null);
		when(sourceAdaptor.startEndpointStreaming(any(String.class), eq(0))).thenReturn(new Result(true));
		setField(plugin, "appAdapter", appAdapter);
		doReturn(sourceAdaptor).when(plugin).getMuxAdaptor("stream1");
		doReturn(appAdapter).when(plugin).getApplication();

		plugin.streamStarted(broadcast);

		verify(sourceAdaptor).startEndpointStreaming("rtmp://127.0.0.1:1935/LiveApp/stream1-muted", 0);
	}

	@Test
	public void testMutedReplicaStartedCreatesManagerAndStopsOnFinish() throws Exception {
		MutedStreamReplicatorPlugin plugin = spy(new MutedStreamReplicatorPlugin());
		EncoderAdaptor sourceAdaptor = mock(EncoderAdaptor.class);
		EncoderAdaptor targetAdaptor = mock(EncoderAdaptor.class);
		AntMediaApplicationAdapter appAdapter = mock(AntMediaApplicationAdapter.class);
		AppSettings appSettings = mock(AppSettings.class);
		Vertx vertx = mock(Vertx.class);
		Broadcast broadcast = new Broadcast();
		Broadcast finished = new Broadcast();
		broadcast.setStreamId("stream1-muted");
		finished.setStreamId("stream1-muted");

		when(appAdapter.getAppSettings()).thenReturn(appSettings);
		when(appSettings.getCustomSetting(any())).thenReturn(null);
		when(sourceAdaptor.addMuxer(any())).thenReturn(true);
		when(targetAdaptor.getMuxerList()).thenReturn(Arrays.asList(mock(Muxer.class)));
		when(sourceAdaptor.getStreamAdaptorList()).thenReturn(new LinkedList<>());
		when(targetAdaptor.getStreamAdaptorList()).thenReturn(new LinkedList<>());
		when(sourceAdaptor.getStreamId()).thenReturn("stream1");
		when(targetAdaptor.getStreamId()).thenReturn("stream1-muted");
		when(targetAdaptor.getVertx()).thenReturn(vertx);
		when(vertx.setPeriodic(any(Long.class), any())).thenReturn(11L);
		doReturn(sourceAdaptor).when(plugin).getMuxAdaptor("stream1");
		doReturn(targetAdaptor).when(plugin).getMuxAdaptor("stream1-muted");
		doReturn(appAdapter).when(plugin).getApplication();

		plugin.streamStarted(broadcast);

		verify(targetAdaptor).setBufferTimeMs(500);
		verify(vertx).setPeriodic(any(Long.class), any());
		assertEquals(1, getManagers(plugin).size());

		plugin.streamFinished(finished);

		verify(vertx).cancelTimer(11L);
		verify(sourceAdaptor).removeMuxer(any());
		assertEquals(0, getManagers(plugin).size());
	}

	@Test
	public void testMutedReplicaStartedIgnoresNonEncoderAdaptors() throws Exception {
		MutedStreamReplicatorPlugin plugin = spy(new MutedStreamReplicatorPlugin());
		AntMediaApplicationAdapter appAdapter = mock(AntMediaApplicationAdapter.class);
		AppSettings appSettings = mock(AppSettings.class);
		Broadcast broadcast = new Broadcast();
		broadcast.setStreamId("stream1-muted");

		when(appAdapter.getAppSettings()).thenReturn(appSettings);
		when(appSettings.getCustomSetting(any())).thenReturn(null);
		doReturn(mock(MuxAdaptor.class)).when(plugin).getMuxAdaptor("stream1");
		doReturn(mock(MuxAdaptor.class)).when(plugin).getMuxAdaptor("stream1-muted");
		doReturn(appAdapter).when(plugin).getApplication();

		plugin.streamStarted(broadcast);

		assertEquals(0, getManagers(plugin).size());
	}

	@Test
	public void testInvalidBroadcastsAreIgnored() throws Exception {
		MutedStreamReplicatorPlugin plugin = spy(new MutedStreamReplicatorPlugin());
		Broadcast broadcast = new Broadcast();
		broadcast.setStreamId("  ");

		plugin.streamStarted((Broadcast) null);
		plugin.streamStarted(broadcast);
		plugin.streamFinished((Broadcast) null);
		plugin.streamFinished(broadcast);

		verify(plugin, never()).getMuxAdaptor(any());
	}

	@SuppressWarnings("unchecked")
	private Map<String, ?> getManagers(MutedStreamReplicatorPlugin plugin) {
		try {
			Field field = MutedStreamReplicatorPlugin.class.getDeclaredField("mutedStreamManagers");
			field.setAccessible(true);
			return (Map<String, ?>) field.get(plugin);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void setField(Object target, String fieldName, Object value) {
		try {
			Field field = findField(target.getClass(), fieldName);
			field.setAccessible(true);
			field.set(target, value);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
		Class<?> current = type;
		while (current != null) {
			try {
				return current.getDeclaredField(fieldName);
			}
			catch (NoSuchFieldException e) {
				current = current.getSuperclass();
			}
		}
		throw new NoSuchFieldException(fieldName);
	}
}
