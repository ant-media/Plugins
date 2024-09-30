## Clip Creator Plugin

This plugin periodically creates MP4 files from HLS segment files for all broadcasting streams in an application based on a specified interval or on demand via REST using a stream ID. The generated MP4 files are stored in the database as VoDs. The MP4 creation interval can be configured in the plugin's app settings, with a default value of 600 seconds.
When periodic MP4 creation is triggered, the plugin will locate the last segment of the .m3u8 file, then go back by the specified interval in seconds, capture that segment range, and generate an MP4 from those segments. So by default, created MP4 clip duration should be around 600 seconds.


## Installation
Copy clip-creator.jar to 
usr/local/antmedia/plugins
directory.
Restart the server.

For compilation run ./redeploy.sh. It will generate clip-creator.jar in target directory.

## Configuration
For this plugin to work correctly HLS should be enabled and
hlsPlayListType must be set as "event" through app settings.

"hlsPlayListType":"event"



## Usage
After installation, the plugin will automatically initialize on server startup. By default, if there are broadcasting HLS-enabled streams, it will generate MP4 files for them in the /streams directory and save them as VoDs in the database.

Plugin also introduces useful REST methods.

### 1-) Create MP4 Clip
Send a `POST` request to:

`https://{YOUR_SERVER}:{PORT}/{APP}/rest/clip-creator/mp4/{STREAM_ID}`

This endpoint will create a MP4 immediately and return file content as response.
If there is an MP4 created by plugin since boot, it returns the mp4 clip from last mp4 creation time to the time of calling this REST endpoint.
For example if last MP4 is generated at 14:00 and method is called at 14:05, duration of clip should be 5 minutes.

If there is no MP4 created so far by the plugin, maximum duration of created clip by this endpoint will be around `mp4CreationIntervalSeconds` 
### 2-) Start Periodic MP4 Creation
By default, plugin starts periodic creation on server boot. This endpoint is useful if you want to modify the periodic creation interval after server boot.

Send a `POST` request to:


`https://{YOUR_SERVER}:{PORT}/{APP}/rest/clip-creator/start/{mp4CreationIntervalSeconds}`
### 3-) Stop Periodic MP4 Creation
Stops periodic MP4 creation.

Send a `POST` request to:


`https://{YOUR_SERVER}:{PORT}/{APP}/rest/clip-creator/stop`


## Options
MP4 creation interval can be set through plugins app settings.

```javascript
"customSettings":{
  "plugin.clip-creator": {
    "mp4CreationIntervalSeconds": 1800
  }
}
```

### 
### Option List

- `mp4CreationIntervalSeconds`: Defines the interval in seconds for generating MP4 files from HLS segments. This value determines how often MP4 clips are created from broadcasting streams as well as duration of the created MP4. 600 by default.

















