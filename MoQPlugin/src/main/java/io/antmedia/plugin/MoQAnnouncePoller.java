package io.antmedia.plugin;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Polls the MoQ relay's {@code /announced/moq/{appName}} endpoint every N milliseconds
 * and reconciles active ingest handlers in {@link MoQPlugin}.
 *
 * <p>Reconcile logic (runs on every poll):
 * <ul>
 *   <li>Announced + no handler / dead handler → start (or restart) ingest</li>
 *   <li>Not announced + live handler          → stop ingest</li>
 * </ul>
 */
public class MoQAnnouncePoller {

    private static final Logger logger = LoggerFactory.getLogger(MoQAnnouncePoller.class);

    private final String announceUrl;
    private final MoQPlugin owner;
    private long timerId = -1;

    /**
     * @param relayBaseUrl  WebTransport relay URL (e.g. {@code https://localhost:4443/moq}).
     *                      The HTTP announce endpoint is derived by forcing HTTP and stripping the path.
     * @param appName       AMS application name (e.g. {@code LiveApp})
     * @param owner         Plugin that owns the ingest handler map
     */
    public MoQAnnouncePoller(String relayBaseUrl, String appName, MoQPlugin owner) {
        // Derive plain HTTP announce URL from the relay URL (which may be https/wss).
        // e.g. "https://localhost:4443/moq" → "http://localhost:4443/announced/moq/LiveApp"
        try {
            java.net.URL parsed = new java.net.URL(relayBaseUrl);
            int port = parsed.getPort(); // -1 if absent
            String hostPort = parsed.getHost() + (port > 0 ? ":" + port : "");
            this.announceUrl = "http://" + hostPort + "/announced/moq/" + appName;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid relay URL: " + relayBaseUrl, e);
        }
        this.owner = owner;
    }

    public void start(Vertx vertx) {
        int intervalMs = owner.loadSettings().getIngestPollIntervalMs();
        timerId = vertx.setPeriodic(intervalMs, id ->
            vertx.executeBlocking(() -> { reconcile(); return null; }, false)
        );
        logger.info("MoQ announce poller started: {} (every {}ms)", announceUrl, intervalMs);
    }

    public void stop(Vertx vertx) {
        if (timerId >= 0) {
            vertx.cancelTimer(timerId);
            timerId = -1;
        }
    }

    private void reconcile() {
        Set<String> announced = fetchAnnounced();
        Set<String> active    = owner.getActiveIngestStreamIds();

        for (String streamId : announced) {
            MoQStreamFetcher handler = owner.getIngestHandler(streamId);
            if (handler == null || !handler.isAlive()) {
                if (handler != null) {
                    logger.info("MoQ: ingest for {} died, restarting", streamId);
                    handler.stopStream();
                }

                logger.info("Starting injest of stream {}", streamId);
                owner.startIngest(streamId);
            }
        }

        for (String streamId : active) {
            if (!announced.contains(streamId)) {
                logger.info("MoQ: stream {} no longer announced, stopping ingest", streamId);
                owner.stopIngest(streamId);
            }
        }
    }

    /** GET the announce endpoint and return the set of stream IDs with a {@code /publish} suffix. */
    private Set<String> fetchAnnounced() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(announceUrl).openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);

            if (conn.getResponseCode() != 200) {
                return Collections.emptySet();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> line.endsWith("/publish"))
                        .map(line -> line.substring(0, line.length() - "/publish".length()))
                        .collect(Collectors.toCollection(HashSet::new));
            }
        } catch (Exception e) {
            logger.debug("MoQ announce poll failed: {}", e.getMessage());
            return Collections.emptySet();
        }
    }
}
