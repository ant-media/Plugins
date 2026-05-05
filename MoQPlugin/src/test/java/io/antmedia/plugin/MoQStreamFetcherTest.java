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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

        assertNull(fetcher.getLogStream());
        assertFalse(fetcher.isAlive());

        ((ServerSocket) getField(fetcher, "serverSocket")).close();
    }

    @Test
    public void testStopStream() throws Exception {
        MoQStreamFetcher fetcher = newFetcher("s1");
        ServerSocket ss = getField(fetcher, "serverSocket");

        // With a process attached: getLogStream returns its stderr, stopStream destroys it
        Process moq = mock(Process.class);
        InputStream errStream = new ByteArrayInputStream(new byte[0]);
        when(moq.getErrorStream()).thenReturn(errStream);
        setField(fetcher, "moqProcess", moq);
        assertSame(errStream, fetcher.getLogStream());

        fetcher.stopStream();
        verify(moq).destroy();
        assertTrue(ss.isClosed());
    }

    @Test
    public void testStartStream() throws Exception {
        // Spawn fails: early return, no relay thread, no super call
        MoQStreamFetcher bad = spy(newFetcher("s1"));
        doThrow(new IOException("moq-cli not found")).when(bad).spawnMoqCli();

        bad.startStream();
        verify(bad, never()).startRelayThread();
        verify(bad, never()).callSuperStartStream();
        assertNull(getField(bad, "moqProcess"));
        ((ServerSocket) getField(bad, "serverSocket")).close();

        // Spawn succeeds: process stored, relay thread started, super called
        MoQStreamFetcher ok = spy(newFetcher("s2"));
        Process moq = mock(Process.class);
        doReturn(moq).when(ok).spawnMoqCli();
        doNothing().when(ok).startRelayThread();
        doNothing().when(ok).callSuperStartStream();

        ok.startStream();
        assertSame(moq, getField(ok, "moqProcess"));
        verify(ok).startRelayThread();
        verify(ok).callSuperStartStream();
        ((ServerSocket) getField(ok, "serverSocket")).close();
    }

    @Test
    public void testRunRelay_acceptTimeout_callsStopStream() throws Exception {
        int saved = MoQStreamFetcher.acceptTimeoutMs;
        try {
            MoQStreamFetcher.acceptTimeoutMs = 100; // tight timeout so accept() returns quickly
            MoQStreamFetcher fetcher = spy(newFetcher("s1"));

            fetcher.runRelay(); // synchronous; nothing connects -> SocketTimeoutException -> stopStream

            verify(fetcher).stopStream();
        } finally {
            MoQStreamFetcher.acceptTimeoutMs = saved;
        }
    }

    @Test
    public void testRunRelay_pumpsBytesFromProcessToClient() throws Exception {
        MoQStreamFetcher fetcher = newFetcher("s1");
        ServerSocket ss = getField(fetcher, "serverSocket");

        // Mock moq-cli stdout with a fixed payload; ByteArrayInputStream EOFs after drain
        byte[] payload = "fmp4-bytes".getBytes();
        Process moq = mock(Process.class);
        when(moq.getInputStream()).thenReturn(new ByteArrayInputStream(payload));
        setField(fetcher, "moqProcess", moq);

        // Connect a client in a separate thread so accept() returns
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        Thread client = new Thread(() -> {
            try (Socket s = new Socket("localhost", ss.getLocalPort())) {
                s.getInputStream().transferTo(received);
            } catch (Exception ignored) {}
        });
        client.start();

        fetcher.runRelay();
        client.join(2000);

        assertArrayEquals(payload, received.toByteArray());
    }

    @Test
    public void testRunRelay_serverSocketClosed_swallowsIoException() throws Exception {
        MoQStreamFetcher fetcher = newFetcher("s1");
        // Closed server socket -> accept() throws SocketException (an IOException, not SocketTimeoutException)
        ((ServerSocket) getField(fetcher, "serverSocket")).close();

        fetcher.runRelay(); // must not throw
    }

    @Test
    public void testStartRelayThread_runsRelayInBackground() throws Exception {
        MoQStreamFetcher fetcher = spy(newFetcher("s1"));
        CountDownLatch entered = new CountDownLatch(1);
        doAnswer(inv -> { entered.countDown(); return null; }).when(fetcher).runRelay();

        fetcher.startRelayThread();
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        ((ServerSocket) getField(fetcher, "serverSocket")).close();
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

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
