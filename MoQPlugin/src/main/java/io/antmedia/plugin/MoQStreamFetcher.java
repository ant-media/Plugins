package io.antmedia.plugin;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.streamsource.StreamFetcher;
import io.vertx.core.Vertx;
import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pulls an external MoQ broadcast into AMS by spawning {@code moq ... export fmp4}
 * and relaying its fMP4 output over a local TCP socket that FFmpeg (StreamFetcher) reads.
 *
 * <p>Data flow:
 * <pre>
 *   moq stdout
 *       │  (Java pipe)
 *       ▼
 *   relay thread  ──TCP──►  FFmpeg (avformat_open_input)  ──►  MuxAdaptor  ──►  AMS
 * </pre>
 *
 * <p>The socket is bound in the constructor so the relay thread is already in {@code accept()}
 * by the time FFmpeg dials in. Shutdown runs the other way: killing moq ends {@code transferTo},
 * the socket closes, and FFmpeg sees a clean EOF.
 *
 * <p>moq stderr is available via {@link #getLogStream()} and polled by
 * {@link MoQPlugin#pollCliLogs()}.
 */
public class MoQStreamFetcher extends StreamFetcher {

    private static final Logger logger = LoggerFactory.getLogger(MoQStreamFetcher.class);
    static int acceptTimeoutMs = 10000;

    // ThreadLocal carries the ServerSocket through the super() call so we can
    // create it before super() (to get the port) while still calling super() first.
    private static final ThreadLocal<ServerSocket> socketCarrier = new ThreadLocal<>();

    private final String streamId;
    private final String broadcastName;
    private final String relayUrl;
    private final boolean tlsDisableVerify;
    private final ServerSocket serverSocket;

    private final AtomicReference<Process> moqProcess  = new AtomicReference<>();
    private final AtomicReference<Socket>  relaySocket = new AtomicReference<>();

    public MoQStreamFetcher(String streamId, String appName, String relayUrl, IScope scope, Vertx vertx, boolean tlsDisableVerify) {
        super(allocateUrl(), streamId, AntMediaApplicationAdapter.LIVE_STREAM, scope, vertx, 0);
        this.serverSocket = socketCarrier.get();
        socketCarrier.remove();

        this.streamId = streamId;
        this.broadcastName = appName + "/" + streamId + "/publish";
        this.relayUrl = relayUrl;
        this.tlsDisableVerify = tlsDisableVerify;

        setRestartStream(false); // poller owns restart logic
    }

    /**
     * Creates a ServerSocket on an OS-assigned port, stores it in the ThreadLocal so the
     * constructor body can retrieve it, and returns the matching {@code tcp://localhost:PORT} URL
     * for StreamFetcher.
     */
    private static String allocateUrl() {
        try {
            ServerSocket ss = new ServerSocket(0);
            socketCarrier.set(ss);
            return "tcp://localhost:" + ss.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("MoQ: failed to bind local TCP port for ingest", e);
        }
    }

    /** True if the StreamFetcher WorkerThread is still alive. Used by the poller. */
    public boolean isAlive() {
        return getThread() != null && getThread().isAlive();
    }

    /** moq-cli stderr — polled by MoQPlugin for log output. */
    public InputStream getLogStream() {
        Process p = moqProcess.get();
        return p != null ? p.getErrorStream() : null;
    }

    @Override
    public void startStream() {
        try {
            moqProcess.set(spawnMoqCli());
        } catch (IOException e) {
            logger.error("MoQ: failed to spawn moq for stream {}", streamId, e);
            return;
        }

        startRelayThread();

        // WorkerThread spawned here → calls avformat_open_input("tcp://localhost:PORT").
        // serverSocket is already listening, so the connection completes immediately.
        callSuperStartStream();
    }

    protected Process spawnMoqCli() throws IOException {
        Process p = new ProcessBuilder(buildMoqCliCommand()).start();
        logger.info("MoQ: moq export started for {}", broadcastName);
        return p;
    }

    /** Builds the moq export command; the {@code --client-tls-disable-verify} flag is added only for the embedded localhost relay. */
    List<String> buildMoqCliCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add(MoqBinaries.resolve("moq"));
        cmd.add("--client-connect");
        cmd.add(relayUrl);
        cmd.add("--client-bind");
        cmd.add(MoqBinaries.CLIENT_BIND);
        if (tlsDisableVerify) cmd.add("--client-tls-disable-verify");
        cmd.add("--broadcast");
        cmd.add(broadcastName);
        cmd.add("export");
        cmd.add("fmp4");
        // Without a cap moq only flushes on a video keyframe, so the audio track buffers
        // forever and never reaches us. 0s means one fragment per frame on every track.
        cmd.add("--fragment-duration");
        cmd.add("0s");
        return cmd;
    }

    protected void callSuperStartStream() {
        super.startStream();
    }

    @Override
    public void stopStream() {
        // 1. Kill moq → transferTo() in relay thread gets IOException/EOF → relay exits.
        Process p = moqProcess.get();
        if (p != null) {
            p.destroy();
        }
        // 2. Close relay socket → unblocks FFmpeg's av_read_frame with EOF.
        closeQuietly(relaySocket.get());
        // 3. Close server socket → unblocks accept() if relay thread is still waiting.
        closeQuietly(serverSocket);

        super.stopStream();
    }

    protected void startRelayThread() {
        Thread relay = new Thread(this::runRelay, "moq-relay-" + streamId);
        relay.setDaemon(true);
        relay.start();
    }

    /** Body of the relay thread, factored out so it can be invoked synchronously in tests. */
    void runRelay() {
        try {
            serverSocket.setSoTimeout(acceptTimeoutMs);
            Socket s = serverSocket.accept();
            s.setTcpNoDelay(true);
            relaySocket.set(s);
            logger.info("MoQ: relay connected for stream {}", streamId);

            // Pump moq stdout → FFmpeg. Blocks until moq exits or socket closes.
            moqProcess.get().getInputStream().transferTo(s.getOutputStream());

            logger.info("MoQ: relay ended for stream {}", streamId);
            // Close socket so FFmpeg receives clean EOF rather than hanging.
            closeQuietly(s);

        } catch (SocketTimeoutException e) {
            logger.error("MoQ: FFmpeg did not connect within {}ms for stream {}, stopping",
                    acceptTimeoutMs, streamId);
            stopStream();
        } catch (IOException e) {
            // Normal on shutdown: moqProcess destroyed or socket closed by stopStream().
            if (logger.isDebugEnabled()) {
                logger.debug("MoQ: relay IO ended for stream {}: {}", streamId, e.getMessage());
            }
        }
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            // best-effort cleanup; nothing useful we can do here
        }
    }
}
