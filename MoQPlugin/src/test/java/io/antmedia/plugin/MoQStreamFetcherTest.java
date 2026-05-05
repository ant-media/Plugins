package io.antmedia.plugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.antmedia.AppSettings;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.IContext;
import org.red5.server.api.scope.IScope;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.net.ServerSocket;

public class MoQStreamFetcherTest {

    private IScope scope;
    private Vertx vertx;

    @Before
    public void setUp() {
        // StreamFetcher constructor walks scope.getContext().getApplicationContext().getBean(AppSettings.BEAN_NAME)
        AppSettings appSettings = mock(AppSettings.class);
        when(appSettings.getStreamFetcherBufferTime()).thenReturn(0);

        ApplicationContext appCtx = mock(ApplicationContext.class);
        when(appCtx.getBean(AppSettings.BEAN_NAME)).thenReturn(appSettings);

        IContext red5Ctx = mock(IContext.class);
        when(red5Ctx.getApplicationContext()).thenReturn(appCtx);

        scope = mock(IScope.class);
        when(scope.getContext()).thenReturn(red5Ctx);

        vertx = mock(Vertx.class);
    }

    private MoQStreamFetcher newFetcher(String streamId) {
        return new MoQStreamFetcher(streamId, "live", "http://localhost:4443/moq", scope, vertx);
    }

    @Test
    public void testConstructor() throws Exception {
        MoQStreamFetcher fetcher = newFetcher("s1");

        // Binds a ServerSocket on a real port, parent's streamUrl points at it
        ServerSocket ss = getField(fetcher, "serverSocket");
        assertNotNull(ss);
        assertFalse(ss.isClosed());
        assertTrue(ss.getLocalPort() > 0);
        assertEquals("tcp://localhost:" + ss.getLocalPort(), fetcher.getStreamUrl());

        // Each fetcher gets its own port
        MoQStreamFetcher other = newFetcher("s2");
        ServerSocket otherSs = getField(other, "serverSocket");
        assertNotEquals(ss.getLocalPort(), otherSs.getLocalPort());

        // ThreadLocal carrier is cleared so it does not leak across constructions
        Field tlField = MoQStreamFetcher.class.getDeclaredField("socketCarrier");
        tlField.setAccessible(true);
        ThreadLocal<?> tl = (ThreadLocal<?>) tlField.get(null);
        assertNull(tl.get());

        ss.close();
        otherSs.close();
    }

    @Test
    public void testInitialState_noProcess_noThread() throws Exception {
        MoQStreamFetcher fetcher = newFetcher("s1");

        // moq-cli not started yet -> no log stream
        assertNull(fetcher.getLogStream());
        // WorkerThread not started yet -> not alive
        assertFalse(fetcher.isAlive());

        ((ServerSocket) getField(fetcher, "serverSocket")).close();
    }

    @Test
    public void testStopStream_closesServerSocket() throws Exception {
        MoQStreamFetcher fetcher = newFetcher("s1");
        ServerSocket ss = getField(fetcher, "serverSocket");
        assertFalse(ss.isClosed());

        fetcher.stopStream();
        assertTrue(ss.isClosed());
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return (T) f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
