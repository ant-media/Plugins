# HLS Merger Plugin

The **HLS Merger Plugin** enhances your HLS streaming capabilities by providing tools to:

1. Create HLS master playlists with multiple resolutions.
2. Combine video streams with multiple audio tracks for multilingual or alternate audio experiences.

---

## Installation

### Prerequisites
- [Ant Media Server](https://antmedia.io/)
- Java Runtime Environment (JRE)
- FFmpeg (for generating streams)

### Installation Steps

1. **Download the Plugin**  
   Fetch the latest snapshot from Sonatype:

   ```bash
   wget -O maven-metadata.xml https://oss.sonatype.org/service/local/repositories/snapshots/content/io/antmedia/plugin/HLSMerger/maven-metadata.xml
   export LATEST_SNAPSHOT=$(grep -o '<version>[^<]*</version>' maven-metadata.xml | tail -n 1 | sed -e 's/<version>//g' -e 's/<\/version>//g')
   wget -O HLSMerger.jar "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.antmedia.plugin&a=HLSMerger&v=${LATEST_SNAPSHOT}&e=jar"
   ```

2. **Install the Plugin**  
   Copy the JAR file into the Ant Media Server plugins directory:

   ```bash
   sudo cp HLSMerger.jar /usr/local/antmedia/plugins
   ```

3. **Restart the Server**  
   Restart Ant Media Server to activate the plugin:

   ```bash
   sudo service antmedia restart
   ```

---

## Usage

### 1. Create a HLS Master Playlist with Multiple Resolutions

#### Overview  
Adaptive Bitrate Streaming (ABR) enhances the viewing experience by dynamically adjusting video quality to match the viewer's internet speed. The **HLS Merger Plugin** simplifies ABR by merging multiple resolution streams into a single master playlist, reducing server-side CPU usage.

#### Steps

1. **Publish Streams with Different Resolutions**  
   Use FFmpeg to stream video at different resolutions:

   ```bash
   ffmpeg -re -stream_loop -1 -i src/test/resources/test_video_720p.flv -codec copy -f flv rtmp://localhost/LiveApp/stream1
   ffmpeg -re -stream_loop -1 -i src/test/resources/test_video_360p.flv -codec copy -f flv rtmp://localhost/LiveApp/stream2
   ```

2. **Merge the Streams**  
   Call the REST API to create a master playlist:

   ```bash
   export MASTER_M3U8=merged_stream
   export SERVER_ADDR=localhost

   curl -X POST -H "Accept: application/json" -H "Content-Type: application/json" \
   http://${SERVER_ADDR}:5080/LiveApp/rest/v1/hls-merger/multi-resolution-stream/$MASTER_M3U8 -d '["stream1", "stream2"]'
   ```

   **Result:**  
   Access the master playlist at:  
   `http://localhost:5080/LiveApp/streams/merged_stream.m3u8`

3. **Play the Stream**  
   - Directly play with Ant Media Server player by visiting the url: `http://localhost:5080/LiveApp/play.html?id=merged_stream&playOrder=hls` in your browser.
   - Use a compatible HLS player to play the resultant m3u8 file URL in step 2.
     For example in [hls.js](https://hlsjs.video-dev.org/demo/):
     1. Copy the master file URL into URL field as in the image.
     2. Click "Apply" button. Player should start to play your stream.
     3. Click "Quality-levels" button. You should see available resolutions below the player.
     4. Choose any resolution to switch the quality.
     ![multi-resolution-hls](multi-resolution-hls.png)

4. **Stop the Merging Process**  
   Delete the master playlist when no longer needed:

   ```bash
   curl -X DELETE -H "Accept: application/json" -H "Content-Type: application/json" \
   http://${SERVER_ADDR}:5080/LiveApp/rest/v1/hls-merger/multi-resolution-stream/$MASTER_M3U8
   ```

---

### 2. Create a HLS Stream with Multiple Audio Tracks

#### Overview  
Support multilingual or alternate audio streams by merging a video stream with multiple audio tracks into a single HLS playlist. Viewers can switch between audio tracks on HLS-compatible players.

#### Steps

1. **Update HLS settings on Ant Media Server Dashboard**

   Set the `Segment Duration` to `10` seconds in LiveApp Application Settings in Ant Media Server Dashdoard.

   ![segment-duration-setting](segment-duration-setting.png)

2. **Create Streams**  
   Generate the video and audio streams:

   - **Video Stream:**

     ```bash
     ffmpeg -re -stream_loop -1 -i src/test/resources/test_video_720p.flv -an -codec copy -f flv rtmp://localhost/LiveApp/videoStream
     ```

   - **Audio Streams:**

     ```bash
     ffmpeg -re -stream_loop -1 -i src/test/resources/test_video_720p.flv -vn -codec copy -f flv rtmp://localhost/LiveApp/audiostream1
     ffmpeg -re -stream_loop -1 -i src/test/resources/test_video_720p.flv -vn -codec copy -f flv rtmp://localhost/LiveApp/audiostream2
     ```

3. **Merge Streams**  
   Use the REST API to combine the audio streams with the video stream:

   ```bash
   export MASTER_M3U8=merged_stream
   export SERVER_ADDR=localhost

   curl -X POST -H "Accept: application/json" -H "Content-Type: application/json" \
   http://${SERVER_ADDR}:5080/LiveApp/rest/v1/hls-merger/videoStream/multi-audio-stream/$MASTER_M3U8 -d '["audiostream1", "audiostream2"]'
   ```

   **Result:**  
   Access the master playlist at:  
   `http://localhost:5080/LiveApp/streams/merged_stream.m3u8`

4. **Play the Stream**  
   Use a compatible HLS player to play the resultant m3u8 file URL in step 3.
   For example in [hls.js](https://hlsjs.video-dev.org/demo/):
   1. Copy the master file URL into URL field as in the image.
   2. Click "Apply" button. Player should start to play your stream.
   3. Click "Audio-tracks" button. You should see available audio tracks below the player.
   4. Choose any audio track to listen.
   
   ![multi-audio-hls](multi-audio-hls.png)

5. **Delete the Merged Stream**  
   Remove the playlist when no longer needed:

   ```bash
   curl -X DELETE -H "Accept: application/json" -H "Content-Type: application/json" \
   http://${SERVER_ADDR}:5080/LiveApp/rest/v1/hls-merger/multi-audio-stream/$MASTER_M3U8
   ```

---

## Build from Source

1. Clone the repository:

   ```bash
   git clone https://github.com/ant-media/Plugins.git
   ```

2. Navigate to the HLSMerger directory:

   ```bash
   cd Plugins/HLSMergerPlugin
   ```

3. Build the plugin:

   ```bash
   mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
   ```

4. Install the plugin:

   ```bash
   sudo cp target/HLSMerger.jar /usr/local/antmedia/plugins
   ```

5. Restart the server:

   ```bash
   sudo service antmedia restart
   ```

---

## Additional Resources

- [Ant Media Server Documentation](https://antmedia.io/docs/)
- [REST API Guide](https://antmedia.io/docs/guides/developer-sdk-and-api/rest-api-guide/)
- [HLS Overview](https://en.wikipedia.org/wiki/HTTP_Live_Streaming)