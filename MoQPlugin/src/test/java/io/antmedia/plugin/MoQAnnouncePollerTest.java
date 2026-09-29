package io.antmedia.plugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static io.antmedia.plugin.TestReflect.*;

import com.sun.net.httpserver.HttpServer;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public class MoQAnnouncePollerTest {

    private static final String LOCAL_RELAY = "http://localhost:4443/moq";

    private MoQAnnouncePoller newPoller(String relayUrl, MoQPlugin owner) {
        return new MoQAnnouncePoller(relayUrl, "live", owner, false);
    }

    @Test
    public void testAnnounceUrlDerivedFromRelayUrl() throws Exception {
        MoQPlugin owner = mock(MoQPlugin.class);

        assertEquals("http://localhost:4443/announced/moq/live",
                getField(newPoller(LOCAL_RELAY, owner), "announceUrl"));

        // The scheme is preserved and the relay's own path is dropped
        assertEquals("https://relay.example.com:9000/announced/moq/live",
                getField(newPoller("https://relay.example.com:9000/moq", owner), "announceUrl"));

        // No port in, no :port out
        assertEquals("http://example.com/announced/moq/live",
                getField(newPoller("http://example.com/moq", owner), "announceUrl"));

        try {
            newPoller("not_a_url", owner);
            fail("an unparseable relay URL must not produce a poller pointed at nothing");
        } catch (IllegalArgumentException e) {
            assertTrue("the message has to name the URL that was rejected, got: " + e.getMessage(),
                    e.getMessage().contains("not_a_url"));
        }
    }

    @Test
    public void testStartAndStop() {
        MoQPlugin owner = mock(MoQPlugin.class);
        MoQSettings settings = new MoQSettings();
        settings.setIngestPollIntervalMs(2000);
        when(owner.loadSettings()).thenReturn(settings);
        when(owner.getActiveIngestStreamIds()).thenReturn(setOf("gone"));

        Vertx vertx = mock(Vertx.class);
        ArgumentCaptor<Handler<Long>> tick = ArgumentCaptor.forClass(Handler.class);
        when(vertx.setPeriodic(anyLong(), tick.capture())).thenReturn(42L);
        // Run the blocking body inline, and assert it was queued as un-ordered (false)
        when(vertx.executeBlocking(any(Callable.class), eq(false))).thenAnswer(inv -> {
            ((Callable<?>) inv.getArgument(0)).call();
            return null;
        });

        MoQAnnouncePoller poller = spy(newPoller(LOCAL_RELAY, owner));
        doReturn(setOf()).when(poller).fetchAnnounced();

        // Stop before start: no NPE, no cancel
        poller.stop(vertx);
        verify(vertx, never()).cancelTimer(anyLong());

        poller.start(vertx);
        verify(vertx).setPeriodic(eq(2000L), any());

        // A tick has to reach reconcile(), which sees "gone" active but no longer announced
        tick.getValue().handle(1L);
        verify(owner).stopIngest("gone");

        poller.stop(vertx);
        verify(vertx).cancelTimer(42L);
    }

    @Test
    public void testReconcile() {
        MoQPlugin owner = mock(MoQPlugin.class);
        MoQAnnouncePoller poller = spy(newPoller(LOCAL_RELAY, owner));

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

        assertEquals(setOf("stream1", "stream2", "stream3"),
                MoQAnnouncePoller.parseAnnouncements(new BufferedReader(new StringReader(body))));
        assertTrue(MoQAnnouncePoller.parseAnnouncements(new BufferedReader(new StringReader(""))).isEmpty());
    }

    @Test(timeout = 30_000)
    public void testFetchAnnounced() throws Exception {
        HttpServer ok = startStubRelay(200, "s1/publish\ns2/publish\nnot-a-broadcast\n");
        try {
            assertEquals(setOf("s1", "s2"), newPoller("http://127.0.0.1:" + ok.getAddress().getPort() + "/moq", mock(MoQPlugin.class)).fetchAnnounced());
        } finally {
            ok.stop(0);
        }

        // A relay that does not know the app must not look like an empty announce list to reconcile()
        HttpServer notFound = startStubRelay(404, "nope");
        try {
            assertTrue(newPoller("http://127.0.0.1:" + notFound.getAddress().getPort() + "/moq", mock(MoQPlugin.class)).fetchAnnounced().isEmpty());
        } finally {
            notFound.stop(0);
        }

        // Grab a port and release it, so nothing is listening and connect() is refused.
        // https + trustSelfSignedCerts so the self-signed TLS setup runs before the failed dial.
        int deadPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            deadPort = probe.getLocalPort();
        }
        MoQAnnouncePoller down = new MoQAnnouncePoller(
                "https://127.0.0.1:" + deadPort + "/moq", "live", mock(MoQPlugin.class), true);

        assertTrue("a dead relay must poll to an empty set, not blow up the timer",
                down.fetchAnnounced().isEmpty());
    }

    /** Serves {@code body} with {@code status} on /announced/moq/live. */
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

    private static Set<String> setOf(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }
}
