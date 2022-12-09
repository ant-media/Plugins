# ZixiPlugin
ZixiPlugin is the tight integration of [Zixi SDK](https://zixi.com) to Ant Media Server. 

## Features
### ZixiClient 
It can connect and pulls the stream from ZixiBroadcaster to Ant Media Server so that you can watch the stream with WebRTC/HLS/DASH etc. on Ant Media Server. In other words, any stream in ZixiBroadcaster can be available in Ant Media Server

### ZixiFeeder
It can push the stream in Ant Media Server to ZixiBroadcaster so that any stream ingested by WebRTC, RTSP, RTMP, SRT, etc. in Ant Media Server can be available in ZixiBroadcaster 

## How to Install
### Pre-request
You need to install Ant Media Server Enterprise Edition in your instance. 
ZixiPlugin is compatible with Ant Media Server 2.5.2 and later versions. Current `zixi-plugin.jar` is x86_64 compatible. 

### Install
1. Download the pre-built `zixi-plugin.jar` file
  ```
  wget 
  ```
2. Copy the `zixi-plugin.jar` file to `plugins` directory under `/usr/local/antmedia`
  ```
  sudo cp zixi-plugin.jar /usr/local/antmedia/plugins
  ```
3. Restart the service
  ```
  sudo service antmedia restart
  ```

## How to Use
ZixiPlugins have REST Methods so start ZixiClient and ZixiFeeder.
Firstly, please install your ZixiBroadcast to your instance. Please reach out to [Zixi](https://zixi.com) to have ZixiBroadcaster and instructions.


### Start a ZixiClient
1. Click INPUTs in ZixiBroadcaster and then Click `New Input` 
   <img width="1141" alt="Screenshot 2022-12-09 at 12 06 28" src="https://user-images.githubusercontent.com/3456251/206665747-93d6b3d7-1742-4839-b563-757c614a1bc4.png">
2. Add a new stream with `Push` type. You can give any name for stream id. In the sample below, we use as `stream1`.
   <img width="1211" alt="Screenshot 2022-12-09 at 12 08 47" src="https://user-images.githubusercontent.com/3456251/206666230-69d74581-ee1f-4df6-9551-d2fc5ffdaba2.png">
3. Push a stream to ZixiBroadcaster. You need to have Zixi SDK to push the stream to ZixiBroadcaster. Here are the commands we use to push the stream [ZixiSDK]([url](https://github.com/ant-media/Plugins/tree/zixi/ZixiPlugin/src/test/resources/zixi_sdks-antmedia-linux64-14.13.44304)). 
PAY ATTENTION: We cannot run the Zixi SDK command line tools(`feeder_interface_tester`, `client_interface_tester`) in Virtual Instances. They work for us in baremetal instances. 
   ```
   $ cd src/test/resources/zixi_sdks-antmedia-linux64-14.13.44304
   $ ffmpeg -re -i test.flv -codec copy -f mpegts udp://127.0.0.1:1234?pkt_size=1316 
   ```
   The command above push the content of a UDP stream to the 1234 port of localhost.
   In a second terminal go to Zixi SDK directory again
   ```
   $ cd src/test/resources/zixi_sdks-antmedia-linux64-14.13.44304
   $ export LD_LIBRARY_PATH=../lib
   $ ./feeder_interface_tester 1234 127.0.0.1 2088 stream1 1000 10000 0 -1 0
   ```
   The command above gets the packet from 1234 port and push to the ZixiBroadcasters in 127.0.0.1 through 2088 port and stream1 channel.   
4. You must see that `stream1` is online on the ZixiBroadcaster as shown below
   <img width="1134" alt="Screenshot 2022-12-09 at 12 27 39" src="https://user-images.githubusercontent.com/3456251/206669919-0ae484fa-fe8d-4c5f-b142-838c8aa8c264.png">
5. Call the REST Method below to let Ant Media Server pull the stream from ZixiBroadcaster through the following url `zixi://127.0.0.1:2077/stream1`
   ```
   curl -X "POST" http://127.0.0.1:5080/LiveApp/rest/zixi/client?start=true -H 'Content-Type: application/json' -d '{"streamUrl":"zixi://127.0.0.1:2077/stream1"}'
   ```
   The method should return something below. Please pay attention to `dataId` field because it's the stream id to play the stream in Ant Media Server
   ```json
   {"success":"true", "message":"Stream pulling is started for ",dataId":"zLgmkdjhdhd","errorId":0}
   ``` 
6. Visit `http://AMS_SERVER_IP:5080/LiveApp/player.html`. Write the stream id to the box below and Click the `Play` to start playing 
 



