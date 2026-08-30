# MoQ Plugin for Ant Media Server

Media over QUIC (MoQ) support for Ant Media Server. It does two things:

- **Out:** takes streams already running in AMS (RTMP, SRT, WebRTC) and publishes them to a MoQ relay, and optionally to a MoQ CDN.
- **In:** picks up MoQ streams published to the relay and pulls them into AMS as normal broadcasts.

Both directions shell out to the `moq-cli` binary. The plugin can also start and babysit a local `moq-relay` process.

---

## How it works

```
                       ┌──────────────┐
browser publisher ───► │              │ ──► AMS ingest (becomes a normal broadcast)
                       │  moq-relay   │
AMS streams      ───►  │  :4443       │ ──► browser player
                       └──────────────┘
                              │
AMS streams      ─────────────┴────────────► MoQ CDN (optional, moqCdnUrl)
```

Broadcast names follow a fixed scheme, and they matter when you open the player:

| what | broadcast name |
|---|---|
| stream AMS publishes | `<app>/<streamId>/source` |
| ABR rung | `<app>/<streamId>/720p` |
| stream AMS ingests | `<app>/<streamId>/publish` |

Ingest works by polling. The plugin asks the relay `GET <relay>/announced/moq/<app>` every couple of seconds. Any name ending in `/publish` gets pulled into AMS.

---

## Build and install

```bash
mvn package -Dmaven.test.skip=true -Dgpg.skip=true -s mvn-settings.xml
```

This produces `target/MoQPlugin-<version>-release.zip`. Extract it on the server and run:

```bash
sudo ./install-moq-plugin.sh
```

The script installs the `moq-cli` and `moq-relay` binaries to `/usr/local/antmedia/plugins/`.

---

## Configuration

Everything lives under one AMS custom setting, `plugin.moq`. The value is a JSON object.

| setting | default | what it does |
|---|---|---|
| `useEmbeddedRelay` | `true` | Plugin starts and manages its own `moq-relay` on port 4443. Set to `false` to use a relay you run yourself. |
| `externalRelayUrl` | `https://localhost:4443/moq` | Which relay to use. Only read when `useEmbeddedRelay` is `false`. |
| `moqCdnUrl` | `""` | When set, streams are published to this CDN **on top of** the relay. Empty means off. |
| `ingestPollIntervalMs` | `2000` | How often to ask the relay what is currently published. |

Keys you leave out fall back to the defaults. If the JSON is broken the plugin logs a warning and uses all defaults, so check the log after you edit it.

### Where to put it

Edit `webapps/<app>/WEB-INF/red5-web.properties`, then restart AMS:

```properties
customSettings={"plugin.moq":{"moqCdnUrl":"https://draft-16.cloudflare.mediaoverquic.com/<PUBLISH_TOKEN>"}}
```

Or use the console REST API. Read the settings, change them, post the whole object back (fields you drop get reset to defaults):

```
GET  /rest/v2/applications/settings/<app>
POST /rest/v2/applications/settings/<app>
```

Check it took effect:

```
MoQ plugin initialized for app: live, relay: https://localhost:4443/moq, cdn: https://draft-16...
```

### Embedded relay

With `useEmbeddedRelay: true` the plugin runs `moq-relay` on port 4443, serving QUIC and HTTP/WS on the same port, with open auth (`--auth-public /`). TLS depends on what is on disk:

- If both `conf/fullchain.pem` and `conf/privkey.pem` exist, the relay uses them and serves `https` and `wss`.
- If not, it generates a self signed cert for `localhost` and serves `http` and `ws`. That only works from the machine itself.

One relay per JVM. If it dies the plugin restarts it, with a 5 second grace so a broken binary does not spin.

### Publishing to a CDN

`moqCdnUrl` is additive. Set it and every stream gets published twice, once to your relay and once to the CDN. Publishing, playback and ingest on the relay keep working exactly as before. If the CDN stalls, only the CDN copy drops frames, local playback is untouched.

The cost is one extra `moq-cli` process per stream per quality. No extra encoding.

Cloudflare notes, all tested:

- **Use a `draft-16` host.** `draft-14` accepts the publish and acks it, but no subscriber can ever discover the stream. It looks like it works in the logs and delivers nothing.
- The publish token goes in the URL path, so the whole thing is your relay URL.
- Cloudflare has a real certificate, so verification stays on. Only the self signed embedded relay skips it.

```json
{"plugin.moq":{"moqCdnUrl":"https://draft-16.cloudflare.mediaoverquic.com/eyJhbGciOi..."}}
```

To watch a CDN stream, point the player at Cloudflare with a **subscribe** token:

```
play.html?url=https://draft-16.cloudflare.mediaoverquic.com/<SUBSCRIBE_TOKEN>&name=live/<streamId>/source
```

**Ingest from a CDN does not work.** Two separate reasons: Cloudflare has no `/announced` endpoint, and `moq-cli subscribe` cannot read the CMAF container that `moq-cli publish` writes (it assumes the legacy frame format, see `moq-mux/src/export/fmp4.rs`). So ingest always goes through your own relay.

### IPv6

The plugin always passes `--client-bind 0.0.0.0:0` to `moq-cli`. By default `moq-cli` binds `[::]:0`, picks the AAAA record, and dies with `NetworkUnreachable` on any host without an IPv6 route. The WebSocket fallback then fails too, and all you see in the log is a failed WebSocket, which sends you looking in the wrong place.

---

## MoQ web player

Player pages built from the JS demo in the moq.dev repo. This will be folded into the main Ant Media player later.

| page | what it is |
|---|---|
| `play.html` | The player. WebCodecs by default. |
| `play.html?fallbackPlayer=true` | Same page using MSE instead. Use it when WebCodecs is not available. |
| `publish.html` | Publish from the browser, lands on the relay as `<app>/<streamId>/publish`. |
| `index.html` | Landing page, links to the player. |

Query parameters for `play.html`:

- `name` (or `broadcast`, `id`): broadcast name, for example `live/mystream/source`
- `url` (or `host`): relay or CDN to connect to. Without it the player guesses from the page URL and appends `/moq`.
- `fallbackPlayer=true`: use MSE instead of WebCodecs

`publish.html` takes `name` and `url` too, but there `name` is just the stream id. The page takes the app from the URL path and adds the suffix itself, so `publish.html?name=mystream` on the `live` app publishes `live/mystream/publish`, which is exactly what ingest looks for.

Build it:

```bash
# run from the moq/ repo root, it is a bun workspace
cd /path/to/moq
bun install
cd js/demo
VITE_RELAY_URL="https://your-server:4443/moq" bun run build -- --base=./
```

Deploy it into an AMS webapp:

```bash
sudo cp -r js/demo/src/dist/. /usr/local/antmedia/webapps/live/moq/
# then open http://<server>:5080/live/moq/play.html?name=live/<streamId>/source
```

---

## Notes

### B-frames

B-frames can cause decode errors and playback glitches with WebCodecs. If you see them, turn B-frames off on the encoder side (for x264: `bframes=0`).

### HTTPS and local networks

- WebCodecs and WebTransport need a secure context. They will not work over plain `http://` on a LAN IP.
- On `localhost` you get a secure context for free, no cert needed.
- On a local network, generate a self signed cert and set it in AMS through the web UI. Restart the server and the relay picks it up.
- For quick local debugging, browsers let you add a security exception for a specific IP.
- The player tries WebTransport first and falls back to WebSocket. The MSE player works without HTTPS.
- Fallbacks are not as fast. If you care about latency, get WebTransport working.

---

## TODO and open questions

- **Security and access control:** webhook auth in `moq-relay` calling the AMS REST token validation (see `moq/rs/moq-relay/src/auth.rs`).
- Maybe move the relay to `https://localhost:4443/moq/<appname>` and shorten broadcast names to `<streamId>/source` and `<streamId>/<height>p`.
- URL question: over http you need `:4443/moq` on the end, over https just `/moq`. Needs testing on a real server without an nginx proxy in front.
- `Error: time overflow` from `moq-cli` on long running streams (accumulated DTS gets too large). Separate issue from the MSE seeking loop.
