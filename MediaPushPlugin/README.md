# Media Push Plugin

Media Push Plugin can stream everything on the given web page with video and audio in realtime.

## Features

### 1. Broadcast the whole web page

You can broadcast the whole web page with video and audio in realtime.

### 2. Record the broadcast if needed

You can record the broadcast if needed. But you need to start the recording manually with REST Api or on Ant Media Server Dashboard.

## How to Install 

### Install Google Chrome 108

1. Remove your existing Google Chrome installation
  ```
  sudo apt-get purge google-chrome-stable
  ```
2. Media Push Plugin uses Google Chrome 108 to broadcast the web page. So you need to install Google Chrome 108 to your server. You can install it on Ubuntu with the following commands.
  ```
  wget --no-verbose -O /tmp/chrome.deb http://trusty-packages.scrutinizer-ci.com/google/linux/chrome/deb/pool/main/g/google-chrome-stable/google-chrome-stable_108.0.5359.71-1_amd64.deb
  ```
  ```
  sudo apt install -y /tmp/chrome.deb
  ```
  ```
  rm /tmp/chrome.deb
  ```
3. Disable Google Chrome auto update
  ```
  sudo apt-mark hold google-chrome-stable
  ```
4. Download the pre-built `media-push-plugin.jar` file
  ```
  wget https://github.com/ant-media/Plugins/raw/master/MediaPushPlugin/build/media-push-plugin.jar
  ```
5. Copy the `webpage-recording-plugin.jar` file to `plugins` directory under `/usr/local/antmedia`
  ```
  sudo cp media-push-plugin.jar /usr/local/antmedia/plugins
  ```
6. Restart the service
  ```
  sudo service antmedia restart
  ```

## How to Use

Media Push Plugin have REST API to control the plugin. 

* Start the broadcast

Call the REST Method below to let Ant Media Server broadcast the web page. You should pass the url of the web page and can pass streamId as query parameter you wanted to use as a parameter.
   ```
   curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "https://<ant-media-server-domain>/<your-webapp-name>/rest/v1/media-push/start" -d '{"url": "http://example.com", "width": 1280, "height": 720}'
   ```

* Stop the broadcast

Call the REST Method below to let Ant Media Server with the stream id you specified in the start method.
   ```
   curl -i -X POST -H "Accept: Application/json" "https://<ant-media-server-domain>/<your-webapp-name>/rest/v1/media-push/stop/{streamId}"
   ```

* Send javascript command to a webpage with given stream id

Call the REST Method below to let Ant Media Server with the stream id you specified in the start method. You should pass the javascript command in the body.
   ```
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

- Media Push Plugin uses Google Chrome 108 to broadcast the web page. So you need to install Google Chrome 108 to your server. You can install it on Ubuntu with the following commands.

  ```
  sudo apt-get purge google-chrome-stable
  ```

  ```
  wget --no-verbose -O /tmp/chrome.deb http://trusty-packages.scrutinizer-ci.com/google/linux/chrome/deb/pool/main/g/google-chrome-stable/google-chrome-stable_108.0.5359.71-1_amd64.deb
  ```

  ```
  sudo apt install -y /tmp/chrome.deb
  ```

  ```
  rm /tmp/chrome.deb
  ```

  ```
  sudo apt-mark hold google-chrome-stable
  ```

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
