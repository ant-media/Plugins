package io.antmedia.plugin;

import com.google.gson.Gson;
import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.muxer.MoQMuxer;
import io.antmedia.plugin.api.IStreamListener;
import org.red5.server.api.scope.IScope;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.datastore.db.types.Broadcast;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Component(value = "moqPlugin")
public class MoQPlugin implements ApplicationContextAware, IStreamListener {

    private static final Logger logger = LoggerFactory.getLogger(MoQPlugin.class);
    private static final long LOG_POLL_INTERVAL_MS = 2000;
    private static final long RELAY_RESTART_GRACE_MS = 5000;
    public  static final int EMBEDDED_RELAY_PORT = 4443;
    public  static final String PUBLISH_TYPE_MOQ = "MoQ";
    public  static final String SETTINGS_KEY     = "plugin.moq";

    private static final String MOQ_RELAY_BIN = "moq-relay";
    private static final String BIND_ADDR     = "[::]:";

    private static final Gson gson = new Gson();

    private static final AtomicReference<Process> relayProcess = new AtomicReference<>();
    private static volatile long lastRelayRestartAttempt = 0;
    private static volatile boolean shutdownHookRegistered = false;

    private ApplicationContext applicationContext;
    private AppSettings appSettings;
    private Vertx vertx;
    private final ConcurrentHashMap<String, Set<MoQMuxer>> muxersByStream = new ConcurrentHashMap<>();

    // Ingest: external MoQ publishers → AMS
    private MoQAnnouncePoller announcePoller;
    private final ConcurrentMap<String, MoQStreamFetcher> activeIngests = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        this.applicationContext = ctx;
        this.vertx = (Vertx) ctx.getBean(IAntMediaStreamHandler.VERTX_BEAN_NAME);

        IAntMediaStreamHandler app = getApplication();
        appSettings = app.getAppSettings();

        app.addStreamListener(this);

        vertx.setPeriodic(LOG_POLL_INTERVAL_MS, l -> pollCliLogs());

        MoQSettings settings = loadSettings();

        if (settings.isUseEmbeddedRelay()) {
            startRelay();
        } else {
            logger.info("Not using embedded relay.");
        }

        String relayUrl = getRelayUrl(settings);
        if (settings.isIngestEnabled()) {
            announcePoller = new MoQAnnouncePoller(relayUrl, app.getScope().getName(), this, settings.isUseEmbeddedRelay());
            announcePoller.start(vertx);
        }

        logger.info("MoQ plugin initialized for app: {}, relay: {}", app.getScope().getName(), relayUrl);
    }

    private static synchronized void startRelay() {
        if (relayProcess.get() != null) {
            // already running — only one relay per JVM
            return;
        }
        try {
            Process p = buildRelayProcessBuilder().redirectErrorStream(true).start();
            relayProcess.set(p);
            if (!shutdownHookRegistered) {
                // resolves current process at shutdown
                Runtime.getRuntime().addShutdownHook(new Thread(MoQPlugin::destroyRelayOnShutdown));
                shutdownHookRegistered = true;
            }
            logger.info("MoQ: embedded relay started on port {}", EMBEDDED_RELAY_PORT);
        } catch (IOException e) {
            logger.error("MoQ: failed to start embedded relay", e);
        }
    }

    static void destroyRelayOnShutdown() {
        Process curr = relayProcess.get();
        if (curr != null) {
            curr.destroy();
        }
    }

    static void maybeRestartRelay(Process relay) {
        if (relay.isAlive()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRelayRestartAttempt < RELAY_RESTART_GRACE_MS) {
            return;
        }

        lastRelayRestartAttempt = now;

        logger.warn("MoQ: embedded relay exited (code {}), restarting", relay.exitValue());
        relayProcess.compareAndSet(relay, null);
        startRelay();
    }

    /** True when both AMS TLS cert files are present on disk.
     *  embedded relay will run HTTPS/WSS. */
    static boolean embeddedRelayHasTls() {
        return certFile().exists() && keyFile().exists();
    }

    private static File certFile() {
        String amsRoot = System.getProperty("red5.root");
        return new File(amsRoot != null ? amsRoot : ".", "conf/fullchain.pem");
    }

    private static File keyFile() {
        String amsRoot = System.getProperty("red5.root");
        return new File(amsRoot != null ? amsRoot : ".", "conf/privkey.pem");
    }

    /** Picks the moq-relay command line: with TLS cert files if both exist, otherwise self-signed for localhost. */
    static ProcessBuilder buildRelayProcessBuilder() {
        String bind = BIND_ADDR + EMBEDDED_RELAY_PORT;

        if (embeddedRelayHasTls()) {
            File cert = certFile();
            File key  = keyFile();
            logger.info("MoQ: found TLS certificate at {}, starting relay with HTTPS/WSS", cert.getAbsolutePath());
            return new ProcessBuilder(
                    MoqBinaries.resolve(MOQ_RELAY_BIN),
                    "--server-bind",      bind,
                    "--tls-cert",         cert.getAbsolutePath(),
                    "--tls-key",          key.getAbsolutePath(),
                    "--web-https-listen", bind,
                    "--web-https-cert",   cert.getAbsolutePath(),
                    "--web-https-key",    key.getAbsolutePath(),
                    "--web-ws",
                    "--auth-public", "/");
        }

        logger.info("MoQ: no TLS certificate found, starting relay with HTTP/WS (localhost only)");
        return new ProcessBuilder(
                MoqBinaries.resolve(MOQ_RELAY_BIN),
                "--server-bind",     bind,
                "--tls-generate",    "localhost",
                "--web-http-listen", bind,
                "--web-ws",
                "--auth-public", "/");
    }

    /**
     * Resolves the relay URL based on the given settings + runtime relay state.
     *
     * <p>For embedded relay, the scheme tracks the cert files on disk: {@code https://} when
     * {@link #embeddedRelayHasTls()} (matches what {@link #buildRelayProcessBuilder()} does),
     * otherwise {@code http://}. For external relay, returns the configured URL verbatim.
     */
    String getRelayUrl(MoQSettings s) {
        if (!s.isUseEmbeddedRelay()) {
            return s.getExternalRelayUrl();
        }
        String scheme = embeddedRelayHasTls() ? "https" : "http";
        return scheme + "://localhost:" + EMBEDDED_RELAY_PORT + "/moq";
    }

    /** Convenience overload that loads settings — prefer {@link #getRelayUrl(MoQSettings)} when settings are already loaded. */
    public String getRelayUrl() {
        return getRelayUrl(loadSettings());
    }

    /** True when current settings point at the embedded localhost relay (so self-signed TLS is expected). */
    public boolean isLocalRelay() {
        return loadSettings().isUseEmbeddedRelay();
    }

    public MoQSettings loadSettings() {
        Object raw = appSettings.getCustomSetting(SETTINGS_KEY);
        if (raw != null) {
            try {
                return gson.fromJson(raw.toString(), MoQSettings.class);
            } catch (Exception e) {
                logger.error("Invalid MoQ settings, using defaults: {}", e.getMessage());
            }
        }
        return new MoQSettings();
    }

    @PreDestroy
    public void destroy() {
        if (announcePoller != null) {
            announcePoller.stop(vertx);
        }
        activeIngests.values().forEach(MoQStreamFetcher::stopStream);
        activeIngests.clear();
        // relay process lifecycle is managed by the JVM shutdown hook in startRelay()
    }

    /** Called on the Vert.x timer thread — non-blocking poll of moq-cli stderr for all active streams. */
    private void pollCliLogs() {
        Process relay = relayProcess.get();
        if (relay != null) {
            readAvailable(relay.getInputStream(), MOQ_RELAY_BIN);
            maybeRestartRelay(relay);
        }
        muxersByStream.values().forEach(muxers ->
            muxers.forEach(muxer -> readAvailable(muxer.getCliErrorStream(), muxer.getOutputURL()))
        );
        activeIngests.forEach((streamId, handler) ->
            readAvailable(handler.getLogStream(), "ingest/" + streamId)
        );
    }

    private void readAvailable(InputStream stream, String name) {
        if (stream == null) return;
        try {
            int available = stream.available();
            if (available == 0) return;

            byte[] buf = new byte[Math.min(available, 2000)];
            int n = stream.read(buf, 0, buf.length);
            if (n > 0 && logger.isInfoEnabled()) {
                logger.info("[moq-cli {}] {}", name, new String(buf, 0, n));
            }
            // Discard any overflow so the buffer doesn't keep growing across polls.
            long remaining = stream.available();
            while (remaining > 0) {
                long s = stream.skip(remaining);
                if (s <= 0) break;
                remaining -= s;
            }
        } catch (IOException e) {
            // poll cycle is best-effort; ignore IO failures and try next time
        }
    }

    @Override
    public void streamStarted(Broadcast broadcast) {
        String streamId = broadcast.getStreamId();
        MuxAdaptor muxAdaptor = getMuxAdaptor(streamId);
        if (muxAdaptor == null) {
            logger.warn("MoQ: no MuxAdaptor for stream {}", streamId);
            return;
        }

        String appName = getApplication().getScope().getName();
        MoQSettings settings = loadSettings();
        String relayUrl = getRelayUrl(settings);
        boolean tlsDisableVerify = settings.isUseEmbeddedRelay();
        Set<MoQMuxer> muxers = ConcurrentHashMap.newKeySet();

        // For direct-muxing (RTMP/SRT), height=0 means "match any resolution".
        // For WebRTC (directMuxingSupported=false), the EncoderAdaptor fallback in addMuxer()
        // must match the muxer to a forwarder/encoder by exact height=0 never matches,
        // so the muxer gets no data. Use the actual source height in that case.
        int sourceAddHeight = 0;
        if (!muxAdaptor.directMuxingSupported() && muxAdaptor.getVideoCodecParameters() != null) {
            sourceAddHeight = muxAdaptor.getVideoCodecParameters().height();
        }
        MoQMuxer sourceMuxer = new MoQMuxer(vertx, streamId, 0, appName, relayUrl, tlsDisableVerify);
        if (muxAdaptor.addMuxer(sourceMuxer, sourceAddHeight)) {
            muxers.add(sourceMuxer);
        }

        if (muxAdaptor.getEncoderSettingsList() != null) {
            for (var encoderSettings : muxAdaptor.getEncoderSettingsList()) {
                MoQMuxer muxer = new MoQMuxer(vertx, streamId, encoderSettings.getHeight(), appName, relayUrl, tlsDisableVerify);
                if (muxAdaptor.addMuxer(muxer, encoderSettings.getHeight())){
                    muxers.add(muxer);
                }
            }
        }

        muxersByStream.put(streamId, muxers);
        logger.info("MoQ: {} quality muxer(s) publishing for stream {}", muxers.size(), streamId);
    }

    @Override
    public void streamFinished(Broadcast broadcast) {
        String streamId = broadcast.getStreamId();
        MuxAdaptor muxAdaptor = getMuxAdaptor(streamId);
        Set<MoQMuxer> muxers = muxersByStream.remove(streamId);
        if (muxers == null) {
            return;
        }

        for (MoQMuxer muxer : muxers) {
            if (muxAdaptor != null){
                muxAdaptor.removeMuxer(muxer);
            }
        }
    }

    @Override public void joinedTheRoom(String roomId, String streamId) { /* MoQ has no room concept */ }
    @Override public void leftTheRoom(String roomId, String streamId)   { /* MoQ has no room concept */ }

    Set<String> getActiveIngestStreamIds() {
        return activeIngests.keySet();
    }

    MoQStreamFetcher getIngestHandler(String streamId) {
        return activeIngests.get(streamId);
    }

    void startIngest(String streamId) {
        IAntMediaStreamHandler app = getApplication();

        // StreamFetcher.WorkerThread requires a Broadcast record in the DB — create one if
        // the stream was never registered manually. Type "liveStream" makes it appear in the
        // management console alongside RTMP/WebRTC streams, not under stream sources.
        if (app.getDataStore().get(streamId) == null) {
            if (app.getAppSettings().isAcceptOnlyStreamsInDataStore()) {
                logger.info("MoQ stream can not be ingested! Stream {} not defined, but only predifined streams are allowed", streamId);
                return;    
            }

            Broadcast b = AntMediaApplicationAdapter.createZombiBroadcast(
                    streamId, streamId,
                    IAntMediaStreamHandler.BROADCAST_STATUS_CREATED,
                    PUBLISH_TYPE_MOQ,
                    "", "{}", "");
            b.setType(AntMediaApplicationAdapter.LIVE_STREAM);
            app.getDataStore().save(b);
        }

        MoQStreamFetcher fetcher;
        try {
            fetcher = createFetcher(streamId, app.getScope().getName(), getRelayUrl(), app.getScope());
        } catch (RuntimeException e) {
            logger.error("MoQ: cannot create stream fetcher for stream {}", streamId, e);
            return;
        }
        fetcher.setStartStreamForce(true);
        activeIngests.put(streamId, fetcher);
        fetcher.startStream();
    }

    void stopIngest(String streamId) {
        MoQStreamFetcher fetcher = activeIngests.remove(streamId);
        if (fetcher != null) {
            fetcher.stopStream();
        }
    }

    protected MoQStreamFetcher createFetcher(String streamId, String appName, String relayUrl, IScope scope) {
        return new MoQStreamFetcher(streamId, appName, relayUrl, scope, vertx, isLocalRelay());
    }

    private IAntMediaStreamHandler getApplication() {
        return (IAntMediaStreamHandler) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
    }

    private MuxAdaptor getMuxAdaptor(String streamId) {
        return getApplication().getMuxAdaptor(streamId);
    }
}