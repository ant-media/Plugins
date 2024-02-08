# Media Push Plugin

Media Push Plugin can stream everything on the given web page with video and audio in realtime.

## Features

### 1. Broadcast the whole web page

You can broadcast the whole web page with video and audio in realtime.

### 2. Record the broadcast if needed

You can record the broadcast if needed. But you need to start the recording manually with REST Api or on Ant Media Server Dashboard.

## How to Install 

1. Connect your Ant Media Server Instance via terminal

2. Get the installation script 
  ```
  wget -O install_media-push-plugin.sh https://raw.githubusercontent.com/ant-media/Plugins/master/MediaPushPlugin/src/main/script/install_media-push-plugin.sh && chmod 755 install_media-push-plugin.sh
  ```
3. Run the installation script
  ```
  sudo ./install_media-push-plugin.sh
  ```
3. Restart the service
  ```
  sudo service antmedia restart
  ```

## How to Use

Media Push Plugin have REST API to control the plugin. 

### Start the broadcast

Call the REST Method below to let Ant Media Server broadcast the web page. You should pass the url of the web page and can pass streamId as query parameter you wanted to use as a parameter.
   ```shell
   export ANT_MEDIA_SERVER_BASE_URL=https://antmedia.example.com:5443
   export APP_NAME=WebRTCAppEE
   export URL_TO_RECORD=https://antmedia.example.com/play.html?id=stream1
   
   curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "${ANT_MEDIA_SERVER_BASE_URL}/${APP_NAME}/rest/v1/media-push/start" -d '{"url": "'"${URL_TO_RECORD}"'", "width": 1280, "height": 720}'
   ```

  The command should respond something like below. Pay attention that `dataId` field is the `streamId` that is generated.
  ```
  HTTP/1.1 200 
  Content-Type: Application/json
  Content-Length: 80
  Date: Mon, 05 Feb 2024 15:23:42 GMT

  {"success":true,"message":null,"dataId":"Z8HfjJD1oLnk1707146618681","errorId":0}
  ```

## Stop the broadcast

Call the REST Method below to let Ant Media Server with the stream id you specified in the start method.
   ```shell
   export STREAM_ID=Z8HfjJD1oLnk1707146618681
   curl -i -X POST -H "Accept: Application/json" "${ANT_MEDIA_SERVER_BASE_URL}/${APP_NAME}/rest/v1/media-push/stop/${STREAM_ID}"
   ```
### Recording
It's also available to record by default. Just add `recordType` option as follows when you're making the request. Then the above request will change to something like this
```shell
curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "${ANT_MEDIA_SERVER_BASE_URL}/${APP_NAME}/rest/v1/media-push/start" -d  {"url": "'"${URL_TO_RECORD}"'", "width": 1280, "height": 720, "recordType":"mp4"}
```

#### Adding Chrome Switches
If you need to add extra chrome switches, you can give them in `extraChromeSwitches` fields in comma-separated way. Here is a sample
```shell
curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "${ANT_MEDIA_SERVER_BASE_URL}/${APP_NAME}/rest/v1/media-push/start" -d  {"url": "'"${URL_TO_RECORD}"'", "width": 1280, "height": 720, "recordType":"mp4", "extraChromeSwitches":"--start-fullscreen,--disable-gpu"}
```

For the default switches, please visit MediaPushPlugin.java and find `CHROME_DEFAULT_SWITHES` field. Lastly, all chrome switches are listed [on here](https://peter.sh/experiments/chromium-command-line-switches/) 


#### Run javascript command in the webpage

Call the REST Method below to let Ant Media Server with the stream id you specified in the start method. You should pass the javascript command in the body.
   ```shell
   curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "https://<ant-media-server-domain>/<your-webapp-name>/rest/v1/media-push/send-command?streamId={streamId}"  -d '{"jsCommand": "{javascript_command_which_is_executed}"}'
   ```

### Optional: How to add Composite Layout 

1. Download the composite_layout.html file
  ```
  wget https://github.com/ant-media/Plugins/raw/master/MediaPushPlugin/build/composite_layout.html
  ```
2. Copy the composite_layout.html file into directory under /usr/local/antmedia/webapps/<your-webapp-name>/
  ```
  sudo cp media_push.html /usr/local/antmedia/webapps/<your-webapp-name>/composite_layout.html
  ```

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
