package io.antmedia.plugin;

import com.google.gson.Gson;
import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.muxer.MoQMuxer;
import io.antmedia.plugin.api.IStreamListener;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component(value = "plugin.moq")
public class MoQPlugin implements ApplicationContextAware, IStreamListener {

    private static final Logger logger = LoggerFactory.getLogger(MoQPlugin.class);
    private static final long LOG_POLL_INTERVAL_MS = 2000;
    public  static final int  EMBEDDED_RELAY_PORT = 4443;

    private static final Gson gson = new Gson();

    private static volatile Process relayProcess;

    private ApplicationContext applicationContext;
    private AppSettings appSettings;
    private Vertx vertx;
    private final ConcurrentHashMap<String, Set<MoQMuxer>> muxersByStream = new ConcurrentHashMap<>();

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
            logger.info("Not using enbeded relay.");
        }

        logger.info("MoQ plugin initialized for app: {}, relay: {}", app.getScope().getName(), settings.getRelayUrl());
    }

    private static synchronized void startRelay() {
        if (relayProcess != null) {
            // already running — only one relay per JVM
            return;
        }

        try {
            String amsRoot = System.getProperty("red5.root");
            File certFile = new File(amsRoot, "conf/fullchain.pem");
            File keyFile  = new File(amsRoot, "conf/privkey.pem");
            boolean hasCert = certFile.exists() && keyFile.exists();

            ProcessBuilder pb;
            if (hasCert) {
                logger.info("MoQ: found TLS certificate at {}, starting relay with HTTPS/WSS", certFile.getAbsolutePath());
                pb = new ProcessBuilder(
                        "moq-relay",
                        "--server-bind",      "[::]:" + EMBEDDED_RELAY_PORT,
                        "--tls-cert",         certFile.getAbsolutePath(),
                        "--tls-key",          keyFile.getAbsolutePath(),
                        "--web-https-listen", "[::]:" + EMBEDDED_RELAY_PORT,
                        "--web-https-cert",   certFile.getAbsolutePath(),
                        "--web-https-key",    keyFile.getAbsolutePath(),
                        "--web-ws",
                        "--auth-public", "/");
            } else {
                logger.info("MoQ: no TLS certificate found, starting relay with HTTP/WS (localhost only)");
                pb = new ProcessBuilder(
                        "moq-relay",
                        "--server-bind",     "[::]:" + EMBEDDED_RELAY_PORT,
                        "--tls-generate",    "localhost",
                        "--web-http-listen", "[::]:" + EMBEDDED_RELAY_PORT,
                        "--web-ws",
                        "--auth-public", "/");
            }

            relayProcess = pb.redirectErrorStream(true).start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> relayProcess.destroy()));
            logger.info("MoQ: embedded relay started on port {}", EMBEDDED_RELAY_PORT);
        } catch (IOException e) {
            logger.error("MoQ: failed to start embedded relay", e);
        }
    }

    public MoQSettings loadSettings() {
        Object raw = appSettings.getCustomSetting("plugin.moq");
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
        // relay lifecycle is managed by the JVM shutdown hook in startRelay()
    }

    /** Called on the Vert.x timer thread — non-blocking poll of moq-cli stderr for all active streams. */
    private void pollCliLogs() {
        if (relayProcess != null) {
            readAvailable(relayProcess.getInputStream(), "moq-relay");
        }
        muxersByStream.values().forEach(muxers ->
            muxers.forEach(muxer -> readAvailable(muxer.getCliErrorStream(), muxer.getOutputURL()))
        );
    }

    private void readAvailable(InputStream stream, String name) {
        if (stream == null) return;
        try {
            int available = stream.available();
            if (available == 0) return;

            byte[] buf = new byte[Math.min(available, 2000)];
            int n = stream.read(buf, 0, buf.length);
            if (n > 0) {
                logger.info("[moq-cli {}] {}", name, new String(buf, 0, n));
            }
            stream.skip(stream.available()); // discard any overflow
        } catch (IOException ignored) {}
    }

    @Override
    public void streamStarted(String streamId) {
        MuxAdaptor muxAdaptor = getMuxAdaptor(streamId);
        if (muxAdaptor == null) {
            logger.warn("MoQ: no MuxAdaptor for stream {}", streamId);
            return;
        }

        String appName = getApplication().getScope().getName();
        String relayUrl = loadSettings().getRelayUrl();
        Set<MoQMuxer> muxers = ConcurrentHashMap.newKeySet();

        MoQMuxer sourceMuxer = new MoQMuxer(vertx, streamId, 0, appName, relayUrl);
        if (muxAdaptor.addMuxer(sourceMuxer, 0) && muxAdaptor.directMuxingSupported()){
            muxers.add(sourceMuxer);
        }

        if (muxAdaptor.getEncoderSettingsList() != null) {
            for (var encoderSettings : muxAdaptor.getEncoderSettingsList()) {
                MoQMuxer muxer = new MoQMuxer(vertx, streamId, encoderSettings.getHeight(), appName, relayUrl);
                if (muxAdaptor.addMuxer(muxer, encoderSettings.getHeight())){
                    muxers.add(muxer);
                }
            }
        }

        muxersByStream.put(streamId, muxers);
        logger.info("MoQ: {} quality timuxers.add(muxer);muxers.add(muxer);er(s) publishing for stream {}", muxers.size(), streamId);
    }

    @Override
    public void streamFinished(String streamId) {
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

    @Override public void joinedTheRoom(String roomId, String streamId) {}
    @Override public void leftTheRoom(String roomId, String streamId) {}

    private IAntMediaStreamHandler getApplication() {
        return (IAntMediaStreamHandler) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
    }

    private MuxAdaptor getMuxAdaptor(String streamId) {
        IAntMediaStreamHandler app = getApplication();
        return app != null ? app.getMuxAdaptor(streamId) : null;
    }
}