# TensorflowPlugin
This is a plugin project to detect and recognize objects in the stream with Tensorflow.


## Instructions

First you need to have Ant Media Server Enterprise Edition v2.4.1 or above installed your linux box.

### Install the Plugin
- Clone the repository in an appropriate directory
  ```
  git clone https://github.com/ant-media/Plugins.git
  ```
  
- Go to TensorflowPlugin directory
 ```
 cd Plugins/TensorflowPlugin
 ```

- Run the `redeploy.sh`
  ```
  ./redeploy.sh
  ```
- After this operation you should have `ant-media-tensorflow-plugin.jar` under target directory

- Copy the plugin to the `plugins` directory of Ant Media Server
  ```
  sudo cp target/ant-media-tensorflow-plugin.jar /usr/local/antmedia/plugins
  ```

### Install Dependencies and Update Settings

-  Go to the Ant Media Server install directory 
  ```
  cd /usr/local/antmedia
  ```
- Run the `install_tensorflow_plugin.sh`
  ```
  ./install_tensorflow_plugin.sh
  ```
- Go to the web panel of Ant Media Server with your web browser `http://YOUR_SERVER_ADDR:5080`
- Click the WebRTCAppEE on the left side bar and then click the `Settings` tab
- Add at least one adaptive bitrate and add `0.0.0.0/0` to the IP filter as shown below


We've just add `0.0.0.0/0` for IP filter to accept all requests for the sake of testing easily. You should update it if you're going to use it in Production. 

- Don't forget to Click the `Save` button at the bottom 
 
### Run the Plugin
- Just publish a WebRTC, RTMP stream or Pull a Stream from IP Camera. The easiest way is sending WebRTC stream
- Go to the https://YOUR_SERVER_DOMAIN:5443/WebRTCAppEE and Click the Publish Button. By default, stream id is "stream1" You need to install SSL. [Check this out](https://github.com/ant-media/Ant-Media-Server/wiki/SSL-Setup) 
- Activate the plugin through start REST method. Please replace the `{YOUR_SERVER_ADDR}` and `{STREAM_ID}` . By default steam id is "stream1"
  ```
  curl --location --request POST 'http://{YOUR_SERVER_ADDR}:5080/WebRTCAppEE/rest/v2/tensorflow/{STREAM_ID}/start' \
    --header 'Accept: Application/json' \
    --header 'Content-Type: application/json'
  ```
- Just monitor the previews directory which is `/usr/local/antmedia/webapps/WebRTCAppEE/previews`. You will see images with detections.

- You can stop detection again with stop REST method.
  ```
  curl --location --request POST 'http://{YOUR_SERVER_ADDR}:5080/WebRTCAppEE/rest/v2/tensorflow/{STREAM_ID}/stop' \
    --header 'Accept: Application/json' \
    --header 'Content-Type: application/json'
  ```
  



