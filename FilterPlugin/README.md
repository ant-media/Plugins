# Filter & MCU Plugin

Filter Plugin let you add filters on the ongoing streams by using REST API and this plugin contains MCU implementation. 
This plugin is deployed in the Ant Media Server Enterprise version. 

# MCU Usage

To use MCU conference, you should add at least one adaptive setting. 
Then you can use MCU conference:

`https://domain-name.com:5443/WebRTCAppEE/mcu.html`

For audio only MCU conference:

`https://domain-name.com:5443/WebRTCAppEE/mcu.html?audioOnly=true`


# Filter Usage

To use filtering, you should add at least one adaptive setting. 
You can create a filter on a stream or multiple streams.
To create a filter you should use the POST method `/v2/filters/create`.
You should pass the filter configuration in JSON format. That JSON should contain
- **inputStreams:** list of the streams that will be used in the filter
- **outputStreams:** stream id of the ouput(filtered) stream
- **videoFilter:** video filter definition, define inputs as [in0], [in1] ... 
- **audioFilter:** audio filter definition, define inputs as [out0], [out1] ...
- **videoEnabled:** true if video will be filtered
- **audioenabled:** true

**Example:** You can apply a vertical flip filter to videao and copy filter to audio with the following REST method call:

```
curl -i -X POST -H "Accept: Application/json" -H "Content-Type: application/json" "http://localhost:5080/WebRTCAppEE/rest/v2/filters/create" -d '{"inputStreams":["stream1"],"outputStreams":["test"],"videoFilter":"[in0]vflip[out0]","audioFilter":"[in0]acopy[out0]","videoEnabled":"true","audioEnabled":"true"}'
```

Note that the stream ID should be `stream1` and the filtered stream ID will be `test`.

# Build

You can build this plugin with the `mvn clean install` command. After building you can copy the `ant-media-filter-plugin.jar` into `/usr/local/antmedia/plugins` directory.
