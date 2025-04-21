# PythonPlugin
This is a Python plugin project for Ant Media Server. You can use this project to interface with python program to modify Audio/Video data, apply AI
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
  cd Plugins/PythonPlugin/
  ```
- Build the Sample Plugin
  ```sh
  sudo ./redeploy.sh
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
  io.antmedia.plugin.PythonPlugin - *************** Stream Started: streamIdXXXXX ***************
  ...
  ...
  ...
  io.antmedia.plugin.PythonPlugin - *************** Stream Finished: streamIdXXXXX ***************
  ...
  ...
  ...
  ```

For more information about the plugins, [visit this post](https://antmedia.io/plugins-will-make-ant-media-server-more-powerful/)
  
-classpath jna.jar:.
