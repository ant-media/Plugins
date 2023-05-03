# TimeCodeExractor
This plugin extracts the custom timecodes from the SEI NAL units in the incoming H264 stream.
Timecodes can b reachabe through REST API and WebRTC Data channel.
 

# Prerequests
- Ant Media Server Enterprise

# Quick Start - How to Use
 - Open a shell in the instance that Ant Media Server Enterprise is running

 - Download the TimeCodeExtractor-{VERSION}.jar file in the `build` directory
 
   
 - Copy the jar file to the plugins directory
   ```
   sudo cp TimeCodeExtractor-{VERSION}.jar /usr/local/antmedia/plugins
   ```
   
-  Restart the Ant Media Server 
   ```
   sudo service antmedia restart
   ```
   
- Publish the stream that has timecodes NAL units to the Ant Media Server. Following command just publishes {INPUT} to the Ant Media Server's,
  running in `example.com`,  `WebRTCAppEE` application with stream id `stream123`. You can change these settings according to your network.
  ```
  ffmpeg -re -i {INPUT} -vcodec libx264 -profile baseline -acodec aac -f mpegts srt://example.com:4200?streamid=WebRTCAppEE/stream123
  ```
  
- Get the timecodes via REST method for stream `stream123`. This method returns the timecodes that is found in the last video packet. 
  ```
  curl https://example.com:5443/WebRTCAppEE/rest/timecode-extractor/timecodes/stream123
  ```
  
- Get the timecodes via WebRTC Data channel. TimeCodeExractor plugin also sends the timecodes found in the last video packet 
  through data channel to the all WebRTC viewers. Just listen data channel in your WebRTC player.
  

# How to Build
- Install Maven
  ```
  sudo apt install maven
  ```
- Clone the repository
  ```
  git clone https://github.com/ant-media/Plugins.git
  ```
- Change directory to TimeCodeExractor plugin
  ```
  cd Plugins/TimeCodeExtractor/
  ```
- Run the `redeploy.sh`
  ```
  ./redeploy.s
  ```
- `TimeCodeExractor-{VERSION}.jar` should be under `target` directory
