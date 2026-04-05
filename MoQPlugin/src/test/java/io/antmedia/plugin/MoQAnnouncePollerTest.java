package io.antmedia.plugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import io.vertx.core.Vertx;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashSet;

public class MoQAnnouncePollerTest {

    @Test
    public void testConstructor_embeddedRelayUrl_buildsAnnounceUrl() throws Exception {
        MoQPlugin owner = mock(MoQPlugin.class);
        MoQAnnouncePoller poller = new MoQAnnouncePoller("http://localhost:4443/moq", "live", owner);

        String announceUrl = getField(poller, "announceUrl");
        assertEquals("http://localhost:4443/announced/moq/live", announceUrl);
    }

    @Test
    public void testConstructor_externalRelayUrl() throws Exception {
        MoQPlugin owner = mock(MoQPlugin.class);
        MoQAnnouncePoller poller = new MoQAnnouncePoller("https://relay.example.com:9000/moq", "myapp", owner);

        String announceUrl = getField(poller, "announceUrl");
        assertEquals("http://relay.example.com:9000/announced/moq/myapp", announceUrl);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_invalidUrl_throwsIllegalArgument() {
        MoQPlugin owner = mock(MoQPlugin.class);
        new MoQAnnouncePoller("not_a_url", "live", owner);
    }

    @Test
    public void testStop_beforeStart_noException() {
        MoQPlugin owner = mock(MoQPlugin.class);
        Vertx vertx = mock(Vertx.class);
        MoQAnnouncePoller poller = new MoQAnnouncePoller("http://localhost:4443/moq", "live", owner);
        // Should not throw
        poller.stop(vertx);
    }

    @Test
    public void testStart_registersPeriodic() {
        MoQPlugin owner = mock(MoQPlugin.class);
        MoQSettings settings = new MoQSettings();
        settings.setIngestPollIntervalMs(2000);
        when(owner.loadSettings()).thenReturn(settings);
        when(owner.getActiveIngestStreamIds()).thenReturn(new HashSet<>());

        Vertx vertx = mock(Vertx.class);
        when(vertx.setPeriodic(anyLong(), any())).thenReturn(1L);

        MoQAnnouncePoller poller = new MoQAnnouncePoller("http://localhost:4443/moq", "live", owner);
        poller.start(vertx);

        verify(vertx).setPeriodic(eq(2000L), any());
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Object target, String fieldName) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return (T) f.get(target);
    }
}
