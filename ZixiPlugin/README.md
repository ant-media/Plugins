# ZixiPlugin
ZixiPlugin is the tight integration of [Zixi SDK](https://zixi.com) to Ant Media Server. 

## Features
### ZixiClient 
It can connect and pulls the stream from ZixiBroadcaster to Ant Media Server so that you can watch the stream with WebRTC/HLS/DASH etc. on Ant Media Server. In other words, any stream in ZixiBroadcaster can be available in Ant Media Server

### ZixiFeeder
It can push the stream in Ant Media Server to ZixiBroadcaster so that any stream ingested by WebRTC, RTSP, RTMP, SRT, etc. in Ant Media Server can be available in ZixiBroadcaster 

## How to Install
### Pre-request, 
You need to install Ant Media Server Enterprise Edition in your instance. 
ZixiPlugin is compatible with Ant Media Server 2.5.2 and later versions. 

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

### Start a ZixiClient
1. Install your ZixiBroadcast to your instance. Please reach out to [Zixi](https://zixi.com) to have ZixiBroadcaster. 
2. Create a INPUT stream in ZixiBroadcaster as 

