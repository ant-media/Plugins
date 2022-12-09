# ZixiPlugin
ZixiPlugin is the tight integration of [Zixi SDK](https://zixi.com) to Ant Media Server. 

## Features
### ZixiClient 
It can connect and pulls the stream from ZixiBroadcaster to Ant Media Server so that you can watch the stream with WebRTC/HLS/DASH etc. on Ant Media Server. In other words, any stream in ZixiBroadcaster can be available in Ant Media Server

### ZixiFeeder
It can push the stream in Ant Media Server to ZixiBroadcaster so that any stream ingested by WebRTC, RTSP, RTMP, SRT, etc. in Ant Media Server can be available in ZixiBroadcaster 

### REST Methods
You can control everything through REST Methods. REST Methods will be available for each application in the Ant Media Server. For all methods, please take a look at the [ZixiRestService.java](https://github.com/ant-media/Plugins/blob/zixi/ZixiPlugin/src/main/java/io/antmedia/rest/ZixiRestService.java)

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
1. Click `INPUTS` in ZixiBroadcaster and then Click `New Input` 
   <img width="800" alt="Screenshot 2022-12-09 at 12 06 28" src="https://user-images.githubusercontent.com/3456251/206665747-93d6b3d7-1742-4839-b563-757c614a1bc4.png">
2. Add a new stream with `Push` type. You can give any name for stream id. In the sample below, we use as `stream1`.
   <img width="800" alt="Screenshot 2022-12-09 at 12 08 47" src="https://user-images.githubusercontent.com/3456251/206666230-69d74581-ee1f-4df6-9551-d2fc5ffdaba2.png">
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
   <img width="800" alt="Screenshot 2022-12-09 at 12 27 39" src="https://user-images.githubusercontent.com/3456251/206669919-0ae484fa-fe8d-4c5f-b142-838c8aa8c264.png">
5. Call the REST Method below to let Ant Media Server pull the stream from ZixiBroadcaster through the following url `zixi://127.0.0.1:2077/stream1`
   ```
   curl -X "POST" http://127.0.0.1:5080/LiveApp/rest/zixi/client?start=true -H 'Content-Type: application/json' -d '{"streamUrl":"zixi://127.0.0.1:2077/stream1"}'
   ```
   The method should return something below. Please pay attention to `dataId` field because it's the stream id to play the stream in Ant Media Server
   ```json
   {"success":"true", "message":"Stream pulling is started for ", "dataId":"zLPmjtlT7whX1670583419181", "errorId":0}
   ``` 
6. Visit `http://AMS_SERVER_IP:5080/LiveApp/player.html`. Write the stream id(`zLPmjtlT7whX1670583419181`) to the box below and click the `Start Playing` button.

   <img width="800" alt="Screenshot 2022-12-09 at 12 27 39" src="https://user-images.githubusercontent.com/3456251/206688427-e6c07187-595b-4f24-b6d9-82ad0c90fcef.png">
  
7. Stop the streaming from client. Pay attention that the stream id(`zLPmjtlT7whX1670583419181`) is being used in the URL 
   ```
   curl -X "DELETE" http://127.0.0.1/LiveApp/rest/zixi/client/zLPmjtlT7whX1670583419181 -H 'Content-Type: application/json'
   ```

### Start a ZixiFeeder
1. Click `INPUTS` in ZixiBroadcaster and then Click `New Input` 
   <img width="800" alt="Screenshot 2022-12-09 at 12 06 28" src="https://user-images.githubusercontent.com/3456251/206665747-93d6b3d7-1742-4839-b563-757c614a1bc4.png">
2. Add a new stream with `Push` type. You can give any name for stream id. In the sample below, we use as `stream2`.
   <img width="800" alt="Screenshot 2022-12-09 at 14 11 17" src="https://user-images.githubusercontent.com/3456251/206689716-9c1ddd1c-4d4f-4b78-be1e-918bb1672553.png">
3. Publish a stream to Ant Media Server through WebRTC on https://AMS_FQDN_DOMAIN:5443/LiveApp as shown below with 'webrtc_stream'

   <img width="800" src="https://user-images.githubusercontent.com/3456251/206694528-55edc0dd-36a3-49cb-85de-1b5845834e8f.png" >

4. Call the following REST method to push the 'webrtc_stream' to the ZixiBroadcaster. Pay attention that we use `webrtc_stream` in the url below and add the ZixiBroadcaster endpoint as url parameter(`zixi://127.0.0.1:2088/stream2`)
   ```
   curl -X "POST" http://127.0.0.1:5080/LiveApp/rest/zixi/feeder/webrtc_stream?url=zixi://127.0.0.1:2088/stream2  -H 'Content-Type: application/json'
   ```
5. Go to the ZixiBroadcaster and see that `stream2` is connected on ZixiBroadcaster
    <img width="800" src="https://user-images.githubusercontent.com/3456251/206694160-37400fdd-4343-4ea7-99db-61fe4bab3660.png" >
   
6. Stop the ZixiFeeder with the following command by using stream id `webrtc_stream` at the end of the url
   ```
   curl -X "DELETE" http://127.0.0.1:5080/LiveApp/rest/zixi/feeder/webrtc_stream  -H 'Content-Type: application/json'
   ```
