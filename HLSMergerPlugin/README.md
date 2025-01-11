# HLS Merger Plugin
This is a HLS Merger plugin project for Ant Media Server. The **HLSMerger Plugin** enhances the HLS experience in two key ways:

1. Creating an HLS stream with multiple resolutions  
2. Creating an HLS stream with multiple audio tracks  

---

## 1. Creating an HLS Stream with Multiple Resolutions

Streaming with multiple resolutions is crucial for Adaptive Bitrate Streaming (ABR). ABR dynamically adjusts the video quality based on the viewer’s internet speed, providing a seamless viewing experience. However, ABR requires transcoding, which can be CPU-intensive. While Ant Media Server supports ABR, some users may prefer to handle this process on the client side.

The **HLSMerger Plugin** offers a solution by allowing users to create streams with different resolutions and then merge these streams into a single HLS master file. This eliminates the need for transcoding on the server side, reducing CPU usage.

##### Example: Creating and Merging Multi-Resolution Streams

To publish multiple RTMP streams with different resolutions using FFmpeg, run the following commands:

```bash
ffmpeg -re -i ~/test/bunnyz.mp4 -s 1920x1080 -codec copy -f flv rtmp://localhost/LiveApp/stream1
ffmpeg -re -i ~/test/bunnyz.mp4 -s 1280x720 -codec copy -f flv rtmp://localhost/LiveApp/stream2
ffmpeg -re -i ~/test/bunnyz.mp4 -s 640x360 -codec copy -f flv rtmp://localhost/LiveApp/stream3
```

Once these streams are created, merge them using the following REST API call:


```bash
curl -X POST -H "Accept: Application/json" -H "Content-Type: application/json" \
http://localhost:5080/LiveApp/rest/v1/hls-merger/multi-resolution-stream/merged_stream -d '["stream1", "stream2", "stream3"]'
```

This creates a single HLS master file pointing to the different resolutions of the stream.

To delete this merged stream, use the following command:

```bash
curl -X DELETE -H "Accept: Application/json" -H "Content-Type: application/json" \
http://localhost:5080/LiveApp/rest/v1/hls-merger/multi-resolution-stream/merged_stream
```

## 2. Creating an HLS Stream with Multiple Audio Tracks
Adding multiple audio tracks to a single video stream is ideal for multilingual streaming. The HLSMerger Plugin enables users to merge a video stream with multiple audio streams into a single HLS playlist (M3U8 file). HLS players can then present these audio options to viewers, allowing them to select their preferred language or audio track.

##### Example: Creating and Merging Multi-Audio Streams
Start by creating a video stream and separate audio streams using FFmpeg:

Create the Video Stream

```bash
ffmpeg -re -i ~/test/bunnyz.mp4 -codec copy -f flv rtmp://localhost/LiveApp/videoStream
```

Create Audio Streams

```bash
ffmpeg -re -i ~/test/bunnyz.mp4 -codec copy -map 0:a:0 -f flv rtmp://localhost/LiveApp/audiostream1
ffmpeg -re -i ~/test/bunnyz.mp4 -codec copy -map 0:a:1 -f flv rtmp://localhost/LiveApp/audiostream2
```

Merge the audio streams with the video stream using the following REST API call:

```bash
curl -X POST -H "Accept: Application/json" -H "Content-Type: application/json" \
http://localhost:5080/LiveApp/rest//v1/hls-merger/videoStream/multi-audio-stream/merged_stream -d '["audiostream1", "audiostream2"]'
```

Once merged, the M3U8 file will use the audio group feature of HLS, enabling audio selection in compatible players.

To delete this merged stream, use the following command:

```bash
curl -X DELETE -H "Accept: Application/json" -H "Content-Type: application/json" \
http://localhost:5080/LiveApp/rest/v1/hls-merger/multi-audio-stream/merged_stream
```

# How to Build and Install the HLSMerger Plugin
## Building the Plugin
Clone the Ant Media Plugins repository.
Navigate to the HLSMerger folder.
Run the following Maven command to build the plugin:

```bash
mvn clean install -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -Dgpg.skip=true
```

The `HLSMerger.jar` file will be created in the `target` folder.

## Installing the Plugin
Copy the `HLSMerger.jar` file to the `/usr/local/antmedia/plugins` directory.
Restart the Ant Media Server service:

```bash
sudo service antmedia restart
```






