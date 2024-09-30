package io.antmedia.plugin;

import com.google.gson.Gson;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.muxer.RecordMuxer;
import io.lindstrom.m3u8.model.MediaPlaylist;
import io.lindstrom.m3u8.model.MediaSegment;
import io.lindstrom.m3u8.parser.MediaPlaylistParser;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.muxer.IAntMediaStreamHandler;
import io.vertx.core.Vertx;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

@Component(value = "plugin.clip-creator")
public class ClipCreatorPlugin implements ApplicationContextAware {

    protected static Logger logger = LoggerFactory.getLogger(ClipCreatorPlugin.class);

    public static final String PLUGIN_KEY = "clip-creator";
    private Gson gson = new Gson();
    private Vertx vertx;
    private ApplicationContext applicationContext;
    private Map<String, Long> lastMp4CreateTimeForStream = new HashMap<>();

    private AppSettings appSettings;

    private ClipCreatorSettings clipCreatorSettings;
    private DataStore dataStore;

    private String appName;

    private String streamsFolder;

    private MediaPlaylistParser m3u8Parser = new MediaPlaylistParser();

    private long periodicCreateTimerId = 0;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        vertx = (Vertx) applicationContext.getBean("vertxCore");

        IAntMediaStreamHandler app = getApplication();

        appSettings = app.getAppSettings();
        dataStore = app.getDataStore();
        appName = app.getScope().getName();
        streamsFolder = IAntMediaStreamHandler.WEBAPPS_PATH + appName + File.separator + "streams";

        loadSettings();

        startPeriodicCreationTimer(clipCreatorSettings.getMp4CreationIntervalSeconds());

        logger.info("Clip Creator Plugin is initialized for app: {}", appName);

    }

    public void loadSettings() {
        Object clipCreatorSettingsString = appSettings.getCustomSetting("plugin."+ClipCreatorPlugin.PLUGIN_KEY);

        if (clipCreatorSettings == null) {
            logger.error("Clip Creator plugin: Using default settings for Clip Creator because no Clip Creator settings in the AppSettings for app:{} ", appName);
            clipCreatorSettings = new ClipCreatorSettings();
        } else {
            try {
                clipCreatorSettings = gson.fromJson(clipCreatorSettingsString.toString(), ClipCreatorSettings.class);
                logger.error("Clip Creator plugin: Using Clip Creator settings for app:{} ", appName);
            } catch (Exception e) {
                logger.error("Clip Creator plugin: Invalid Clip Creator settings, using default settings for app:{}", appName);
                clipCreatorSettings = new ClipCreatorSettings();
            }
        }
    }

    public File convertHlsStreamsToMp4(int mp4CreationIntervalSeconds) {

        ArrayList<File> m3u8FileList = getM3u8FileList();
        if (!m3u8FileList.isEmpty()) {

            for (File m3u8File : m3u8FileList) {
                String streamId = m3u8File.getName().replace(".m3u8", "");
                Broadcast broadcast = dataStore.get(streamId);
                if (broadcast != null &&
                        AntMediaApplicationAdapter.BROADCAST_STATUS_BROADCASTING.equals(broadcast.getStatus())) {
                    MediaPlaylist playList = readPlaylist(m3u8File);
                    ArrayList<File> tsFilesToMerge = getSegmentFilesWithinTimeRange(playList, mp4CreationIntervalSeconds, m3u8File);
                    File mp4File = convertHlsToMp4(m3u8File, tsFilesToMerge, streamId);
                    if (mp4File != null) {
                        logger.info("Clip Creator plugin: MP4 file created successfully from HLS playlist {}", mp4File.getAbsolutePath());
                        return mp4File;
                    }
                }
            }
        }
        return null;
    }

    public MediaPlaylist readPlaylist(File m3u8File) {
        try {
            return getM3u8Parser().readPlaylist(m3u8File.toPath());

        } catch (IOException e) {
            logger.error("Clip Creator plugin: Could not read playlist file.");
        }
        return null;
    }

    public File convertHlsToMp4(File m3u8File, ArrayList<File> tsFilesToMerge, String streamId) {
        try {
            File tsFileList = writeTsFilePathsToTxt(m3u8File, tsFilesToMerge);

            String vodId = RandomStringUtils.randomNumeric(24);

            String mp4FilePath = m3u8File.getAbsolutePath().replace(streamId + ".m3u8", vodId + ".mp4");

            long startTime = System.currentTimeMillis();

            if (ClipCreatorConverter.createMp4(tsFileList, mp4FilePath)) {
                logger.info("Clip Creator plugin: New MP4 created from HLS playlist {} for stream {}", mp4FilePath, streamId);
                lastMp4CreateTimeForStream.put(streamId, startTime);

                File mp4File = new File(mp4FilePath);

                getApplication().muxingFinished(
                        streamId,
                        mp4File,
                        startTime,
                        RecordMuxer.getDurationInMs(mp4File, streamId),
                        ClipCreatorConverter.getResolutionHeight(mp4FilePath),
                        mp4FilePath,
                        vodId
                );
                return mp4File;
            } else {
                logger.info("Clip Creator plugin: Could not create MP4 from HLS for stream {}", streamId);
                return null;
            }
        } catch (IOException e) {
            logger.error("Clip Creator plugin: Error occurred while converting Clip Creator for stream {}: {}", streamId, e.getMessage());
            return null;
        }
    }

    public File writeTsFilePathsToTxt(File m3u8File, ArrayList<File> tsFilesToMerge) throws IOException {
        String txtFilePath = m3u8File.getAbsolutePath().replace(".m3u8", ".txt");
        File txtFile = new File(txtFilePath);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(txtFile))) {
            for (File tsFile : tsFilesToMerge) {
                writer.write("file '" + tsFile.getAbsolutePath() + "'");
                writer.newLine();
            }
        }

        return txtFile;
    }

    // Retrieves the segment files from a media playlist that fall within a specified time range.
    // It identifies the last segment in the playlist and captures all segments from that point
    // backward up to the given interval in seconds.
    public ArrayList<File> getSegmentFilesWithinTimeRange(MediaPlaylist playList, long intervalSeconds, File m3u8File) {
        ArrayList<File> segmentFiles = new ArrayList<>();

        Path playlistBasePath = m3u8File.getParentFile().toPath();

        Optional<OffsetDateTime> lastSegmentTime = playList.mediaSegments()
                .get(playList.mediaSegments().size() - 1)
                .programDateTime();

        if (lastSegmentTime.isPresent()) {
            OffsetDateTime cutoffDateTime = lastSegmentTime.get().minusSeconds(intervalSeconds);
            long cutoffTime = cutoffDateTime.toEpochSecond();

            List<MediaSegment> mediaSegments = playList.mediaSegments();
            for (int i = mediaSegments.size() - 1; i >= 0; i--) {
                MediaSegment segment = mediaSegments.get(i);
                segment.programDateTime().ifPresent(dateTime -> {
                    long time = dateTime.toEpochSecond();

                    if (time >= cutoffTime) {
                        Path segmentPath = playlistBasePath.resolve(segment.uri());
                        segmentFiles.add(segmentPath.toFile());
                    }
                });
            }

            Collections.reverse(segmentFiles);
        }

        return segmentFiles;
    }

    public ArrayList<File> getSegmentFilesWithinTimeRange(MediaPlaylist playList, long startTime, long endTime, File m3u8File) {
        ArrayList<File> segmentFiles = new ArrayList<>();

        Path playlistBasePath = m3u8File.getParentFile().toPath();

        List<MediaSegment> mediaSegments = playList.mediaSegments();

        for (MediaSegment segment : mediaSegments) {
            Optional<OffsetDateTime> segmentDateTimeOpt = segment.programDateTime();

            if (segmentDateTimeOpt.isPresent()) {
                OffsetDateTime segmentDateTime = segmentDateTimeOpt.get();
                long segmentTime = segmentDateTime.toEpochSecond() * 1000;

                if (segmentTime >= startTime && segmentTime <= endTime) {
                    Path segmentPath = playlistBasePath.resolve(segment.uri());
                    segmentFiles.add(segmentPath.toFile());
                }
            }
        }

        return segmentFiles;
    }

    public File getM3u8File(String streamId) {
        File directory = new File(streamsFolder);
        if (directory.isDirectory()) {
            return searchM3u8File(directory, streamId);
        }
        return null;
    }

    public ArrayList<File> getM3u8FileList() {
        File directory = new File(streamsFolder);
        ArrayList<File> m3u8Files = new ArrayList<>();

        if (directory.isDirectory()) {
            searchM3u8Files(directory, m3u8Files);
        }

        return m3u8Files;
    }

    private void searchM3u8Files(File directory, ArrayList<File> m3u8Files) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    searchM3u8Files(file, m3u8Files);
                } else if (file.getName().endsWith(".m3u8")) {
                    m3u8Files.add(file);
                }
            }
        }
    }

    private File searchM3u8File(File directory, String streamId) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File result = searchM3u8File(file, streamId);
                    if (result != null) {
                        return result;
                    }
                } else if (file.getName().endsWith(".m3u8")) {
                    String fileNameWithoutExtension = file.getName().substring(0, file.getName().lastIndexOf('.'));
                    if (streamId.equals(fileNameWithoutExtension)) {
                        return file;
                    }
                }
            }
        }

        return null;
    }

    public void reloadSettings() {
        loadSettings();
    }

    public IAntMediaStreamHandler getApplication() {
        return (IAntMediaStreamHandler) applicationContext.getBean(AntMediaApplicationAdapter.BEAN_NAME);
    }

    public Map<String, Long> getLastMp4CreateTimeForStream() {
        return lastMp4CreateTimeForStream;
    }

    public ClipCreatorSettings getClipCreatorSettings() {
        return clipCreatorSettings;
    }

    public void startPeriodicCreationTimer(int mp4CreationIntervalSeconds) {
        if (periodicCreateTimerId != 0) {
            vertx.cancelTimer(periodicCreateTimerId);
        }

        periodicCreateTimerId = vertx.setPeriodic(mp4CreationIntervalSeconds * 1000L, l -> convertHlsStreamsToMp4(mp4CreationIntervalSeconds));

    }

    public void stopPeriodicCreationTimer(){
        if (periodicCreateTimerId != 0) {
            vertx.cancelTimer(periodicCreateTimerId);
        }
    }

    public MediaPlaylistParser getM3u8Parser() {
        return this.m3u8Parser;
    }

    public void setM3u8Parser(MediaPlaylistParser m3u8Parser) {
        this.m3u8Parser = m3u8Parser;
    }

    public String getStreamsFolder() {
        return streamsFolder;
    }

    public void setStreamsFolder(String streamsFolder) {
        this.streamsFolder = streamsFolder;
    }

}
