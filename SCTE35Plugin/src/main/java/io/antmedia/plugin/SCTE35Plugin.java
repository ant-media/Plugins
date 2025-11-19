package io.antmedia.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.antmedia.EncoderSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.Muxer;
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
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.antmedia.plugin.api.IStreamListener;
import io.antmedia.scte35.SCTE35HLSMuxer;
import io.antmedia.scte35.SCTE35PacketListener;
import io.antmedia.AppSettings;
import io.antmedia.muxer.MuxAdaptor;
import io.antmedia.storage.StorageClient;
import io.vertx.core.Vertx;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H265;

/**
 * SCTE-35 Plugin for Ant Media Server
 * 
 * This plugin detects SCTE-35 cue points from SRT streams and converts them
 * to HLS markers for ad insertion and other broadcast automation purposes.
 */
@Component(value = "scte35.plugin")
public class SCTE35Plugin implements ApplicationContextAware, IStreamListener {

    public static final String BEAN_NAME = "web.handler";
    protected static Logger logger = LoggerFactory.getLogger(SCTE35Plugin.class);

    private Vertx vertx;
    private ApplicationContext applicationContext;
    private final ConcurrentMap<String, SCTE35PacketListener> scte35ListenerMap = new ConcurrentHashMap<>();

    private DataStore dataStore;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        vertx = (Vertx) applicationContext.getBean("vertxCore");

        IAntMediaStreamHandler app = getApplication();
        app.addStreamListener(this);
        
        // Check if SCTE35ManifestModifierFilter is registered in web.xml
        checkFilterRegistration();

        this.dataStore = app.getDataStore();

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
    public void streamStarted(String streamId) {
        logger.info("Stream started: {} - Adding SCTE-35 packet listener", streamId);
        
        IAntMediaStreamHandler app = getApplication();
        SCTE35PacketListener scte35Listener = new SCTE35PacketListener(streamId, app);

        // Attach packet listener first
        boolean plResult = app.addPacketListener(streamId, scte35Listener);
        if (!plResult) {
            logger.warn("Failed to add SCTE-35 packet listener for stream: {}", streamId);
            return;
        }

        // Retrieve mux adaptor for this stream
        MuxAdaptor muxAdaptor = app.getMuxAdaptor(streamId);
        if (muxAdaptor == null) {
            logger.warn("MuxAdaptor not found for stream: {}. Cannot attach SCTE35HLSMuxer", streamId);
            return;
        }

        Broadcast broadcast = dataStore.get(streamId);
        List<EncoderSettings> encoderSettingsList = null;

        if (broadcast != null) {
            encoderSettingsList = broadcast.getEncoderSettingsList();
            if (encoderSettingsList == null) {
                encoderSettingsList = app.getAppSettings().getEncoderSettings();
            }
        } else {
            logger.error("No broadcast found for streamId:{}!", streamId);
            return;
        }


        scte35ListenerMap.put(streamId, scte35Listener);
        AppSettings settings = app.getAppSettings();
        StorageClient storageClient = ((AntMediaApplicationAdapter) app).getStorageClient();

        SCTE35HLSMuxer scte35HLSMuxer = new SCTE35HLSMuxer(
                vertx,
                storageClient,
                settings.getS3StreamsFolderPath(),
                settings.getUploadExtensionsToS3(),
                settings.getHlsHttpEndpoint(),
                settings.isAddDateTimeToHlsFileName());

        // Pass some basic HLS parameters
        scte35HLSMuxer.setHlsParameters(settings.getHlsListSize(), settings.getHlsTime(),
                settings.getHlsPlayListType(), settings.getHlsflags(), settings.getHlsEncryptionKeyInfoFile(), settings.getHlsSegmentType());

        scte35HLSMuxer.setId3Enabled(false); // change to true if you want ID3

        int videoHeight = 0;
        if (muxAdaptor.getVideoCodecParameters() != null) {
            videoHeight = muxAdaptor.getVideoCodecParameters().height();
        }

        boolean muxerAdded = muxAdaptor.addMuxer(scte35HLSMuxer, videoHeight);
        logger.info("Result of adding SCTE35HLSMuxer to stream {} is {}", streamId, muxerAdded);

        if (encoderSettingsList == null || encoderSettingsList.isEmpty()) {
            // If there are no encoders, we need to create _adaptive.m3u8 file, since ad servers expect 'master' playlist,
            // and won't be created unless we use ABR. So we need to do that manually here
            try {
                File adaptiveHLSFile = Muxer.getRecordFile(this.getApplication().getScope(),
                        streamId + MuxAdaptor.ADAPTIVE_SUFFIX, ".m3u8", MuxAdaptor.getSubfolder(broadcast, app.getAppSettings()));
                
                PrintWriter out = new PrintWriter(adaptiveHLSFile);
                out.println("#EXTM3U");

                out.print("#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=800000");
                
                if (muxAdaptor.getVideoCodecParameters() != null) {
                    int width = muxAdaptor.getVideoCodecParameters().width();
                    int height = muxAdaptor.getVideoCodecParameters().height();
                    boolean isH265 = scte35HLSMuxer.getVideoCodecId() == AV_CODEC_ID_H265;
                    
                    out.print(",RESOLUTION=" + width + "x" + height);
                    out.println(",CODECS=\"" + (isH265 ? "hvc1.1.4.L60.B01" : "avc1.42e00a") + ",mp4a.40.2\"");
                } else {
                    out.println("");
                }
                
                out.println(streamId + ".m3u8");
                
                out.flush();
                out.close();
                
                logger.info("Created adaptive playlist for stream {} with no encoder settings", streamId);
            } catch (FileNotFoundException e) {
                logger.error("Failed to create adaptive playlist for stream {}: {}", streamId, e.getMessage());
            }
        }
    }

    @Override
    public void streamFinished(String streamId) {
        logger.info("Stream finished: {} - Removing SCTE-35 packet listener", streamId);
        
        SCTE35PacketListener scte35Listener = scte35ListenerMap.remove(streamId);
        if (scte35Listener != null) {
            getApplication().removePacketListener(streamId, scte35Listener);
            logger.info("SCTE-35 packet listener removed for stream: {}", streamId);
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
     * Get SCTE-35 listener for a specific stream
     */
    public SCTE35PacketListener getSCTE35Listener(String streamId) {
        return scte35ListenerMap.get(streamId);
    }

    /**
     * Check if a stream is currently in a cue-out state
     */
    public boolean isStreamInCueOut(String streamId) {
        SCTE35PacketListener listener = scte35ListenerMap.get(streamId);
        return listener != null && listener.isInCueOut();
    }

    /**
     * Get the current cue-out duration for a stream
     */
    public long getStreamCueOutDuration(String streamId) {
        SCTE35PacketListener listener = scte35ListenerMap.get(streamId);
        return listener != null ? listener.getCueOutDuration() : -1;
    }

    /**
     * Get the elapsed time since cue-out started for a stream
     */
    public long getStreamCueOutElapsedTime(String streamId, long currentPts) {
        SCTE35PacketListener listener = scte35ListenerMap.get(streamId);
        return listener != null ? listener.getCueOutElapsedTime(currentPts) : 0;
    }

    /**
     * Get statistics about active SCTE-35 listeners
     */
    public String getStats() {
        return String.format("Active SCTE-35 listeners: %d", scte35ListenerMap.size());
    }
}
