package io.antmedia.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.EncoderSettings;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.muxer.Muxer;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.scte35.SCTE35CueTracker;
import io.antmedia.scte35.SCTE35MasterPlaylistModifier;
import io.antmedia.scte35.SCTE35PacketListener;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H265;

/**
 * SCTE-35 Plugin for Ant Media Server
 *
 * This plugin detects SCTE-35 cue points from SRT streams and converts them
 * to HLS markers for ad insertion and other broadcast automation purposes.
 */
@Component(value = "scte35.plugin")
public class SCTE35Plugin implements ApplicationContextAware, IStreamListener {

    public static final String BEAN_NAME = "scte35.plugin";

    /**
     * Ad breaks are placed by wall clock, which is read back from the program date time
     * the HLS muxer writes next to every segment.
     */
    private static final String PROGRAM_DATE_TIME_FLAG = "program_date_time";

    protected static Logger logger = LoggerFactory.getLogger(SCTE35Plugin.class);

    private ApplicationContext applicationContext;
    private final ConcurrentMap<String, SCTE35PacketListener> listeners = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SCTE35CueTracker> cueTrackers = new ConcurrentHashMap<>();

    /** Bitrate configured per rendition height, and the fallback for renditions without one.
     * Multithreaded access, needed for modifying m3u8 file on the fly */
    private final ConcurrentMap<String, Map<Integer, Long>> configuredBandwidths = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Long>> pinnedBandwidths = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;

        getApplication().addStreamListener(this);
        checkFilterRegistration();

        logger.info("SCTE-35 Plugin initialized!");
    }

    /**
     * Check if SCTE35ManifestModifierFilter is registered in the ServletContext
     */
    private void checkFilterRegistration() {
        try {
            WebApplicationContext webAppContext = (WebApplicationContext) applicationContext;
            ServletContext servletContext = webAppContext.getServletContext();

            if (servletContext != null) {
                FilterRegistration filterReg = servletContext.getFilterRegistration("SCTE35ManifestModifierFilter");

                if (filterReg == null) {
                    logger.error("*************************************************************");
                    logger.error("SCTE35ManifestModifierFilter is NOT registered in web.xml!");
                    logger.error("Please add the following to your application's WEB-INF/web.xml:");
                    logger.error("");
                    logger.error("    <filter>");
                    logger.error("        <filter-name>SCTE35ManifestModifierFilter</filter-name>");
                    logger.error("        <filter-class>io.antmedia.scte35.SCTE35ManifestModifierFilter</filter-class>");
                    logger.error("        <async-supported>true</async-supported>");
                    logger.error("    </filter>");
                    logger.error("    <filter-mapping>");
                    logger.error("        <filter-name>SCTE35ManifestModifierFilter</filter-name>");
                    logger.error("        <url-pattern>/streams/*</url-pattern>");
                    logger.error("    </filter-mapping>");
                    logger.error("");
                    logger.error("Add this AFTER HlsManifestModifierFilter for correct order.");
                    logger.error("*************************************************************");
                } else {
                    logger.info("SCTE35ManifestModifierFilter is properly registered in web.xml");
                }
            }
        } catch (Exception e) {
            logger.warn("Could not check filter registration: {}", e.getMessage());
        }
    }

    /**
     * Get the application instance
     */
    public AntMediaApplicationAdapter getApplication() {
        return (AntMediaApplicationAdapter) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
    }

    @Override
    public void streamStarted(Broadcast broadcast) {
        String streamId = broadcast.getStreamId();
        AntMediaApplicationAdapter app = getApplication();

        SCTE35CueTracker cueTracker = new SCTE35CueTracker(streamId);
        SCTE35PacketListener listener = new SCTE35PacketListener(streamId, cueTracker);

        if (!app.addPacketListener(streamId, listener)) {
            logger.warn("Failed to add SCTE-35 packet listener for stream: {}", streamId);
            return;
        }

        listeners.put(streamId, listener);
        cueTrackers.put(streamId, cueTracker);

        List<EncoderSettings> encoderSettingsList = getEncoderSettings(broadcast, app);
        configuredBandwidths.put(streamId, readConfiguredBandwidths(encoderSettingsList));
        pinnedBandwidths.put(streamId, new ConcurrentHashMap<>());

        String hlsFlags = app.getAppSettings().getHlsflags();
        if (hlsFlags == null || !hlsFlags.contains(PROGRAM_DATE_TIME_FLAG)) {
            logger.error("hlsflags does not contain '{}' so SCTE-35 markers cannot be placed. "
                    + "Set settings.hlsflags to include +{} for the application.", PROGRAM_DATE_TIME_FLAG, PROGRAM_DATE_TIME_FLAG);
        }

        logger.info("SCTE-35 tracking started for stream: {}", streamId);

        if (encoderSettingsList == null || encoderSettingsList.isEmpty()) {
            createAdaptivePlaylist(broadcast, app);
        }
    }

    private static List<EncoderSettings> getEncoderSettings(Broadcast broadcast, IAntMediaStreamHandler app) {
        List<EncoderSettings> encoderSettingsList = broadcast.getEncoderSettingsList();
        if (encoderSettingsList == null) {
            encoderSettingsList = app.getAppSettings().getEncoderSettings();
        }
        return encoderSettingsList;
    }

    /**
     * Bandwidth to advertise for each rendition height. A rendition carries both tracks,
     * so the video and audio bitrates the user configured are summed.
     */
    private static Map<Integer, Long> readConfiguredBandwidths(List<EncoderSettings> encoderSettingsList) {
        Map<Integer, Long> bandwidths = new HashMap<>();
        if (encoderSettingsList != null) {
            for (EncoderSettings encoderSettings : encoderSettingsList) {
                bandwidths.put(encoderSettings.getHeight(),
                        (long) encoderSettings.getVideoBitrate() + encoderSettings.getAudioBitrate());
            }
        }
        return Collections.unmodifiableMap(bandwidths);
    }

    /**
     * Steadies the bandwidth a master playlist advertises.
     *
     * @return the rewritten playlist, or null when there was nothing to change
     */
    public String pinMasterBandwidths(String streamId, String playlist) {
        Map<Integer, Long> configured = configuredBandwidths.get(streamId);
        Map<String, Long> pinned = pinnedBandwidths.get(streamId);
        if (configured == null || pinned == null) {
            return null;
        }
        return SCTE35MasterPlaylistModifier.pinBandwidths(playlist, streamId, configured, pinned);
    }

    /**
     * Ad servers expect a master playlist. Without adaptive bitrate the server never
     * writes one, so a single entry master pointing at the only rendition is created here.
     */
    private void createAdaptivePlaylist(Broadcast broadcast, IAntMediaStreamHandler app) {
        String streamId = broadcast.getStreamId();

        MuxAdaptor muxAdaptor = app.getMuxAdaptor(streamId);
        if (muxAdaptor == null) {
            logger.warn("MuxAdaptor not found for stream: {}. Cannot create master playlist", streamId);
            return;
        }

        File adaptiveHLSFile = Muxer.getRecordFile(getApplication().getScope(),
                streamId + MuxAdaptor.ADAPTIVE_SUFFIX, ".m3u8", MuxAdaptor.getSubfolder(broadcast, app.getAppSettings()));

        try (PrintWriter out = new PrintWriter(adaptiveHLSFile)) {
            out.println("#EXTM3U");
            out.print("#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=800000");

            if (muxAdaptor.getVideoCodecParameters() != null) {
                boolean isH265 = muxAdaptor.getVideoCodecParameters().codec_id() == AV_CODEC_ID_H265;
                out.print(",RESOLUTION=" + muxAdaptor.getVideoCodecParameters().width()
                        + "x" + muxAdaptor.getVideoCodecParameters().height());
                out.println(",CODECS=\"" + (isH265 ? "hvc1.1.4.L60.B01" : "avc1.42e00a") + ",mp4a.40.2\"");
            } else {
                out.println("");
            }

            out.println(streamId + ".m3u8");

            logger.info("Created master playlist for stream {} with no encoder settings", streamId);
        } catch (FileNotFoundException e) {
            logger.error("Failed to create master playlist for stream {}: {}", streamId, e.getMessage());
        }
    }

    @Override
    public void streamFinished(Broadcast broadcast) {
        String streamId = broadcast.getStreamId();
        cueTrackers.remove(streamId);
        configuredBandwidths.remove(streamId);
        pinnedBandwidths.remove(streamId);

        SCTE35PacketListener listener = listeners.remove(streamId);
        if (listener != null) {
            getApplication().removePacketListener(streamId, listener);
            logger.info("SCTE-35 tracking stopped for stream: {}", streamId);
        }
    }

    @Override
    public void joinedTheRoom(String roomId, String streamId) {
        // No specific action needed for room events in SCTE-35 context
    }

    @Override
    public void leftTheRoom(String roomId, String streamId) {
        // No specific action needed for room events in SCTE-35 context
    }

    /**
     * Ad breaks detected on a stream, or null when the stream is not being tracked
     */
    public SCTE35CueTracker getCueTracker(String streamId) {
        return cueTrackers.get(streamId);
    }
}
