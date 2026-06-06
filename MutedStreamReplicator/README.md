# MutedStreamReplicator

`MutedStreamReplicator` creates a muted companion stream for each published source stream by republishing it to the same application with the `-muted` suffix.

Example:

- Source stream: `liveStream1`
- Muted replica stream: `liveStream1-muted`

The plugin listens for stream lifecycle events, starts a local RTMP endpoint for the muted replica, disables audio and video ingest on the replica stream itself, and then mirrors muxer output from the original enterprise transcoding pipeline into the replica pipeline.

## Requirements

- Ant Media Server Enterprise
- Maven
- Java version compatible with your Ant Media Server build

This plugin depends on `EncoderAdaptor`, so it is intended for transcoding-enabled flows.

## Build

```sh
mvn clean install -Dgpg.skip=true
```

## Install

Copy the generated plugin jar into your Ant Media Server plugins directory:

```sh
cp target/MutedStreamReplicator.jar /usr/local/antmedia/plugins/
```

Restart Ant Media Server after copying the jar:

```sh
sudo service antmedia restart
```

## How It Works

1. When a source stream starts, the plugin asks Ant Media Server to publish a local RTMP endpoint named `<streamId>-muted`.
2. When that muted replica stream starts, the plugin disables direct ingest on the replica adaptor.
3. The plugin attaches lightweight receiver muxers to the source stream and matching rendition encoders.
4. Those receivers forward packets into the target stream's muxers, producing a muted copy of the source stream.

## Notes

- The muted replica stream id suffix is `-muted`.
- The plugin only manages lifecycle for streams it starts through this suffix convention.
- If the source or target adaptor is not an `EncoderAdaptor`, the plugin logs a warning and skips wiring.

## Logs

Useful logs can be watched with:

```sh
tail -f /usr/local/antmedia/log/ant-media-server.log
```

Typical messages include:

- `Started muted replica stream: <streamId>-muted`
- `Muted replica stream started: <streamId>-muted`
- `Failed to start muted replica stream ...`
