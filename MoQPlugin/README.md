# MoQ Plugin for Ant Media Server

Media over QUIC (MoQ) support for Ant Media Server. It does two things:

- **Out:** takes streams already running in AMS (RTMP, SRT, WebRTC) and publishes them to a MoQ relay, and optionally to a MoQ CDN.
- **In:** picks up MoQ streams published to the relay and pulls them into AMS as normal broadcasts.

Both directions shell out to the `moq` binary. The plugin can also start and babysit a local `moq-relay` process.

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

The script installs the `moq` and `moq-relay` binaries to `/usr/local/antmedia/plugins/`.

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

In the web panel, under your application's **Settings → Advanced**:

```json
{"plugin.moq":{"moqCdnUrl":"https://draft-16.cloudflare.mediaoverquic.com/<PUBLISH_TOKEN>"}}
```

You can also set it in `webapps/<app>/WEB-INF/red5-web.properties` and restart AMS, or POST it to `/rest/v2/applications/settings/<app>`.

Either way, check the log to see it took effect:

```
MoQ plugin initialized for app: live, relay: https://localhost:4443/moq, cdn: https://draft-16...
```

### Embedded relay

With `useEmbeddedRelay: true` the plugin runs `moq-relay` on port 4443, serving QUIC and HTTP/WS on the same port, with open auth (`--auth-public /`). TLS depends on what is on disk:

- If both `conf/fullchain.pem` and `conf/privkey.pem` exist, the relay uses them and serves `https` and `wss`.
- If not, it generates a self signed cert for `localhost` and serves `http` and `ws`. That only works from the machine itself.

One relay per JVM. If it dies the plugin restarts it, with a 5 second grace so a broken binary does not spin.

### Publishing to a CDN

`moqCdnUrl` is additive. Set it and every stream is published twice, once to your relay and once to the CDN. Publishing, playback and ingest on the relay keep working exactly as before. If the CDN stalls only the CDN copy drops frames.

Costs one extra `moq` process per stream per quality. Nothing is re-encoded.

```json
{"plugin.moq":{"moqCdnUrl":"https://draft-16.cloudflare.mediaoverquic.com/<PUBLISH_TOKEN>"}}
```

The token is the path, so the token is the whole URL. Cloudflare hands out a publish token and a subscribe token. That one is the publish token.

Watch it in a browser with the subscribe token:

```
play.html?url=https://draft-16.cloudflare.mediaoverquic.com/<SUBSCRIBE_TOKEN>&name=live/<streamId>/source
```

Or from a terminal, which is how you tell a CDN problem from a browser problem:

```bash
moq --client-connect "https://draft-16.cloudflare.mediaoverquic.com/<SUBSCRIBE_TOKEN>" \
    --client-bind 0.0.0.0:0 \
    --broadcast live/<streamId>/source export fmp4 | ffplay -fflags nobuffer -flags low_delay -i -
```

`--client-bind 0.0.0.0:0` keeps `moq` off IPv6. Without it it binds `[::]:0` and dies with `NetworkUnreachable` on a host with no IPv6 route. The plugin always passes it.

Cloudflare specifics:

- Use a `draft-16` host, `draft-14` does not work.
- Real certificate, so verification stays on. Only the self signed embedded relay skips it.
- No `/announced` endpoint, so ingest always goes through your own relay. The player skips discovery on `mediaoverquic.com` hosts too, so the quality buttons stay empty and you switch rendition by editing `name`.

---

## MoQ web player

A small Vite app in `src/main/js/player/`, built on the `@moq/watch` and `@moq/publish` web components. This will be folded into the main Ant Media player later.

| page | what it is |
|---|---|
| `play.html` | The player. |
| `publish.html` | Publish from the browser, lands on the relay as `<app>/<streamId>/publish`. |
| `index.html` | Redirect stub, sends `/moq/` straight to `play.html` with the query string intact. |

Query parameters for `play.html`:

- `name` (or `broadcast`, `id`): broadcast name, for example `live/mystream/source`
- `url` (or `host`): relay or CDN to connect to. Without it the player guesses from the page URL and appends `/moq`.
- `latency`: jitter buffer floor in ms, default 250. `real-time` sizes it from RTT instead, which floors at 20ms and is too tight on a LAN. Handy for A/B testing a crackle without a rebuild.

`publish.html` takes `name` and `url` too, but there `name` is just the stream id. The page takes the app from the URL path and adds the suffix itself, so `publish.html?name=mystream` on the `live` app publishes `live/mystream/publish`, which is exactly what ingest looks for.

WebCodecs is required. A browser without it gets an error, there is no fallback player.

The publisher pins H.264, because browsers that can hardware encode AV1 or VP9 pick those first and `import fmp4` rejects them.

Build it:

```bash
cd src/main/js/player
nix-shell            # on nixos, for node and bun. skip it if npm is already on PATH
npm install
npm run build
rm -rf ../../resources/moq-ams-player-build   # asset names are hashed, stale ones linger
cp -r dist ../../resources/moq-ams-player-build
```

The build output is committed under `src/main/resources/moq-ams-player-build/` and `release.xml` packages it into the release zip. To deploy it straight onto a server, replace the directory rather than copying over it, for the same hashed-asset reason:

```bash
sudo rm -rf /usr/local/antmedia/webapps/live/moq
sudo cp -r dist /usr/local/antmedia/webapps/live/moq
# then open http://<server>:5080/live/moq/play.html?name=live/<streamId>/source
```

The JS packages must match the `moq` binaries: both come from the same upstream revision. If playback stops right after `catalog.json`, that pairing has drifted.

---

## Notes

### B-frames

B-frames can cause decode errors and playback glitches with WebCodecs. If you see them, turn B-frames off on the encoder side (for x264: `bframes=0`).

### HTTPS and local networks

- WebCodecs and WebTransport need a secure context. They will not work over plain `http://` on a LAN IP.
- On `localhost` you get a secure context for free, no cert needed.
- On a local network, generate a self signed cert and set it in AMS through the web UI. Restart the server and the relay picks it up.
- For quick local debugging, browsers let you add a security exception for a specific IP.
- The player tries WebTransport first and falls back to WebSocket.
- Fallbacks are not as fast. If you care about latency, get WebTransport working.

---

## Known limitations

There is no access control yet. The relay is open, so anyone who can reach port 4443 can play or publish. Do not expose it to the internet without something in front of it. Token auth is planned.
