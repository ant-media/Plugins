# MoQ Plugin TODO

## Authentication (parked — research complete)

### Goal
Allow/deny MoQ playback using AMS's existing `playToken` mechanism, same as HLS/WebRTC.

### Chosen approach: moq-relay webhook auth

Instead of JWT token exchange, extend moq-relay with a new `--auth-webhook <url>` option.
When a client connects, the relay calls AMS to validate the request — no token exchange,
no JWT key management in the plugin.

**Flow:**
```
Client → relay URL: https://relay/LiveApp/streamId?playToken=xxx
Relay  → GET https://ams-host/rest/v2/moq/authorize?path=/LiveApp/streamId&playToken=xxx
AMS    → HTTP 200 (allow) or 401 (deny)
Relay  → accepts or rejects WebTransport connection
```

**moq-relay changes (Rust, `rs/moq-relay/src/auth.rs`):**
- New `AuthConfig` field: `webhook: Option<String>` (e.g. `--auth-webhook <url>`)
- New field: `webhook_cache_ttl: u64` (seconds, default 30)
- `Auth::verify()` becomes async when webhook is set
- Per `(path, token)` cache using `DashMap<(String, String), Instant>` to avoid hammering AMS
- Hard timeout on webhook call (~2s); fail **closed** on timeout/error
- Can coexist with existing `key`/`public` modes

**AMS changes:**
- New REST endpoint: `GET /rest/v2/moq/authorize`
- Accepts `path` and `playToken` query params
- Reuses existing token validation logic
- Returns 200 or 401 (no body needed)

**Design decisions still open:**
- Fail open vs fail closed when AMS is unreachable (leaning: fail closed)
- Whether to pass client IP to AMS for IP-based restrictions
- Cache invalidation on stream stop

### Contribution
This is generic enough to contribute upstream to the moq project as an alternative
auth backend alongside the existing JWT key and public-path modes.
Relevant files: `rs/moq-relay/src/auth.rs`, `rs/moq-relay/src/config.rs`
