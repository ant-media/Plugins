# RTSP Output Plugin

Makes any stream published to Ant Media Server playable over RTSP:

```
rtsp://<server>:8554/<app>/<streamId>
rtsp://<server>:8554/<app>/<streamId>_480p     one per transcoded rendition
```

Works with VLC, ffplay, GStreamer `rtspsrc` and VMS software, over both TCP interleaved and UDP.

## How it works

RTSP carries no media of its own. It is a text signalling protocol, and the media rides on plain RTP, one independent stream per track, synced by RTCP Sender Reports.

The plugin registers an `RtspMuxer` (`format = "rtsp"`) with the stream's `MuxAdaptor`. That muxer uses the FFmpeg already bundled with AMS to publish the stream into a local MediaMTX over RTSP `ANNOUNCE`, and MediaMTX serves the players.

```
ingest -> MuxAdaptor -> RtspMuxer -> MediaMTX :8554 -> RTSP clients
```

One MediaMTX process per JVM, supervised by the plugin, with everything except RTSP disabled in its config.
Every app on the server shares that one process, and paths are namespaced by app name so they cannot
collide. Its generated config goes into a private temp directory, not the server's `conf` directory.

## Install

Unzip the release and run the installer as root:

```sh
unzip RtspOutPlugin-release.zip
sudo ./RTSP-Out-Plugin/install-rtsp-out-plugin.sh
```

It puts `RtspOutPlugin.jar`, the `mediamtx` binary and its licence in `/usr/local/antmedia/plugins`, then
restarts the server. Nothing is written outside that directory. The server only classloads `.jar` files, so
the binary sitting next to the jar is invisible to it, and the plugin looks for it there first.

Set `AMS_DIR` if the server is somewhere else, and `SERVICE_NAME` if the service is not called `antmedia`:

```sh
sudo AMS_DIR=/opt/antmedia ./RTSP-Out-Plugin/install-rtsp-out-plugin.sh
```

Uninstall is `rm` on those three files plus a restart.

## Build

```sh
./fetch-mediamtx.sh          # once, downloads the pinned MediaMTX
mvn clean package            # jar plus target/RtspOutPlugin-release.zip
```

For a dev box, `./redeploy.sh` builds and copies straight into a local server, then restart it:

```sh
./redeploy.sh
sudo service antmedia restart
```

Unit tests need `-DskipTests=false`, the Ant Media parent pom skips tests by default:

```sh
mvn test -DskipTests=false
```

## Settings

Under `plugin.rtsp-out` in the app's custom settings:

| key | default | meaning |
| --- | --- | --- |
| `enabled` | `true` | master switch for this app |
| `enabledByDefault` | `true` | default for streams with no REST override |
| `rtspPort` | `8554` | port MediaMTX serves on, server wide |

Turning `enabled` on takes effect on the next published stream, no server restart needed. Turning it off
stops new streams getting endpoints; streams already running keep theirs until they stop.

`rtspPort` is not really per app. There is one MediaMTX for the whole server, so the first app to load
picks the port and the rest follow it. See [Multiple apps](#multiple-apps).

The `mediamtx` binary is looked up next to the plugin jar first, which is where `redeploy.sh` puts it, then
in `/usr/local/bin`, then on `PATH`.

MediaMTX gets a generated config with everything except RTSP switched off. It is written to a private
directory under the system temp dir, not to the server's `conf` directory, and rewritten every time the
process starts. There is nothing in it to edit: change the settings above instead.

MediaMTX itself runs at `warn`, so only real problems reach `ant-media-server.log`. At `info` it writes a
few lines per connection and per path, which on a busy server is noise. The plugin logs its own stream
events at `info` either way.

## REST

Base path is `/<app>/rest/rtsp-out`.

| method | path | purpose |
| --- | --- | --- |
| `GET` | `/status` | plugin and MediaMTX state, including the port actually in use and whether the supervisor gave up |
| `POST` | `/disable/{streamId}` | turn RTSP output off for one stream |
| `POST` | `/enable/{streamId}` | turn it on for one stream |

`enable` and `disable` set a per stream override that wins over `enabledByDefault` in both directions, so
`enable` also works on a server that has `enabledByDefault` set to false. The override survives the stream
stopping and restarting. Both return the usual `{"success":..., "message":...}` JSON.

`GET /status` answers with this app's view of the plugin plus the shared MediaMTX:

```json
{
  "app": "LiveApp",
  "enabled": true,
  "enabledByDefault": true,
  "rtspPort": 8554,
  "configuredRtspPort": 8554,
  "streamOverrides": { "test_2": false },
  "activeStreams": { "test_1": ["test_1", "test_1_360p"] },
  "mediamtxBinary": "/usr/local/antmedia/plugins/mediamtx",
  "mediamtxRunning": true,
  "mediamtxListening": true,
  "mediamtxGeneration": 1,
  "mediamtxGaveUp": false
}
```

| field | meaning |
| --- | --- |
| `rtspPort` | the port MediaMTX is really on, which is what the URLs use |
| `configuredRtspPort` | what this app asked for. Different from `rtspPort` means another app got there first |
| `streamOverrides` | the per stream `enable` and `disable` calls that are in effect |
| `activeStreams` | stream id to the MediaMTX paths it currently has. A stream with fewer paths than you expect is still being retried, or gave up on a rung |
| `mediamtxRunning` | the process is alive |
| `mediamtxListening` | it answers on the port. This lags `mediamtxRunning` by a few hundred ms at startup |
| `mediamtxGeneration` | how many times the process has been started. Goes up by one on every crash and restart |
| `mediamtxGaveUp` | the supervisor stopped restarting it. Nothing gets RTSP until the server restarts. See [Recovering from errors](#recovering-from-errors) |

## Recovering from errors

An endpoint that cannot be opened is not lost. Each app runs a reconciler that checks its published
streams every 2 seconds and fixes what is not right:

- MediaMTX was not up yet, or was down, when the stream started. The stream keeps waiting and gets its
  endpoints as soon as MediaMTX answers. Nothing is given up on while MediaMTX is down.
- MediaMTX crashed and the supervisor restarted it. Every live stream on every app is republished into
  the new process. Without this the process would come back and every stream would stay dead.
- One endpoint's publish connection broke while the stream stayed up. It gets rebuilt.

An endpoint that keeps failing while MediaMTX is up and answering gets 10 tries before the plugin gives
up on that one path and says so in the log. That is the case of a rendition the transcoder never
produced, or a codec MediaMTX rejects. The rest of the stream's endpoints are unaffected, and a MediaMTX
restart clears the count.

If MediaMTX itself dies immediately 5 times in a row, usually a port conflict, the supervisor stops
restarting it and logs why once. `GET /status` reports that as `mediamtxGaveUp`.

## Multiple apps

One Ant Media Server means one MediaMTX process, shared by every app that loads this plugin. Only the
plugin bean and its stream bookkeeping are per app.

- Paths are `<app>/<streamId>`, so two apps publishing the same stream id do not collide.
- The first app to load wins `rtspPort`. Any other app publishes to, and advertises, the running port
  rather than its own setting, so nothing silently publishes into a port that is not there. `GET /status`
  shows both numbers, so a mismatch is visible.
- Undeploying one app closes that app's endpoints and leaves MediaMTX and every other app alone. The
  process is only stopped when the server shuts down.

## Playing with low latency

A client joining mid-GOP has to wait for a keyframe. FFmpeg based players buffer everything they read while they wait, then start playing from the beginning of that buffer, so the wait turns into permanent lag. Give them low latency flags:

```sh
ffplay -fflags nobuffer -flags low_delay -analyzeduration 300000 -probesize 200000 \
       -framedrop -rtsp_transport tcp rtsp://<server>:8554/<app>/<streamId>
```

Measured on a live server, this is the difference between roughly 2s and roughly 0.2s of lag on a WebRTC ingested stream. Nothing on the server side moves this number, so tune the client first.

## Codecs

Whatever Ant Media Server ingests and MediaMTX can serve over RTSP:

| | |
| --- | --- |
| video | H264, H265, VP8, MPEG-4 part 2, MPEG-2, MPEG-1, M-JPEG |
| audio | AAC, Opus, MP3, Vorbis, G711 µ-law, G711 A-law, G722, G726 |

VP9 and AV1 are not supported: FFmpeg will not packetize either into RTP outside of experimental mode. AC-3 is not supported: MediaMTX rejects it. A stream in one of those codecs simply gets no RTSP endpoint, everything else about it keeps working.

## Resolutions and adaptive bitrate

**RTSP has no adaptive bitrate.** There is nothing in the protocol for it. RFC 2326 has no quality switching and no manifest, so there is no equivalent of an HLS or DASH playlist for a player to choose from. An SDP can list several video tracks, but that is track selection, not adaptation: a player takes the first video track or tries to render all of them at once. RTSP 2.0 (RFC 7826) has more machinery and essentially nothing implements it.

The way everyone solves this is one URL per rendition. It is what an IP camera's main and sub streams are, and what ONVIF profiles are. You pick the quality by picking the URL, and the client stays on it.

So this plugin publishes one endpoint per rendition. The bare stream id is always the source, and every entry in the app's (or the broadcast's) encoder settings gets its own path, using the same `_<height>p` naming that recordings already use:

```
rtsp://<server>:8554/<app>/<streamId>            the source, exactly as ingested
rtsp://<server>:8554/<app>/<streamId>_480p       transcoded rendition
rtsp://<server>:8554/<app>/<streamId>_360p       transcoded rendition
```

The height in the path is the `height` from the encoder settings, so a ladder of `360` and `480` gives you `_360p` and `_480p`. No bitrate in the name, unlike the HLS playlists.

A rendition endpoint only appears if the transcoder actually produced that rung. A missing rung is retried
for a while first, in case the encoder was just not ready yet, and then the log says which one gave up and why:

```
Giving up on RTSP endpoint test_2_720p after 10 attempts, MuxAdaptor had nothing to bind at height 720
```

This costs one FFmpeg RTSP context and one MediaMTX path per rendition per stream, whether or not anyone is watching, so keep the ladder to what you need.

## What it costs

Measured on one stream with 20 concurrent readers, 10 on each of two renditions, 10 over TCP and 10 over UDP:

| | idle | 20 readers |
| --- | --- | --- |
| MediaMTX CPU | 1.4% | 5.4% |
| MediaMTX RSS | 41.6 MB | 45.7 MB |

Roughly 0.3% of one core and 200 KB per reader, and memory stops growing once everyone is connected. Ant
Media Server itself does not move at all: publishing into MediaMTX costs the same whether nobody or twenty
people are watching. Every reader holds one TCP control connection, UDP ones included.

What does scale is the publish side, one FFmpeg RTSP context and one MediaMTX path per rendition per
stream, watched or not. See [Resolutions and adaptive bitrate](#resolutions-and-adaptive-bitrate).

## Notes

RTSP output is non-muxed. Audio and video are separate RTP streams, which is how RTSP works and not a limitation of this plugin.

Playback is currently unauthenticated. Bind or firewall port 8554 accordingly.
