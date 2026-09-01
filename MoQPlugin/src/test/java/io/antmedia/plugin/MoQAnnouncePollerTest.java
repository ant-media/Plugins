package io.antmedia.plugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public class MoQAnnouncePollerTest {

    private MoQAnnouncePoller newPoller(String relayUrl, String app, MoQPlugin owner) {
        return new MoQAnnouncePoller(relayUrl, app, owner, false);
    }

    @Test
    public void testAnnounceUrlFromRelayUrl() throws Exception {
        MoQPlugin owner = mock(MoQPlugin.class);

        MoQAnnouncePoller p1 = newPoller("http://localhost:4443/moq", "live", owner);
        assertEquals("http://localhost:4443/announced/moq/live", getField(p1, "announceUrl"));

        // Scheme is preserved on the announce endpoint (https in -> https out).
        MoQAnnouncePoller p2 = newPoller("https://relay.example.com:9000/moq", "myapp", owner);
        assertEquals("https://relay.example.com:9000/announced/moq/myapp", getField(p2, "announceUrl"));

        // No port specified -> URL has no :port suffix
        MoQAnnouncePoller p3 = newPoller("http://example.com/moq", "live", owner);
        assertEquals("http://example.com/announced/moq/live", getField(p3, "announceUrl"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRelayUrl() {
        newPoller("not_a_url", "live", mock(MoQPlugin.class));
    }

    @Test
    public void testStartAndStop() {
        MoQPlugin owner = mock(MoQPlugin.class);
        MoQSettings settings = new MoQSettings();
        settings.setIngestPollIntervalMs(2000);
        when(owner.loadSettings()).thenReturn(settings);
        when(owner.getActiveIngestStreamIds()).thenReturn(new HashSet<>());

        Vertx vertx = mock(Vertx.class);
        when(vertx.setPeriodic(anyLong(), any())).thenReturn(42L);

        MoQAnnouncePoller poller = newPoller("http://localhost:4443/moq", "live", owner);

        // Stop before start: no NPE, no cancel
        poller.stop(vertx);
        verify(vertx, never()).cancelTimer(anyLong());

        // Start: registers periodic
        poller.start(vertx);
        verify(vertx).setPeriodic(eq(2000L), any());

        // Stop after start: cancels the right timer
        poller.stop(vertx);
        verify(vertx).cancelTimer(42L);
    }

    @Test
    public void testReconcile() {
        MoQPlugin owner = mock(MoQPlugin.class);
        MoQAnnouncePoller poller = spy(newPoller("http://localhost:4443/moq", "live", owner));

        // Alive handler matching an announce: nothing happens
        MoQStreamFetcher live = mock(MoQStreamFetcher.class);
        when(live.isAlive()).thenReturn(true);
        when(owner.getIngestHandler("alive")).thenReturn(live);
        doReturn(setOf("alive")).when(poller).fetchAnnounced();
        when(owner.getActiveIngestStreamIds()).thenReturn(setOf("alive"));

        poller.reconcile();
        verify(owner, never()).startIngest(any());
        verify(owner, never()).stopIngest(any());
        verify(live, never()).stopStream();

        // Now: announce a new stream, kill the previously-alive handler (still announced -> restart),
        // and add "old" that is active but no longer announced (-> stop)
        MoQStreamFetcher dead = mock(MoQStreamFetcher.class);
        when(dead.isAlive()).thenReturn(false);
        when(owner.getIngestHandler("alive")).thenReturn(dead);
        when(owner.getIngestHandler("new")).thenReturn(null);
        doReturn(setOf("new", "alive")).when(poller).fetchAnnounced();
        when(owner.getActiveIngestStreamIds()).thenReturn(setOf("alive", "old"));

        poller.reconcile();
        verify(owner).startIngest("new");
        verify(dead).stopStream();
        verify(owner).startIngest("alive");
        verify(owner).stopIngest("old");
        verify(owner, never()).stopIngest("new");
    }

    @Test
    public void testParseAnnouncements() {
        // stream3 is padded (the \s escapes keep the spaces a text block would strip) to prove
        // trimming, noSuffix and other/something are dropped, and stream1 repeats to prove dedupe.
        String body = """
                stream1/publish
                stream2/publish
                \s stream3/publish \s
                noSuffix

                other/something
                stream1/publish
                """;

        Set<String> result = MoQAnnouncePoller.parseAnnouncements(new BufferedReader(new StringReader(body)));

        assertEquals(setOf("stream1", "stream2", "stream3"), result);

        Set<String> empty = MoQAnnouncePoller.parseAnnouncements(new BufferedReader(new StringReader("")));
        assertTrue(empty.isEmpty());
    }

    /** Serves {@code body} with {@code status} on /announced/moq/live and returns the poller pointed at it. */
    private static HttpServer startStubRelay(int status, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/announced/moq/live", exchange -> {
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    public void testFetchAnnounced_okResponse_parsesStreamIds() throws Exception {
        HttpServer server = startStubRelay(200, "s1/publish\ns2/publish\nnot-a-broadcast\n");
        try {
            MoQAnnouncePoller poller = newPoller(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/moq", "live", mock(MoQPlugin.class));

            assertEquals(setOf("s1", "s2"), poller.fetchAnnounced());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testFetchAnnounced_nonOkResponse_returnsEmpty() throws Exception {
        HttpServer server = startStubRelay(404, "nope");
        try {
            MoQAnnouncePoller poller = newPoller(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/moq", "live", mock(MoQPlugin.class));

            assertTrue("a relay that does not know the app must not look like an empty announce list to reconcile()",
                    poller.fetchAnnounced().isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testFetchAnnounced_relayDown_returnsEmptyInsteadOfThrowing() throws Exception {
        // Grab a port and release it, so nothing is listening and connect() is refused
        int deadPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            deadPort = probe.getLocalPort();
        }

        // trustSelfSignedCerts=true + https so the self-signed TLS setup runs before the failed dial
        MoQAnnouncePoller poller = new MoQAnnouncePoller(
                "https://127.0.0.1:" + deadPort + "/moq", "live", mock(MoQPlugin.class), true);

        assertTrue("a dead relay must poll to an empty set, not blow up the timer",
                poller.fetchAnnounced().isEmpty());
    }

    @Test
    public void testStart_periodicTickRunsReconcileOffTheEventLoop() {
        MoQPlugin owner = mock(MoQPlugin.class);
        when(owner.loadSettings()).thenReturn(new MoQSettings());
        when(owner.getActiveIngestStreamIds()).thenReturn(setOf("gone"));

        Vertx vertx = mock(Vertx.class);
        ArgumentCaptor<Handler<Long>> tick = ArgumentCaptor.forClass(Handler.class);
        when(vertx.setPeriodic(anyLong(), tick.capture())).thenReturn(42L);
        // Run the blocking body inline, and assert it was queued as un-ordered (false)
        when(vertx.executeBlocking(any(Callable.class), eq(false))).thenAnswer(inv -> {
            ((Callable<?>) inv.getArgument(0)).call();
            return null;
        });

        MoQAnnouncePoller poller = spy(newPoller("http://localhost:4443/moq", "live", owner));
        doReturn(setOf()).when(poller).fetchAnnounced();
        poller.start(vertx);

        tick.getValue().handle(1L);

        // The tick reached reconcile(), which saw "gone" active but not announced
        verify(poller).reconcile();
        verify(owner).stopIngest("gone");
    }

    private static Set<String> setOf(String... items) {
        Set<String> s = new HashSet<>();
        for (String i : items) s.add(i);
        return s;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }
}
