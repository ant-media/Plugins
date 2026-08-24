# RTSP Output Plugin

Makes any stream published to Ant Media Server playable over RTSP:

```
rtsp://<server>:8554/<app>/<streamId>          the source, exactly as ingested
rtsp://<server>:8554/<app>/<streamId>_480p     one path per transcoded rendition
```

Works with VLC, ffplay, GStreamer `rtspsrc` and VMS software, over TCP interleaved and UDP. RTSP has no
adaptive bitrate, so every rendition is its own URL, named the way recordings are.

Linux x86_64 only, the bundled binary is `linux_amd64`.

## Install

Unzip the release and run the installer as root:

```sh
unzip RtspOutPlugin-release.zip
sudo ./RTSP-Out-Plugin/install-rtsp-out-plugin.sh
```

It stops the server, puts `RtspOutPlugin.jar`, the `antmedia-mediamtx` binary and its licence in
`/usr/local/antmedia/plugins`, and starts the server again. Nothing is written outside that directory.
The server only loads `.jar` and `.zip` from there, so the extensionless binary next to the jar is ignored
by everything but the plugin.

Set `AMS_DIR` if the server is somewhere else, `SERVICE_NAME` if the service is not called `antmedia`:

```sh
sudo AMS_DIR=/opt/antmedia ./RTSP-Out-Plugin/install-rtsp-out-plugin.sh
```

Uninstall is `rm` on those three files plus a restart.

## Ports

Following ports are required for playback:


| port   | protocol | what for                                                           |
| ------ | -------- | ------------------------------------------------------------------ |
| `8554` | TCP      | RTSP signalling, and the media when a client picks TCP interleaved |
| `8000` | UDP      | RTP, the media for clients that pick UDP                           |
| `8001` | UDP      | RTCP for those clients                                             |


They are the standard RTSP ports. TCP playback needs 8554 open, UDP playback needs all
three, and a client that cannot reach 8000 and 8001 looks like a stream that connects and plays nothing. If
any of the three is aready in use on machine, plugin will not start.

## Playing

```sh
ffplay -rtsp_transport tcp rtsp://<server>:8554/<app>/<streamId>     # TCP, needs only 8554
ffplay -rtsp_transport udp rtsp://<server>:8554/<app>/<streamId>     # UDP, needs 8000 and 8001 too
```

FFmpeg based players buffer everything they read while they wait for the first keyframe, then play from the
front of that buffer, so the wait turns into permanent lag. For low latency playback, add these flags:

```sh
ffplay -fflags nobuffer -flags low_delay -analyzeduration 300000 -probesize 200000 -framedrop -rtsp_transport tcp rtsp://<server>:8554/<app>/<streamId>
```

Measured on a live server, that is the difference between roughly 2s and roughly 0.2s of lag.

## How it works

The plugin registers an `RtspMuxer` with the stream's `MuxAdaptor`. It uses the FFmpeg already bundled with
AMS to publish into a local MediaMTX over RTSP `ANNOUNCE`, which serves the players.

```
ingest -> MuxAdaptor -> RtspMuxer -> MediaMTX :8554 -> RTSP clients
```

One instance per server, supervised by the plugin, with everything except RTSP switched off. Its config is
generated into a private temp directory, never the server's `conf`. Every app shares the process and paths
are `<app>/<streamId>`, so two apps can use the same stream id. Undeploying an app closes only its own
endpoints, the process stops when the server does.

Each rendition costs one FFmpeg RTSP context and one path, watched or not, so keep the ladder to
what you need. Readers are cheap, roughly 0.3% of a core and 200 KB each, measured with 20 of them.

## Settings

Under `plugin.rtsp-out` in the app's custom settings:


| key                | default | meaning                                                                     |
| ------------------ | ------- | --------------------------------------------------------------------------- |
| `enabled`          | `true`  | master switch for this app                                                  |
| `enabledByDefault` | `true`  | default for streams with no REST override                                   |
| `udauthEnabled`    | `true`  | run the app's play security on every RTSP reader. See [Security](#security) |


`enabled` applies to the next published stream, no restart needed, and streams already running keep their
endpoints. `authEnabled` needs a server restart, it changes the config the process starts with.

The `antmedia-mediamtx` binary is looked up next to the plugin jar first, where the installer puts it, then
`/usr/local/bin`, then `PATH`. It is the stock binary under our own name, recognisable in `ps`.

## REST

Base path is `/<app>/rest/rtsp-out`.


| method | path                  | purpose                             |
| ------ | --------------------- | ----------------------------------- |
| `GET`  | `/status`             | plugin and RTSP process state       |
| `POST` | `/disable/{streamId}` | turn RTSP output off for one stream |
| `POST` | `/enable/{streamId}`  | turn it on for one stream           |


`enable` and `disable` write a per stream override that beats `enabledByDefault` in both directions and
survives the stream restarting. Both return the usual `{"success":..., "message":...}` JSON.

```json
{
  "app": "LiveApp",
  "enabled": true,
  "enabledByDefault": true,
  "rtspPort": 8554,
  "streamOverrides": { "test_2": false },
  "activeStreams": { "test_1": ["test_1", "test_1_360p"] },
  "mediamtxBinary": "/usr/local/antmedia/plugins/antmedia-mediamtx",
  "mediamtxRunning": true,
  "mediamtxListening": true,
  "mediamtxGeneration": 1,
  "mediamtxGaveUp": false
}
```

`activeStreams` maps a stream id to the paths it has, fewer than expected means one is still being retried
or was given up on. `mediamtxGeneration` counts process starts, so it goes up on every crash and restart,
and `mediamtxGaveUp` means the supervisor stopped restarting it.

## Security

RTSP readers go through the app's play checks, the same ones HLS uses: play token, hash, JWT, TOTP
subscriber and blocked subscribers. A token that plays over HLS plays over RTSP, no second set of keys.
Credentials ride the URL query under the same names, so it is the same command with a longer URL. Quote it,
`&` and `?` are shell characters:

```sh
'rtsp://<server>:8554/<app>/<streamId>?token=<token>'
'rtsp://<server>:8554/<app>/<streamId>?subscriberId=<id>&subscriberCode=<totp>'
```

Players that will not pass a query string can send the token as the RTSP password and the subscriber id as
the user. FFmpeg based clients truncate `user:pass` at 127 characters, so a play token or a hash fits and a
JWT does not:

```sh
'rtsp://<subscriberId>:<token>@<server>:8554/<app>/<streamId>'
```

Where it differs from HLS:

- A one time play token is single use plus a 60 second window for the same client address. RTSP authorizes
  a reader twice, on DESCRIBE and on SETUP, and only the credential ties the two together.
- `hlsViewerLimit` is not enforced and there is no IP filtering.
- One token covers a stream and all of its renditions.
- `authEnabled: false` opens playback to anyone who can reach 8554. Publishing stays restricted to the
  server itself either way.

## Codecs

Whatever Ant Media Server ingests and RTP can carry:


|       |                                                            |
| ----- | ---------------------------------------------------------- |
| video | H264, H265, VP8, MPEG-4 part 2, MPEG-2, MPEG-1, M-JPEG     |
| audio | AAC, Opus, MP3, Vorbis, G711 µ-law, G711 A-law, G722, G726 |


VP9 and AV1 are out, FFmpeg will not packetize either into RTP outside experimental mode. AC-3 is out,
MediaMTX rejects it. A stream in one of those gets no RTSP endpoint and keeps working otherwise.

## Recovering from errors

Each app rechecks its published streams every 2 seconds and rebuilds what is missing, so an endpoint that
could not be opened is not lost. Streams wait while MediaMTX is down, are republished when it restarts, and
a publish connection that breaks under a running stream is rebuilt.

An endpoint that keeps failing while it is up gets 10 tries, then that one path is given up on and logged,
usually a rendition the transcoder never produced or a codec it rejects. The rest of the stream is
unaffected and a restart clears the count. If the process itself dies immediately 5 times in a row, usually
a port conflict, the supervisor stops and `GET /status` reports `mediamtxGaveUp`.

## Troubleshooting

Start with `GET /<app>/rest/rtsp-out/status`, it answers most of these.


| symptom                                 | usual cause                                                                            |
| --------------------------------------- | -------------------------------------------------------------------------------------- |
| nothing plays, `mediamtxGaveUp` is true | one of the three [ports](#ports) is taken. Free it and restart the server              |
| one stream has no endpoint              | it is disabled, check `enabled`, `enabledByDefault` and `streamOverrides`              |
| a rendition is missing                  | the transcoder never produced that rung, the log says `Giving up on RTSP endpoint ...` |
| connects, then plays nothing            | UDP 8000 and 8001 are blocked. Add `-rtsp_transport tcp` to confirm                    |
| audio plays, video does not             | the log warns `bound audio only`. That is a bug, open an issue                         |
| every reader is refused                 | play security is on, see [Security](#security)                                         |
| plays everywhere but here               | its codec is not in the [codec](#codecs) list                                          |
| about 2s of lag                         | client side buffering, see [Playing](#playing)                                         |


## Build

```sh
./release.sh     # runs the tests, writes target/RtspOutPlugin-release.zip
```

The zip is one `RTSP-Out-Plugin/` directory holding the jar, the binary, its licence, this README
and the installer. The binary lives in the repo under `src/main/native/linux-x86_64/`, nothing to download
first, and the script stops before Maven if it is missing.

`./redeploy.sh` builds and copies into a local dev server instead. Running Maven by hand needs
`-DskipTests=false` for the tests, the Ant Media parent pom skips them by default.

## Limits

- **No cluster support.** Every node runs its own instance and only the origin holds the stream, so an RTSP
  URL has to point at the origin.
- **No viewer limits or stats.** The auth callback sees a reader connect but never leave, so a count kept
  from it would only grow.

## Third party

Ships [MediaMTX](https://github.com/bluenviron/mediamtx) v1.20.1, MIT licensed, see
[LICENSE](src/main/native/linux-x86_64/LICENSE).