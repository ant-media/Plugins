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
- Add at least one adaptive bitrate as shown below

- Don't forget to Click the `Save` button at the bottom 
 

To use this plugin:
- Run the `install_tensorflow_plugin.sh` script in AMS installation directory. It will download dependant libraries
- copy the ant-media-tensorflow-plugin.jar into {AMS_INSTALLATION_DIRECTORY}/plugins
- Add an adaptive streaming settings from AMS Dashboard
- Start a stream
- Call the following Post REST method to start detection
```{SERVER_URL}/{APP_NAME}/rest/v2/tensorflow/{STREAM_ID}/start```
- You can find the detection results in `{AMS_INSTALLATION_DIRECTORY}/webapps/{APP_NAME}/previews`
- - Call the following Post REST method to stop detection
```{SERVER_URL}/{APP_NAME}/rest/v2/tensorflow/{STREAM_ID}/stop```


