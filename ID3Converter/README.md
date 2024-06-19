# SamplePlugin
This is a plugin for Ant Media Server that reads the unregistered SEI messages from the incoming stream in annexb format
and convert them to ID3 tags in HLS stream. It supports parsing AVC(H264) and HEVC(H265) parsing. 

# Prerequests
- Install Ant Media Server
- Install Maven 

# Quick Start

- Clone the repository and go the Sample Plugin Directory
  ```sh
  git clone https://github.com/ant-media/Plugins.git
  cd Plugins/ID3Converter/
  ```
- Build the Plugin
  ```sh
  mvn install  -Dgpg.skip=true
  ```
- Copy the generated jar file to your Ant Media Server's plugin directory
  ```sh
  cp target/ID3Converter.jar /usr/local/antmedia/plugins
  ```
- Restart the Ant Media Server
  ```
  sudo service antmedia restart
  ```
- Enable `id3TagEnabled` by settings `true` in 'Application Settings' -> 'Advanced' section on web panel

- Publish/unPublish a Live Stream to Ant Media Server with WebRTC/RTMP/RTSP

- Check the ID3 tags in the HLS stream. SEI messages will be there as ID3 tags

  
