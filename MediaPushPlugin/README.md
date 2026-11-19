# Media Push Plugin

The Media Push Plugin is an excellent tool for customizing video views, such as adding animations, overlays, and recording features for video calls and conferences. It works by capturing audio and video from a URL and publishing it to the Ant Media Server. This means that all activities, including emojis, comments, and overlays, can be delivered to large audiences in real-time (WebRTC) or with low latency (HLS).

## Use cases
<img width="640" alt="video call, ant media server"  src="https://github.com/ant-media/Plugins/assets/3456251/5b4c6a9d-23a3-4c8f-86ad-f8a3fb4b8562">
<br/>
Conference Recording
<br/><br/><br/>
<img width="640" alt="video overlay, ant media server" src="https://github.com/ant-media/Plugins/assets/3456251/b4df2134-ffab-426f-9d0d-fbb835210c8d">
<br/>
Live Overlays
<br/><br/><br/>
<img width="640" alt="player kill, ant media server" src="https://github.com/ant-media/Plugins/assets/3456251/f0cfef63-ba52-4dfc-a92c-4d677fa1caee">
<br/>
Advance Scenarios such as Player Kill
<br/><br/><br/>


## Features
### 1. Broadcast the URL
Broadcast the URL, including all animations and overlays, by capturing the view and audio in real time.

### 2. Record All Activities in the URL
Record the streams from the URL.

### 3. Play the Stream
Play the stream in real-time (WebRTC) or with low latency (HLS, DASH, CMAF).


## How to Install 

1. Connect to your Ant Media Server instance via terminal.

2. Download the installation script:
  ```shell
  wget -O install_media-push-plugin.sh https://raw.githubusercontent.com/ant-media/Plugins/master/MediaPushPlugin/src/main/script/install_media-push-plugin.sh && chmod 755 install_media-push-plugin.sh
  ```
3. Execute the installation script:
  ```shell
  sudo ./install_media-push-plugin.sh
  ```
3. Restart the service:
  ```shell
  sudo service antmedia restart
  ```

## How to Use

The Media Push Plugin includes a REST API that allows you to control the plugin remotely. This API can be used to manage settings, initiate broadcasts, and interact with other features provided by the plugin programmatically.

### Start the broadcast

To have the Ant Media Server broadcast a web page, use the REST method described below. You must pass the URL of the web page you wish to broadcast. Optionally, you can specify a streamId by including it as a query parameter.
1. Set the necessary environment variables for your server configuration:
```shell
   export ANT_MEDIA_SERVER_BASE_URL=https://antmedia.example.com:5443
   export APP_NAME=WebRTCAppEE
   export URL_TO_RECORD=https://antmedia.example.com/WebRTCAppEE/play.html?id=stream1
 ```
2. Broadcast Command
Use the following cURL command to initiate the broadcast:
 ```shell
  curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "${ANT_MEDIA_SERVER_BASE_URL}/${APP_NAME}/rest/v1/media-push/start" -d '{"url": "'"${URL_TO_RECORD}"'", "width": 1280, "height": 720}'
   ```
Expected Response
Upon successful execution, the server should respond as follows. Note that the dataId field represents the generated streamId.
  ```
  HTTP/1.1 200 
  Content-Type: Application/json
  Content-Length: 80
  Date: Mon, 05 Feb 2024 15:23:42 GMT

  {"success":true,"message":null,"dataId":"Z8HfjJD1oLnk1707146618681","errorId":0}
  ```
This output confirms that the broadcast has started, and provides the streamId (dataId), which can be used for further operations related to this stream.

### Stop the broadcast
To stop a broadcast on the Ant Media Server using a specified streamId, use the REST method as outlined below. Ensure you have the streamId from a previous broadcast session to correctly identify the stream you wish to stop.
1. Set the streamId of the broadcast you want to stop:
   ```shell
   export STREAM_ID=Z8HfjJD1oLnk1707146618681
   ```
2. Use the following cURL command to send a request to stop the broadcast:

   ```shell
   curl -i -X POST -H "Accept: Application/json" "${ANT_MEDIA_SERVER_BASE_URL}/${APP_NAME}/rest/v1/media-push/stop/${STREAM_ID}"
   ```
This command will instruct the Ant Media Server to stop broadcasting the stream associated with the provided streamId. Make sure the STREAM_ID matches the one you obtained when initiating the broadcast.

### Record the broadcast
To record the broadcast in addition to streaming, you can include the recordType option in your REST API call. This option specifies the format in which the broadcast should be recorded. Here's how you can modify the previous start broadcast command to include recording:

```shell
curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "${ANT_MEDIA_SERVER_BASE_URL}/${APP_NAME}/rest/v1/media-push/start" -d  '{"url": "'"${URL_TO_RECORD}"'", "width": 1280, "height": 720, "recordType":"mp4"}'
```
This command will initiate the broadcast of the specified URL and simultaneously record it in MP4 format. Ensure to replace "${URL_TO_RECORD}" with the actual URL you want to broadcast and record.

### Add Chrome Switches

To incorporate extra Chrome switches into your REST API request for broadcasting a web page with the Ant Media Server, specify them in the `extraChromeSwitches` field of your JSON payload. These should be listed in a comma-separated format. Here’s a refined version of your command that includes extra Chrome switches:
```shell
curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "${ANT_MEDIA_SERVER_BASE_URL}/${APP_NAME}/rest/v1/media-push/start" -d  '{"url": "'"${URL_TO_RECORD}"'", "width": 1280, "height": 720, "recordType":"mp4", "extraChromeSwitches":"--start-fullscreen,--disable-gpu"}'
```
This command configures the Chrome instance that captures the web page with the following switches:

- `--start-fullscreen`: Starts Chrome in fullscreen mode.
- `--disable-gpu`: Disables GPU hardware acceleration.
These switches can help optimize the browser environment for specific server configurations or broadcasting needs.


For the default Chrome switches used by the Media Push Plugin, you can refer to the MediaPushPlugin.java file and look for the `CHROME_DEFAULT_SWITCHES` field. This will provide you with the preset configurations applied to the Chrome instance by the plugin.

Additionally, a comprehensive list of all available Chrome command-line switches can be found on the following website: [Chromium Command Line Switches](https://peter.sh/experiments/chromium-command-line-switches/). This resource is valuable for understanding the full range of options you can utilize to customize the behavior of Chrome through the Media Push Plugin.



### Run Javascript in the URL

To send a JavaScript command to a specific stream on the Ant Media Server using the stream ID provided in the start method, use the following REST API call. Replace placeholders with your actual server domain, web application name, stream ID, and the JavaScript command you wish to execute.

```shell
   curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "https://<ant-media-server-domain>/<your-webapp-name>/rest/v1/media-push/send-command?streamId={streamId}"  -d '{"jsCommand": "{javascript_command_which_is_executed}"}'
```

## Use-case: Conference Recording
The Media Push Plugin is ideal for recording conferences or video calls. A simple player, [multitrack-play.html](https://github.com/ant-media/StreamApp/blob/master/src/main/webapp/multitrack-play.html), is implemented to play video conferences or calls. You can easily customize this player and add overlays to enhance your recordings.

### Start Recording
To start recording a conference or call currently active under streamId: `room1`, follow these steps:

1. Set the necessary environment variables:
```shell
export ANT_MEDIA_SERVER_BASE_URL=https://antmedia.example.com:5443
export APP_NAME=WebRTCAppEE
#Pay attention that there is double quote and we've muted=false query
export URL_TO_RECORD="${ANT_MEDIA_SERVER_BASE_URL}/${APP_NAME}/multitrack-play.html?id=room1&muted=false"
```
2. Execute the following cURL command to start recording:
```shell
curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "${ANT_MEDIA_SERVER_BASE_URL}/${APP_NAME}/rest/v1/media-push/start" -d  '{"url": "'"${URL_TO_RECORD}"'", "width": 1280, "height": 720, "recordType":"mp4"}'
```
Expected Response:
The response includes the `dataId`, which is the new streamId generated for this recording session.
```
  HTTP/1.1 200 
  Content-Type: Application/json
  Content-Length: 80
  Date: Mon, 05 Feb 2024 15:23:42 GMT

  {"success":true,"message":null,"dataId":"Z8HfjJD1oLnk1707146618681","errorId":0}
```

### Stop Recording
To stop the recording, use the streamId (dataId) generated from the start command:
1. Set the streamId:
 ```shell
  export STREAM_ID=Z8HfjJD1oLnk1707146618681
 ```
2. Send a cURL request to stop the recording:

 ```shell
 curl -i -X POST -H "Accept: Application/json" "${ANT_MEDIA_SERVER_BASE_URL}/${APP_NAME}/rest/v1/media-push/stop/${STREAM_ID}"
 ```
This process ensures your conference or video call is recorded and can be managed directly via REST API commands, providing flexibility and control over your media content on the Ant Media Server.

 

## Optional: How to add Composite Layout 

1. Download the composite_layout.html file
  ```
  wget https://github.com/ant-media/Plugins/raw/master/MediaPushPlugin/build/composite_layout.html
  ```
2. Copy the composite_layout.html file into directory under /usr/local/antmedia/webapps/<your-webapp-name>/
  ```
  sudo cp composite_layout.html /usr/local/antmedia/webapps/<your-webapp-name>/composite_layout.html
  ```

Note: There is also a sample composite layout file which provides the current layout as a feedback by SEI or ID3 metadata. Its name is composite_layout_with_feedback.html

### How to use Composite Layout

* Start the Composite Layout

Call the REST Method below to let Ant Media Server with the stream id you specified in the start method. You should pass the url, width and height in the body.
   ```
   curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "https://<ant-media-server-domain>/<your-webapp-name>/rest/v1/media-push/start"  -d '{"url": "https://<ant-media-server-domain>/<your-webapp-name>/composite_layout.html?roomId=<room-name>&publisherId=<composite-layout-publisher-id>", "width": 1280, "height": 720}'
   ```

* Stop the Composite Layout

Call the REST Method below to let Ant Media Server with the stream id you specified in the stop method.
   ```
   curl -i -X POST -H "Accept: Application/json" "https://<ant-media-server-domain>/<your-webapp-name>/rest/v1/media-push/stop/{composite-layout-publisher-id}"
   ```

* Update the Composite Layout UI

Call the REST Method below to update the layout on the fly.
   ```
   curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "https://<ant-media-server-domain>/<your-webapp-name>/rest/v2/broadcasts/<composite-layout-publisher-id>/data"  -d '{"streamId":"streamId1","layoutOptions": {"canvas": {"width": 640,"height": 640},"layout": [{"streamId": "<room-participant-id>","region": {"xPos": 20,"yPos": 0,"zIndex": 1,"width": 200,"height": 200},"fillMode": "fill","placeholderImageUrl": "https://cdn-icons-png.flaticon.com/512/149/149071.png"}]}}' 
   ```

   
## How to Build from Source Code


- Clone the repository

  ```
  git clone https://github.com/ant-media/Plugins
  ```

- Go to the Media Push Plugin directory

  ```
  cd Plugins/MediaPushPlugin
  ```

- Modify the redeploy.sh file with your Ant Media Server installation path

  ```
  Change AMS_DIR=/usr/local/antmedia/
  ```

- Build & install the plugin

  ```
  chmode +x redeploy.sh
  ./redeploy.sh
  ```

- Restart Ant Media Server

  ```
  sudo service antmedia restart
  ```

### How to Customize
You can modify the code and build the plugin by yourself to make it work with your own needs. For example, you can play the video or login to the web page with your own credentials before starting the broadcast.
Go to the MediaPushPlugin and modify the customModification method as you wish. Then build the plugin with the following command.

  ```
  chmode +x redeploy.sh
  ./redeploy.sh
  ```
