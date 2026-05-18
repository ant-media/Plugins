package io.antmedia.plugin;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

    /** SSLContext that skips certificate validation. Installed only when polling the embedded localhost relay. */
    private static final SSLContext TRUST_ALL_CTX = buildTrustAllContext();

    // Trust-all is only ever applied in fetchAnnounced() when trustSelfSignedCerts == true,
    // which the plugin sets only for the embedded localhost relay (self-signed or hostname-mismatched
    // cert against localhost:4443). For external relays the standard JDK trust store + hostname
    // verification stays in effect — see MoQPlugin.isLocalRelay().
    @SuppressWarnings({"java:S4830", "java:S4426"})
    private static SSLContext buildTrustAllContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{ new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) { /* embedded localhost relay only */ }
                public void checkServerTrusted(X509Certificate[] c, String a) { /* embedded localhost relay only */ }
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, null);
            return ctx;
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final String announceUrl;
    private final MoQPlugin owner;
    private final boolean trustSelfSignedCerts;
    private long timerId = -1;

    /**
     * @param relayBaseUrl          WebTransport relay URL (e.g. {@code https://localhost:4443/moq}).
     *                              The announce endpoint is derived by preserving the scheme and stripping the path.
     * @param appName               AMS application name (e.g. {@code LiveApp})
     * @param owner                 Plugin that owns the ingest handler map
     * @param trustSelfSignedCerts  When true (embedded relay), skip TLS verification on the announce HTTPS poll.
     */
    public MoQAnnouncePoller(String relayBaseUrl, String appName, MoQPlugin owner, boolean trustSelfSignedCerts) {
        // Derive the announce URL from the relay URL, preserving the scheme (http or https).
        // e.g. "https://localhost:4443/moq" → "https://localhost:4443/announced/moq/LiveApp"
        try {
            java.net.URL parsed = new java.net.URL(relayBaseUrl);
            int port = parsed.getPort(); // -1 if absent
            String hostPort = parsed.getHost() + (port > 0 ? ":" + port : "");
            this.announceUrl = parsed.getProtocol() + "://" + hostPort + "/announced/moq/" + appName;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid relay URL: " + relayBaseUrl, e);
        }
        this.owner = owner;
        this.trustSelfSignedCerts = trustSelfSignedCerts;
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

    void reconcile() {
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
    @SuppressWarnings({"java:S5527", "java:S4830"}) // trust-all + permissive hostname verifier are gated on trustSelfSignedCerts (embedded localhost only)
    Set<String> fetchAnnounced() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(announceUrl).openConnection();
            if (trustSelfSignedCerts && conn instanceof HttpsURLConnection) {
                ((HttpsURLConnection) conn).setSSLSocketFactory(TRUST_ALL_CTX.getSocketFactory());
                ((HttpsURLConnection) conn).setHostnameVerifier((h, s) -> true);
            }
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);

            if (conn.getResponseCode() != 200) {
                return Collections.emptySet();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return parseAnnouncements(reader);
            }
        } catch (Exception e) {
            logger.debug("MoQ announce poll failed: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /** Strips the {@code /publish} suffix from each line and collects the stream IDs. */
    static Set<String> parseAnnouncements(BufferedReader reader) {
        return reader.lines()
                .map(String::trim)
                .filter(line -> line.endsWith("/publish"))
                .map(line -> line.substring(0, line.length() - "/publish".length()))
                .collect(Collectors.toCollection(HashSet::new));
    }
}
