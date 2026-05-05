package io.antmedia.plugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.vertx.core.Vertx;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

public class MoQAnnouncePollerTest {

    private MoQAnnouncePoller newPoller(String relayUrl, String app, MoQPlugin owner) {
        return new MoQAnnouncePoller(relayUrl, app, owner);
    }

    @Test
    public void testAnnounceUrlFromRelayUrl() throws Exception {
        MoQPlugin owner = mock(MoQPlugin.class);

        MoQAnnouncePoller p1 = newPoller("http://localhost:4443/moq", "live", owner);
        assertEquals("http://localhost:4443/announced/moq/live", getField(p1, "announceUrl"));

        // External relay over https collapses to http, port preserved
        MoQAnnouncePoller p2 = newPoller("https://relay.example.com:9000/moq", "myapp", owner);
        assertEquals("http://relay.example.com:9000/announced/moq/myapp", getField(p2, "announceUrl"));

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
        String body = "stream1/publish\n"
                + "stream2/publish\n"
                + "  stream3/publish  \n"   // trimmed
                + "noSuffix\n"
                + "\n"
                + "other/something\n"
                + "stream1/publish\n";       // duplicate -> Set dedupes

        Set<String> result = MoQAnnouncePoller.parseAnnouncements(new BufferedReader(new StringReader(body)));

        assertEquals(setOf("stream1", "stream2", "stream3"), result);

        Set<String> empty = MoQAnnouncePoller.parseAnnouncements(new BufferedReader(new StringReader("")));
        assertTrue(empty.isEmpty());
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
