package io.antmedia.muxer;

import io.vertx.core.Vertx;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.Write_packet_Pointer_BytePointer_int;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

public class MoQMuxer extends Muxer {

    private static final int AVIO_BUFFER_SIZE = 64 * 1024;
    private static final int QUEUE_CAPACITY   = 64; // ~2s buffer at 30fps/4Mbps; ~1MB/stream

    // Static opaque-key → instance dispatch, same pattern as SRTStream
    private static final ConcurrentHashMap<BytePointer, MoQMuxer> instances = new ConcurrentHashMap<>();

    private static final Write_packet_Pointer_BytePointer_int writeCallback =new Write_packet_Pointer_BytePointer_int() {
        @Override
        public int call(Pointer opaque, BytePointer buf, int size) {
            MoQMuxer muxer = instances.get(opaque);
            if (muxer == null) return size;

            byte[] data = new byte[size];
            buf.get(data);

            if (!muxer.queue.offer(data)) {
                muxer.logger.warn("MoQ queue full, dropping fmp4 chunk for {}", muxer.streamName);
            }
            return size;
        }
    };

    private final String streamName;
    private final String relayUrl;
    private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    private volatile boolean running;
    private volatile boolean firstKeyframeSeen;

    private BytePointer opaque;
    private AVIOContext avioContext;
    private Process moqCliProcess;
    private Thread drainThread;

    public MoQMuxer(Vertx vertx, String streamId, int height, String appName, String relayUrl) {
        super(vertx);
        this.streamId = streamId;
        this.format = "mp4";
        this.streamName = appName + "/" + streamId + "/" + (height == 0 ? "source" : height + "p");
        this.relayUrl = relayUrl;

		this.firstAudioDts = -1;
		this.firstVideoDts = -1;
    }

    @Override
    public boolean isCodecSupported(int codecId) {
        return codecId == AV_CODEC_ID_H264 || codecId == AV_CODEC_ID_H265
                || codecId == AV_CODEC_ID_AAC || codecId == AV_CODEC_ID_OPUS;
    }

    @Override
    public AVFormatContext getOutputFormatContext() {
        if (outputFormatContext == null) {
            outputFormatContext = new AVFormatContext(null);
            // No filename — AVIO is injected in openIO()
            if (avformat_alloc_output_context2(outputFormatContext, null, format, (String) null) < 0) {
                logger.error("Could not create fMP4 output context for {}", streamName);
                return null;
            }
        }
        return outputFormatContext;
    }

    @Override
    public String getOutputURL() {
        return "moq://" + streamName;
    }

    @Override
    public boolean openIO() {
        opaque = new BytePointer(streamId);

        BytePointer buffer = new BytePointer(av_malloc(AVIO_BUFFER_SIZE)).capacity(AVIO_BUFFER_SIZE);
        avioContext = avio_alloc_context(buffer, AVIO_BUFFER_SIZE, 1, opaque, null, writeCallback, null);

        if (avioContext == null || avioContext.isNull()) {
            logger.error("Failed to allocate AVIOContext for {}", streamName);
            return false;
        }

        instances.put(opaque, this);
        AVFormatContext ctx = getOutputFormatContext();
        ctx.pb(avioContext);
        // Tell clearResource() not to call avio_closep() — we own this AVIOContext
        ctx.flags(ctx.flags() | AVFormatContext.AVFMT_FLAG_CUSTOM_IO);
        return true;
    }

    @Override
    public synchronized boolean prepareIO() {
        options.put("movflags", "cmaf+separate_moof+delay_moov+skip_trailer+frag_every_frame");
        options.put("fflags", "+flush_packets");
        running = true;
        startMoqCli();
        return super.prepareIO();
    }

    @Override
    public synchronized void writePacket(AVPacket pkt, AVRational inputTimebase, AVRational outputTimebase, int codecType) {
        // Drop until first video keyframe to avoid partial GOPs
        if (!firstKeyframeSeen) {
            if (codecType == AVMEDIA_TYPE_VIDEO && (pkt.flags() & AV_PKT_FLAG_KEY) != 0) {
                firstKeyframeSeen = true;
            } else {
                return;
            }
        }

        super.writePacket(pkt, inputTimebase, outputTimebase, codecType);
    }

    /** Returns moq-cli stderr stream for external log draining, or null if not started. */
    public InputStream getCliErrorStream() {
        return moqCliProcess != null ? moqCliProcess.getErrorStream() : null;
    }

    private void startMoqCli() {
        try {
            moqCliProcess = new ProcessBuilder(
                    "moq-cli", "publish",
                    "--url", relayUrl,
                    "--tls-disable-verify",
                    "--name", streamName,
                    "fmp4")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();

            OutputStream moqStdin = moqCliProcess.getOutputStream();
            drainThread = new Thread(() -> {
                try {
                    while (running || !queue.isEmpty()) {
                        byte[] chunk = queue.poll(200, TimeUnit.MILLISECONDS);
                        if (chunk != null) {
                            moqStdin.write(chunk);
                            moqStdin.flush();
                        }
                    }
                    moqStdin.close();
                } catch (Exception e) {
                    if (running) logger.error("MoQ drain error for {}", streamName, e);
                }
            }, "moq-drain-" + streamId);
            drainThread.setDaemon(true);
            drainThread.start();

        } catch (Exception e) {
            logger.error("Failed to start moq-cli for {}", streamName, e);
        }
    }

    @Override
    public synchronized void writeTrailer() {
        running = false;
        // av_write_trailer() flushes remaining data through writeCallback into the queue,
        // then clearResource() skips avio_closep() because AVFMT_FLAG_CUSTOM_IO is set.
        super.writeTrailer();

        if (drainThread != null) {
            try {
                drainThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            drainThread = null;
        }

        if (moqCliProcess != null) {
            try {
                if (!moqCliProcess.waitFor(5, TimeUnit.SECONDS)) moqCliProcess.destroy();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                moqCliProcess.destroy();
            }
            moqCliProcess = null;
        }

        // Free AVIO resources (buffer owned by avioContext since avio_alloc_context)
        if (avioContext != null) {
            av_free(avioContext.buffer());
            avioContext.close();
            avioContext = null;
        }

        if (opaque != null) {
            instances.remove(opaque);
            opaque.close();
            opaque = null;
        }

        // logger.info("MoQ muxer writeTrailer: {}", broadcastName);
    }
}
