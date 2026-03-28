package io.antmedia.muxer;

import io.vertx.core.Vertx;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avformat.Write_packet_Pointer_BytePointer_int;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bytedeco.ffmpeg.avutil.AVDictionary;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

public class MoQMuxer extends Muxer {

    private static final int AVIO_BUFFER_SIZE = 64 * 1024;
    private static final int QUEUE_CAPACITY   = 64; // ~2s buffer at 30fps/4Mbps; ~1MB/stream

    private static final ConcurrentHashMap<BytePointer, MoQMuxer> instances = new ConcurrentHashMap<>();

    private static final Write_packet_Pointer_BytePointer_int writeCallback = new Write_packet_Pointer_BytePointer_int() {
        @Override
        public int call(Pointer opaque, BytePointer buf, int size) {
            MoQMuxer muxer = instances.get(opaque);
            if (muxer == null) return size;
            byte[] data = new byte[size];
            buf.get(data);
            if (!muxer.queue.offer(data)) {
                muxer.logger.warn("Queue full, dropping chunk for {}", muxer.streamName);
            }
            return size;
        }
    };

    private final String streamName;
    private final String relayUrl;
    private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    private volatile boolean running;
    private volatile boolean headerFailed = false;
    private int videoOutStreamIdx = -1; // output stream index for the video track

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
    public synchronized boolean addVideoStream(int width, int height, AVRational timebase, int codecId,
            int streamIndex, boolean isAVC, AVCodecParameters codecpar) {
        int extradataSize = codecpar != null ? codecpar.extradata_size() : -1;
        byte firstBytes = (codecpar != null && extradataSize > 0) ? codecpar.extradata().get(0) : 0;

        boolean result = super.addVideoStream(width, height, timebase, codecId, streamIndex, isAVC, codecpar);
        if (result) {
            videoOutStreamIdx = inputOutputStreamIndexMap.get(streamIndex);
            if (codecpar != null && codecpar.extradata_size() > 0) {
                AVStream outStream = getOutputFormatContext().streams(videoOutStreamIdx);
                byte[] ed = new byte[codecpar.extradata_size()];
                codecpar.extradata().get(ed, 0, ed.length);
                setExtradata(outStream, ed);
                logger.debug("addVideoStream: copied extradata ({} bytes) to output stream for {}", ed.length, streamName);
            } else {
                logger.warn("addVideoStream: no extradata in codecpar for {} — will try to extract from first keyframe", streamName);
            }
        } else {
            logger.warn("addVideoStream: super.addVideoStream returned false for {}", streamName);
        }
        return result;
    }

    /**
     * For RTMP/ffmpeg (direct-muxing) sources: addStream is called instead of addVideoStream.
     * The base class copies full codecpar (including extradata) via avcodec_parameters_copy,
     * but never sets videoOutStreamIdx — so we must set it here.
     */
    @Override
    public synchronized boolean addStream(AVCodecParameters codecParameters, AVRational timebase, int streamIndex) {
        int codecType = codecParameters.codec_type();
        int extradataSize = codecParameters.extradata_size();
        boolean result = super.addStream(codecParameters, timebase, streamIndex);
        if (result && codecType == AVMEDIA_TYPE_VIDEO) {
            Integer outIdx = inputOutputStreamIndexMap.get(streamIndex);
            if (outIdx != null) {
                videoOutStreamIdx = outIdx;
                logger.debug("addStream: video outIdx={} for {}", videoOutStreamIdx, streamName);
            } else {
                logger.warn("addStream: video stream not in outputMap after super call for {}", streamName);
            }
        }
        return result;
    }

    /**
     * For Opus audio from WebRTC: addAudioStream never sets extradata, but CMAF requires a
     * 19-byte OpusHead in the dOps box. We synthesize it here after the stream is created.
     */
    @Override
    public synchronized boolean addAudioStream(int sampleRate, AVChannelLayout channelLayout, int codecId, int streamIndex) {
        int nbChannels = channelLayout != null ? channelLayout.nb_channels() : -1;

        boolean result = super.addAudioStream(sampleRate, channelLayout, codecId, streamIndex);
        if (result && codecId == AV_CODEC_ID_OPUS) {
            Integer outIdx = inputOutputStreamIndexMap.get(streamIndex);
            if (outIdx != null) {
                AVStream outStream = getOutputFormatContext().streams(outIdx);
                int ch = nbChannels > 0 ? nbChannels : 2;
                int rate = sampleRate > 0 ? sampleRate : 48000;
                // Build 19-byte OpusHead per RFC 7845 for the CMAF dOps box
                byte[] head = new byte[19];
                head[0]='O'; head[1]='p'; head[2]='u'; head[3]='s'; head[4]='H'; head[5]='e'; head[6]='a'; head[7]='d';
                head[8] = 1;                              // version
                head[9] = (byte) ch;                      // channel count
                head[10] = 0x38; head[11] = 0x01;         // pre-skip = 312 (LE), standard for WebRTC Opus
                head[12] = (byte)(rate & 0xFF);            // input sample rate (LE)
                head[13] = (byte)((rate >>  8) & 0xFF);
                head[14] = (byte)((rate >> 16) & 0xFF);
                head[15] = (byte)((rate >> 24) & 0xFF);
                head[16] = 0; head[17] = 0;               // output gain = 0 (LE)
                head[18] = 0;                              // channel mapping family = 0 (simple stereo/mono)
                setExtradata(outStream, head);
                // logger.info("addAudioStream: synthesized OpusHead ch={} rate={} outIdx={} for {}", ch, sampleRate, outIdx, streamName);
            } else {
                logger.warn("addAudioStream: Opus stream not in outputMap for {}", streamName);
            }
        } else if (!result) {
            logger.warn("addAudioStream: super.addAudioStream returned false for {}", streamName);
        }
        return result;
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
        ctx.flags(ctx.flags() | AVFormatContext.AVFMT_FLAG_CUSTOM_IO);
        return true;
    }

    @Override
    public synchronized boolean prepareIO() {
        options.put("movflags", "cmaf+separate_moof+delay_moov+skip_trailer");
        options.put("fflags", "+flush_packets");
        options.put("frag_duration", "5000"); // write every 5ms (max 200 fps technically)
        running = true;
        startMoqCli();
        if (!openIO()) {
            return false;
        }
        
        // allow upstream to feed packets; header written on first keyframe
        isRunning.set(true); 
        return true;
    }

    /**
     * Drop audio and non-keyframe video until the header has been written.
     * writeVideoFrame handles the deferred write_header on the first keyframe.
     */
    @Override
    public synchronized void writePacket(AVPacket pkt, AVRational inputTimebase, AVRational outputTimebase, int codecType) {
        if (!headerWritten) {
            boolean isKeyFrame = (pkt.flags() & AV_PKT_FLAG_KEY) != 0;
            if (codecType != AVMEDIA_TYPE_VIDEO || !isKeyFrame) {
                return;
            }
        }

        super.writePacket(pkt, inputTimebase, outputTimebase, codecType);
    }

    /**
     * On the first keyframe: if extradata is not already set (WebRTC SFU path: isAVC=false,
     * null codecpar), parse SPS/PPS from the Annex B frame and store them in Annex B format
     * (first byte 0x00). The 0x00 first byte triggers FFmpeg movenc.c to call
     * ff_nal_parse_units() on every frame, converting Annex B → AVCC length-prefix format
     * for the mp4 mdat. ff_isom_write_avcc (avcC box) handles Annex B extradata natively.
     * After header is written, subsequent calls go straight to super.
     */
    @Override
    protected void writeVideoFrame(AVPacket pkt, AVFormatContext context) {
        if (!headerWritten) {
            if (videoOutStreamIdx < 0) {
                return;
            }
            AVStream outStream = context.streams(videoOutStreamIdx);
            int existingExtradataSize = outStream.codecpar().extradata_size();

            if (existingExtradataSize <= 0) {
                logger.debug("writeVideoFrame: no extradata on outStream, extracting SPS/PPS in Annex B format for {}", streamName);
                byte[] frameData = new byte[pkt.size()];
                pkt.data().get(frameData, 0, pkt.size());
                byte[] annexBExtradata = extractAnnexBSPSPPS(frameData);
                if (annexBExtradata != null) {
                    setExtradata(outStream, annexBExtradata);
                    logger.debug("writeVideoFrame: set Annex B extradata ({} bytes, first byte=0x{}) for {}",
                            annexBExtradata.length, Integer.toHexString(annexBExtradata[0] & 0xFF), streamName);
                } else {
                    logger.warn("writeVideoFrame: could not extract SPS/PPS from keyframe for {}", streamName);
                }
            }

            if (outStream.codecpar().extradata_size() <= 0) {
                logger.debug("writeVideoFrame: still no extradata, dropping keyframe for {}", streamName);
                return;
            }

            if (headerFailed) {
                logger.debug("writeVideoFrame: headerFailed=true, skipping for {}", streamName);
                return;
            }

            // Call avformat_write_header directly — writeHeader() calls clearResource() on failure,
            // which would free tmpPacket/videoPkt still held by Muxer.writePacket up the call stack
            AVDictionary opts = null;
            if (!options.isEmpty()) {
                opts = new AVDictionary();
                for (String key : options.keySet()) {
                    av_dict_set(opts, key, options.get(key), 0);
                }
            }
            int ret = avformat_write_header(context, opts);
            if (opts != null) av_dict_free(opts);
            if (ret < 0) {
                logger.warn("writeVideoFrame: avformat_write_header failed for {}: {}", streamName, getErrorDefinition(ret));
                headerFailed = true;
                return;
            }
            headerWritten = true;
            logger.debug("writeVideoFrame: header written successfully for {}", streamName);
        }

        super.writeVideoFrame(pkt, context);
    }

    private void setExtradata(AVStream outStream, byte[] data) {
        BytePointer ptr = new BytePointer(av_mallocz(data.length + AV_INPUT_BUFFER_PADDING_SIZE));
        for (int i = 0; i < data.length; i++) ptr.put(i, data[i]);
        if (outStream.codecpar().extradata() != null && !outStream.codecpar().extradata().isNull()) {
            av_free(outStream.codecpar().extradata());
        }
        outStream.codecpar().extradata(ptr);
        outStream.codecpar().extradata_size(data.length);
    }

    /**
     * Extracts SPS and PPS NAL units from an Annex B buffer and returns them
     * in Annex B format: {@code 00 00 00 01 <sps> 00 00 00 01 <pps>}.
     * <p>
     * The result MUST be stored with first byte = 0x00 so that FFmpeg's
     * movenc.c treats it as Annex B and calls ff_nal_parse_units() to convert
     * every video frame from Annex B to AVCC length-prefix format before writing
     * to the mp4 mdat. ff_isom_write_avcc() (which writes the avcC box) also
     * handles Annex B extradata natively.
     * <p>
     * Returns null if SPS or PPS cannot be found or are too short.
     */
    private byte[] extractAnnexBSPSPPS(byte[] annexB) {
        byte[] sps = null, pps = null;
        int i = 0;
        while (i < annexB.length) {
            int scLen = 0;
            if (i + 4 <= annexB.length
                    && (annexB[i] & 0xFF) == 0 && (annexB[i+1] & 0xFF) == 0
                    && (annexB[i+2] & 0xFF) == 0 && (annexB[i+3] & 0xFF) == 1) {
                scLen = 4;
            } else if (i + 3 <= annexB.length
                    && (annexB[i] & 0xFF) == 0 && (annexB[i+1] & 0xFF) == 0
                    && (annexB[i+2] & 0xFF) == 1) {
                scLen = 3;
            }
            if (scLen == 0) { i++; continue; }

            int naluStart = i + scLen;
            if (naluStart >= annexB.length) break;
            int naluType = annexB[naluStart] & 0x1F;

            int j = naluStart + 1;
            while (j < annexB.length) {
                if (j + 3 <= annexB.length
                        && (annexB[j] & 0xFF) == 0 && (annexB[j+1] & 0xFF) == 0
                        && ((annexB[j+2] & 0xFF) == 1
                            || (j + 4 <= annexB.length && (annexB[j+2] & 0xFF) == 0 && (annexB[j+3] & 0xFF) == 1))) {
                    break;
                }
                j++;
            }

            if (naluType == 7 && j - naluStart >= 4) sps = Arrays.copyOfRange(annexB, naluStart, j);
            else if (naluType == 8)                   pps = Arrays.copyOfRange(annexB, naluStart, j);

            i = j;
        }

        if (sps == null || pps == null) return null;

        // 00 00 00 01 <sps> 00 00 00 01 <pps>  — first byte is 0x00, not 0x01
        byte[] out = new byte[4 + sps.length + 4 + pps.length];
        out[2] = 0; out[3] = 1; // 00 00 00 01
        System.arraycopy(sps, 0, out, 4, sps.length);
        out[4 + sps.length + 2] = 0;
        out[4 + sps.length + 3] = 1; // 00 00 00 01
        System.arraycopy(pps, 0, out, 4 + sps.length + 4, pps.length);
        return out;
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
                    if (running) logger.error("Drain error for {}", streamName, e);
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
        if (headerWritten) {
            super.writeTrailer(); // flushes remaining data + frees AVFormatContext
        } else {
            clearResource(); // header never written; just free resources
        }

        if (drainThread != null) {
            try { drainThread.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            drainThread = null;
        }

        if (moqCliProcess != null) {
            try {
                if (!moqCliProcess.waitFor(5, TimeUnit.SECONDS)) {
                    moqCliProcess.destroy();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                moqCliProcess.destroy();
            }
            moqCliProcess = null;
        }

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
        logger.debug("writeTrailer: cleanup complete for {}", streamName);
    }
}
