# Clip Creator Plugin

The Clip Creator Plugin is an open-source Ant Media Server (AMS) plugin that generates MP4 files from HLS (HTTP Live Streaming) segments. It’s designed to help you capture parts of a live stream at regular intervals or on-demand and save them as MP4 clips. Essentially, this plugin converts your hls stream to MP4 clips. These clips are stored in a specified directory usr/local/antmedia/webapps/{appName}/streams and cataloged in your database as VoD (Video on Demand) objects, ready for sharing and playback.

By default, the plugin saves clips every 10 minutes (600 seconds), but this can be adjusted to fit your needs. Each saved clip also triggers a notification via vodReady webhook, letting you know it’s ready to access.


## Why Use the Clip Creator Plugin?

Without this plugin, creating MP4 clips from live HLS streams requires complicated configurations and multiple steps. The Clip Creator Plugin simplifies this process by:

- Automatically capturing MP4 clips from live streams at set intervals.
- Allowing on-demand clip creation with a simple REST API call.
- Storing generated clips in a ready-to-use MP4 format.
- Reducing manual file merging and segment management.


## Installation
- Install FFmpeg (required for the plugin to function):
  ```
  sudo apt install ffmpeg
  ```

- Download the Plugin(Alternatively, Build from Source)

  Download the jar file through [clip-creator on Sonatype](https://oss.sonatype.org/#nexus-search;gav~io.antmedia.plugin~clip-creator~~~). Now we assume that you've downloaded the clip-creator jar file.


- Copy the `clip-creator.jar` file to the plugins directory
  ```
  sudo cp clip-creator.jar /usr/local/antmedia/plugins/
  ```
  
- Restart the server 
  ```
  sudo service antmedia restart
  ```



## Configuration
To enable the Clip Creator Plugin to function correctly, follow these configuration steps:

- Enable HLS Streaming:
Go to your app settings and ensure HLS streaming is enabled. The plugin uses HLS segments to create MP4 clips.

- Choose your HLS playlist mode based on stream lifetime:

  **For short streams** (minutes to a few hours), `hlsPlayListType=event` works fine — ffmpeg keeps every segment, so any past moment is clip-able.

  **For long-running streams** (hours to days, or 24/7) leave `hlsPlayListType` empty (live mode) and set `hlsListSize` to the retention window you want, in segments. With `hlsTime=2`, `hlsListSize=43200` retains 24 h of clip-able history. ffmpeg's `delete_segments` flag (in the AMS default `hlsflags`) automatically deletes old `.ts` files and prunes the m3u8 — no manual cleanup needed.

  Avoid `event` mode for 24/7 streams: it disables `delete_segments`, so both the m3u8 and `.ts` files grow without bound.

- Adjust Clip Interval:
In advanced app settings, configure the custom settings:
```
"customSettings": {
    "plugin.clip-creator": {
        "mp4CreationIntervalSeconds": 1800
    }
}
```
Replace 1800 with your preferred interval in seconds (default is 600 seconds or 10 minutes).

- Plugin settings reference:
```
"customSettings": {
    "plugin.clip-creator": {
        "enabled": true,
        "mp4CreationIntervalSeconds": 600,
        "deleteHLSFilesAfterCreatedMp4": false,
        "maxClipDurationSeconds": 21600
    }
}
```
`maxClipDurationSeconds` (default 21600 / 6 h) is the hard upper bound on the clip range accepted by the timestamp-range endpoint described below.


## How to Use the Clip Creator Plugin
Once installed and configured, the plugin automatically creates MP4 clips for any active HLS-enabled streams in your `/streams` directory at the set interval.

When periodic MP4 creation is triggered, the plugin will locate the last segment of the .m3u8 file, then go back by the specified interval in seconds, capture that segment range, and generate an MP4 from those segments. So created MP4 clip duration will be same as `mp4CreationIntervalSeconds`

Here are some helpful REST API commands for additional control:


### 1. Start Periodic Clip Creation
By default, plugin starts periodic creation on server boot.

This command is useful if you want to adjust the periodic clip creation interval after startup (boot).

POST Request:
```
https://{YOUR_SERVER}:{PORT}/{APP}/rest/clip-creator/periodic-recording/{periodSeconds}
```

Example CURL Command:
```
curl -X POST "https://{YOUR_SERVER}:{PORT}/{APP}/rest/clip-creator/periodic-recording/{periodSeconds}" -H "Content-Type: application/json"
```

### 2. Create MP4 Clip on Demand
Trigger an immediate MP4 clip creation without waiting for the periodic interval:

POST Request:
```
https://{YOUR_SERVER}:{PORT}/{APP}/rest/clip-creator/mp4/{STREAM_ID}?returnFile=true
```

Example CURL Command:
```
curl -X POST "https://{YOUR_SERVER}:{PORT}/{APP}/rest/clip-creator/mp4/{STREAM_ID}?returnFile=true" -H "Content-Type: application/json"
```

  - `returnFile`: This parameter is set to false by default.
      - `returnFile=true`:
        The server will create the MP4 immediately and return the file content as a response.

      - `returnFile=false`:
        The server will return a JSON response indicating whether the VoD creation was successful. If successful, the response will include a dataId field containing the created vodId.

If there is an MP4 created by plugin since boot, it returns the MP4 clip from last MP4 creation time to the time of calling this REST endpoint.

For example if last MP4 is generated at 14:00 and method is called at 14:05, duration of clip should be 5 minutes.

If there is no MP4 created so far by the plugin, maximum duration of created clip by this endpoint will be around `mp4CreationIntervalSeconds`

### 3. Create MP4 Clip for a UTC Timestamp Range
Create an MP4 from HLS segments between two UTC timestamps. Useful when an external system orchestrates *when* clips are taken (e.g. "give me the slice from 14:00:00 to 14:45:00 UTC for stream X").

POST Request:
```
https://{YOUR_SERVER}:{PORT}/{APP}/rest/clip-creator/mp4/{STREAM_ID}/range?startTimestamp={ms}&endTimestamp={ms}&returnFile={bool}
```

Example CURL Command:
```
curl -X POST "https://{YOUR_SERVER}:{PORT}/{APP}/rest/clip-creator/mp4/{STREAM_ID}/range?startTimestamp=1727644047000&endTimestamp=1727644107000&returnFile=false" -H "Content-Type: application/json"
```

Parameters:
  - `startTimestamp` (required): inclusive UTC milliseconds since epoch.
  - `endTimestamp` (required): inclusive UTC milliseconds since epoch. Must be `>` `startTimestamp` and not in the future.
  - `returnFile`: same contract as `/mp4/{STREAM_ID}` — `false` returns JSON with the new vodId, `true` returns the MP4 file content.

Validation responses:
  - `400` if `endTimestamp <= startTimestamp`.
  - `400` if `endTimestamp` is in the future.
  - `400` if the requested duration exceeds `maxClipDurationSeconds` (default 21600).
  - `417` if no broadcast exists for the stream id.
  - `417` if no segments are found in the range — typically because the requested range falls outside the HLS retention window (`hlsListSize × hlsTime`) or the stream had no data then.

Notes:
  - Range clips do NOT affect the periodic recorder's bookkeeping; `lastMp4CreateTime` is left untouched, so the next periodic interval still slices from where it would have.
  - Concurrent range requests serialize webapp-wide (single mutex shared with the periodic recorder). If high-throughput concurrent clipping is needed, file an issue.

### 4. Stop Periodic Clip Creation
Pause the periodic MP4 creation:

DELETE Request:
```
https://{YOUR_SERVER}:{PORT}/{APP}/rest/clip-creator/periodic-recording
```
Example CURL Command:
```
curl -X POST "https://{YOUR_SERVER}:{PORT}/{APP}/rest/clip-creator/periodic-recording" -H "Content-Type: application/json"
```

## Conclusion 
The Clip Creator Plugin offers an easy, automated way to capture and save MP4 clips from live streams in Ant Media Server. By setting up periodic intervals or using on-demand REST API commands, you can seamlessly integrate this plugin into your streaming setup, ensuring high-quality, shareable MP4 clips are always ready.

If you have questions or need any support, contact us via https://antmedia.io or write directly to contact@antmedia.io

