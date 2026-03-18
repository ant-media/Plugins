# MoQ Plugin for Ant Media Server

Media-over-QUIC (MoQ) playback plugin for Ant Media Server. Pipes existing AMS streams into a `moq-relay` instance via `moq-cli`, making them available to MoQ-capable clients over WebTransport.

---

## Build & Install

```bash
mvn package -Dmaven.test.skip=true -Dgpg.skip=true -s mvn-settings.xml
```

This produces `target/MoQPlugin-<version>-release.zip`. Extract it on the server and run:

```bash
sudo ./install-moq-plugin.sh
```

The script installs `moq-cli` and `moq-relay` binaries to `/usr/local/antmedia/plugins/`.

---

## Demo Web Player

Build the static player from the [moq](https://github.com/kixelated/moq-rs) JS demo:

```bash
cd js/demo
VITE_RELAY_URL="https://your-server:4443/anon" bun run build -- --base=./
```

Copy `src/dist/` to your AMS webapp directory (e.g. `webapps/LiveApp/moq/`) and open `index.html`.

---

## TODO

- **Security / Access control** — webhook-based auth in `moq-relay` calling AMS REST token validation (see `moq/rs/moq-relay/src/auth.rs`)
- **Embedded relay** — manage `moq-relay` process lifecycle from the plugin instead of requiring a separately running instance
- **Publishing** — accept inbound MoQ streams and publish them into AMS as live streams
- test self-contained ssl dose it work


## Notes

### B-Frames
B-frames can cause decode errors or playback glitches with WebCodecs. If you see issues, disable B-frames on the encoder side (e.g. for x264: `bframes=0`).

### Fallback Player (`/mse.html`)
A fallback player using MSE (Media Source Extensions) is available at `/mse.html`. Use this when WebCodecs or WebTransport are unavailable.

### HTTPS, Local Networks, and Fallbacks
- **WebCodecs** and **WebTransport** require a secure context — they will not work over plain `http://` on a LAN IP.
- On **localhost** (`127.0.0.1`) a secure context is granted automatically — no cert needed.
- On a **local network**, generate a self-signed certificate and configure it in AMS via the Web UI. After a **server restart**, the MoQ relay will automatically pick it up and use it. 
- For local debugging without a cert, browsers allow adding security exceptions for specific IPs.
- The demo player tries **WebTransport → WebSocket** automatically (requires HTTPS setup). For MSE-based playback use the separate player at `/mse.html`, and it can work without https setup.
- NOTE THAT: Fallback player using WebSocket or MSE might not be able to deliver satisfaing ultra-low latency experience. Please use secure **WebTransport** protocol


- Question? When accessing with http we should add port :4443/moq at the end of link....
-           When accessing with https, we should just add /moq at the end of link, right? TEST: If no nginx proxy is is in use, how dose this work with local ant-media-server? Need likely to deploy to amazon to test this....





--- Figure out publishing?





-- BUGFIX: 
 - The Error: time overflow from moq-cli is a separate issue (large accumulated DTS from a long-running stream) — it'll need fixing too, but it's not what's causing the MSE seeking loop.

