# SamplePlugin
This is a sample plugin project for Ant Media Server. You can use this a basis for your plugin.
With this plugin you can find:
- Accessing the Ant Media Server ie. AntMediaApplicationAdaptor class
- Registration of the plugin as the PacketListener and/or FrameListener 
- Consuming packets and/or frames
- REST interface implementation

# Prerequests
- Install Ant Media Server
- Install Maven 

# Quick Start

- Clone the repository and go the Sample Plugin Directory
  ```sh
  git clone https://github.com/ant-media/Plugins.git
  cd Plugins/SamplePlugin/
  ```
- Build the Sample Plugin
  ```sh
  mvn install  -Dgpg.skip=true
  ```
- Copy the generated jar file to your Ant Media Server's plugin directory
  ```sh
  cp target/PluginApp.jar /usr/local/antmedia/plugins
  ```
- Restart the Ant Media Server
  ```
  sudo service antmedia restart
  ```
- Publish/unPublish a Live Stream to Ant Media Server with WebRTC/RTMP/RTSP
- Check the logs on the server side 
  ```
  tail -f /usr/local/antmedia/log/ant-media-server.log
  ```
  You would see the following logs
  ```
  ...
  ...
  ...
  io.antmedia.plugin.SamplePlugin - *************** Stream Started: streamIdXXXXX ***************
  ...
  ...
  ...
  io.antmedia.plugin.SamplePlugin - *************** Stream Finished: streamIdXXXXX ***************
  ...
  ...
  ...
  ```

For more information about the plugins, [visit this post](https://antmedia.io/plugins-will-make-ant-media-server-more-powerful/)
  
