package io.antmedia.plugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static io.antmedia.plugin.TestReflect.*;

import io.antmedia.AppSettings;
import io.antmedia.streamsource.StreamFetcher;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.IContext;
import org.red5.server.api.scope.IScope;
import org.springframework.context.ApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MoQStreamFetcherTest {

    private static final String LOCAL_RELAY = "http://localhost:4443/moq";

    private IScope scope;
    private Vertx vertx;

    // Every fetcher binds a real listening port in its constructor. Closing them at the end of
    // each test body only works while nothing above it throws, so it happens here instead.
    private final List<MoQStreamFetcher> fetchers = new ArrayList<>();

    @Before
    public void setUp() {
        // StreamFetcher's constructor walks scope.getContext().getApplicationContext().getBean(AppSettings.BEAN_NAME)
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

    @After
    public void releasePorts() throws Exception {
        for (MoQStreamFetcher fetcher : fetchers) {
            ((ServerSocket) getField(fetcher, "serverSocket")).close();
        }
        fetchers.clear();
    }

    private MoQStreamFetcher newFetcher(String streamId) {
        MoQStreamFetcher fetcher = new MoQStreamFetcher(streamId, "live", LOCAL_RELAY, scope, vertx, true);
        fetchers.add(fetcher);
        return fetcher;
    }

    @Test
    public void testConstructor() throws Exception {
        MoQStreamFetcher fetcher = newFetcher("s1");
        ServerSocket ss = (ServerSocket) getField(fetcher, "serverSocket");

        // Bound on a real port before super() runs, with the parent's streamUrl pointing at it
        assertFalse(ss.isClosed());
        assertEquals("tcp://localhost:" + ss.getLocalPort(), fetcher.getStreamUrl());
        assertNotEquals("each fetcher needs its own port",
                ss.getLocalPort(), ((ServerSocket) getField(newFetcher("s2"), "serverSocket")).getLocalPort());

        // Left behind, the ThreadLocal carrier would hand a stale socket to the next construction
        ThreadLocal<ServerSocket> carrier = staticField(MoQStreamFetcher.class, "socketCarrier");
        assertNull(carrier.get());

        assertFalse("the announce poller owns restart, so the fetcher must not restart itself",
                fetcher.isRestartStream());
        assertNull("no moq process yet", fetcher.getLogStream());
        assertFalse("no worker thread yet", fetcher.isAlive());
    }

    @Test
    public void testStopStream() throws Exception {
        MoQStreamFetcher fetcher = newFetcher("s1");
        ServerSocket ss = (ServerSocket) getField(fetcher, "serverSocket");

        Process moq = mock(Process.class);
        InputStream stderr = new ByteArrayInputStream(new byte[0]);
        when(moq.getErrorStream()).thenReturn(stderr);
        setMoqProcess(fetcher, moq);
        assertSame("getLogStream feeds MoQPlugin's log poll", stderr, fetcher.getLogStream());

        // The relay socket refuses to close (already reset by the peer, say). The server socket is
        // closed after it, so an exception leaking out of here would strand the listening port.
        Socket stuck = mock(Socket.class);
        doThrow(new IOException("connection reset")).when(stuck).close();
        setRelaySocket(fetcher, stuck);

        fetcher.stopStream();

        verify(moq).destroy();
        verify(stuck).close();
        assertTrue("the listening port must still be released", ss.isClosed());
    }

    @Test
    public void testStartStream() throws Exception {
        MoQStreamFetcher bad = spy(newFetcher("s1"));
        doThrow(new IOException("moq not found")).when(bad).spawnMoqCli();

        bad.startStream();

        verify(bad, never()).startRelayThread();
        verify(bad, never()).callSuperStartStream();
        assertNull("a failed spawn must not leave a process behind", getMoqProcess(bad));

        MoQStreamFetcher ok = spy(newFetcher("s2"));
        Process moq = mock(Process.class);
        doReturn(moq).when(ok).spawnMoqCli();
        doNothing().when(ok).startRelayThread();
        doNothing().when(ok).callSuperStartStream();

        ok.startStream();

        assertSame(moq, getMoqProcess(ok));
        verify(ok).startRelayThread();
        verify(ok).callSuperStartStream();
    }

    @Test(timeout = 15_000)
    public void testRunRelay_acceptNeverSucceeds() throws Exception {
        int saved = MoQStreamFetcher.acceptTimeoutMs;
        try {
            MoQStreamFetcher.acceptTimeoutMs = 100; // tight timeout so accept() returns quickly
            MoQStreamFetcher timedOut = spy(newFetcher("s1"));

            timedOut.runRelay(); // nothing connects -> SocketTimeoutException -> stopStream

            verify(timedOut).stopStream();
        } finally {
            MoQStreamFetcher.acceptTimeoutMs = saved;
        }

        // A closed server socket throws SocketException instead, which must be swallowed
        MoQStreamFetcher closed = newFetcher("s2");
        ((ServerSocket) getField(closed, "serverSocket")).close();

        closed.runRelay();

        assertNull("accept() never returned, so no relay socket can exist", getRelaySocket(closed));
    }

    @Test(timeout = 15_000)
    public void testRunRelay_pumpsBytesFromProcessToClient() throws Exception {
        MoQStreamFetcher fetcher = newFetcher("s1");
        ServerSocket ss = (ServerSocket) getField(fetcher, "serverSocket");

        byte[] payload = "fmp4-bytes".getBytes();
        Process moq = mock(Process.class);
        when(moq.getInputStream()).thenReturn(new ByteArrayInputStream(payload));
        setMoqProcess(fetcher, moq);

        // Connect from another thread so accept() returns
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        AtomicReference<Exception> clientFailure = new AtomicReference<>();
        Thread client = new Thread(() -> {
            try (Socket s = new Socket("localhost", ss.getLocalPort())) {
                s.getInputStream().transferTo(received);
            } catch (Exception e) {
                clientFailure.set(e);
            }
        });
        client.start();

        fetcher.runRelay();
        client.join(5000);

        assertNull("the test client failed: " + clientFailure.get(), clientFailure.get());
        assertArrayEquals(payload, received.toByteArray());
    }

    @Test
    public void testBuildMoqCliCommand() {
        List<String> embedded = newFetcher("s1").buildMoqCliCommand();

        assertTrue("relay URL passed through", embedded.contains(LOCAL_RELAY));
        assertTrue("broadcast name has the /publish suffix", embedded.contains("live/s1/publish"));
        assertTrue("the embedded relay is self-signed", embedded.contains("--client-tls-disable-verify"));
        assertEquals("moq binds [::]:0 by default, which dies on hosts with no IPv6 route",
                MoqBinaries.CLIENT_BIND, embedded.get(embedded.indexOf("--client-bind") + 1));
        assertEquals("export is the verb", "export", embedded.get(embedded.size() - 4));
        assertEquals("fmp4 follows export", "fmp4", embedded.get(embedded.size() - 3));
        assertEquals("without a per-frame cap moq only flushes on a video keyframe",
                "0s", embedded.get(embedded.size() - 1));

        MoQStreamFetcher cdn = new MoQStreamFetcher("s2", "live", "https://relay.example.com/moq", scope, vertx, false);
        fetchers.add(cdn);
        List<String> external = cdn.buildMoqCliCommand();

        assertTrue(external.contains("https://relay.example.com/moq"));
        assertFalse("a real certificate must still be verified",
                external.contains("--client-tls-disable-verify"));
    }

    @Test(timeout = 15_000)
    public void testStartRelayThread_runsRelayInBackground() throws Exception {
        MoQStreamFetcher fetcher = spy(newFetcher("s1"));
        CountDownLatch entered = new CountDownLatch(1);
        doAnswer(inv -> { entered.countDown(); return null; }).when(fetcher).runRelay();

        fetcher.startRelayThread();

        assertTrue(entered.await(10, TimeUnit.SECONDS));
    }

    @Test(timeout = 30_000)
    public void testIsAlive_tracksWorkerThread() throws Exception {
        MoQStreamFetcher fetcher = newFetcher("s1");

        // The announce poller restarts an ingest the moment this flips to false, so a
        // present-but-finished thread has to be distinguishable from a running one.
        CountDownLatch release = new CountDownLatch(1);
        StreamFetcher.WorkerThread worker = spy(fetcher.new WorkerThread());
        doAnswer(inv -> { release.await(); return null; }).when(worker).run(); // no real FFmpeg work
        fetcher.setThread(worker);
        worker.start();

        assertTrue("a running worker thread means the ingest is alive", fetcher.isAlive());

        release.countDown();
        worker.join(10_000);
        assertFalse("a finished worker thread must report dead so the poller restarts it", fetcher.isAlive());
    }

    @Test(timeout = 30_000)
    public void testSpawnMoqCli_execsTheBuiltCommand() throws Exception {
        Path dir = Files.createTempDirectory("moq-spawn");
        Path argv = dir.resolve("argv.txt");
        Path bin = MoqTestBinary.write(dir, argv);

        MoQStreamFetcher fetcher = newFetcher("s1");
        Process p = MoqTestBinary.withResolvedMoq(bin, fetcher::spawnMoqCli);
        try {
            assertTrue("spawnMoqCli must hand back a started process", p.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, p.exitValue());

            // What actually reached exec() has to be what buildMoqCliCommand produced
            List<String> cmd = fetcher.buildMoqCliCommand();
            assertEquals(cmd.subList(1, cmd.size()), Files.readAllLines(argv));
        } finally {
            p.destroyForcibly();
        }
    }

    @SuppressWarnings("unchecked")
    private static Process getMoqProcess(MoQStreamFetcher f) throws Exception {
        return ((AtomicReference<Process>) getField(f, "moqProcess")).get();
    }

    @SuppressWarnings("unchecked")
    private static void setMoqProcess(MoQStreamFetcher f, Process p) throws Exception {
        ((AtomicReference<Process>) getField(f, "moqProcess")).set(p);
    }

    @SuppressWarnings("unchecked")
    private static Socket getRelaySocket(MoQStreamFetcher f) throws Exception {
        return ((AtomicReference<Socket>) getField(f, "relaySocket")).get();
    }

    @SuppressWarnings("unchecked")
    private static void setRelaySocket(MoQStreamFetcher f, Socket s) throws Exception {
        ((AtomicReference<Socket>) getField(f, "relaySocket")).set(s);
    }

}
