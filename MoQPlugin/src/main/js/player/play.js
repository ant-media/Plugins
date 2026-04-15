import "@moq/watch/element";
import "@moq/watch/support/element";

// ─── Elements ────────────────────────────────────────────────────────────────
const player         = document.getElementById("player");
const errors         = document.getElementById("errors");
const qualityPanel   = document.getElementById("quality-panel");
const qualityButtons = document.getElementById("quality-buttons");
const urlRows      = document.getElementById("url-rows");
const infoName     = document.getElementById("info-name");
const infoUrl      = document.getElementById("info-url");
const infoMode     = document.getElementById("info-mode");
const infoStatus   = document.getElementById("info-status");
const streamForm   = document.getElementById("stream-control");
const streamInput  = document.getElementById("stream-input");
const modeRadios   = document.querySelectorAll("input[name=mode]");
const volumeSlider = document.getElementById("volume-slider");
const volumeIcon   = document.getElementById("volume-icon");
const volumeLabel  = document.getElementById("volume-label");
const jitterSlider = document.getElementById("jitter-slider");
const jitterLabel  = document.getElementById("jitter-label");
const dbgFps       = document.getElementById("dbg-fps");
const dbgBuf       = document.getElementById("dbg-buf");
const dbgStalled   = document.getElementById("dbg-stalled");
const dbgLatency   = document.getElementById("dbg-latency");
const dbgJitter    = document.getElementById("dbg-jitter");
const dbgVbytes    = document.getElementById("dbg-vbytes");
const dbgCodec     = document.getElementById("dbg-codec");

// ─── Helpers ─────────────────────────────────────────────────────────────────
function badge(text, color) {
  return `<span class="badge badge-${color}">${text}</span>`;
}

function showError(msg) {
  const div = document.createElement("div");
  div.className = "banner banner-error";
  div.innerHTML = `⛔ ${msg}`;
  errors.appendChild(div);
}

// ─── Query params ─────────────────────────────────────────────────────────────
const params = new URLSearchParams(window.location.search);

// Broadcast name: ?name= or ?broadcast= or ?id=
const broadcastName =
  params.get("name") ??
  params.get("broadcast") ??
  params.get("id") ??
  null;

// Source host: ?url= (full URL or just hostname[:port]), or ?host=, or page hostname
const rawUrl  = params.get("url") ?? params.get("host") ?? null;

// Force MSE fallback player: ?fallbackPlayer=true
const forceFallback = params.get("fallbackPlayer") === "true";

// Pre-fill the stream input from URL params (if provided)
streamInput.value = broadcastName ?? "";
infoName.textContent = broadcastName ?? "—";

// ─── WebCodecs check ─────────────────────────────────────────────────────────
const hasWebCodecs = typeof VideoDecoder !== "undefined";

if (!hasWebCodecs) {
  const warn = document.createElement("div");
  warn.className = "banner banner-warn";
  warn.innerHTML =
    `⚠️ <strong>WebCodecs is not supported in this browser.</strong> ` +
    `Falling back to MSE player. Chrome 94+, Edge 94+, or Safari 17.4+ required for WebCodecs.`;
  errors.appendChild(warn);
}

// ─── Player mode switching ────────────────────────────────────────────────────
/**
 * "auto"       → canvas if WebCodecs available, else video (MSE)
 * "webcodecs"  → force canvas
 * "mse"        → force video
 */
// ─── Jitter (buffer) settings per player mode ────────────────────────────────
// Increase jitter for MSE because it needs more buffer for smooth playback.
// WebCodecs decodes frame-by-frame so 100ms is sufficient.
// Search "MSE_JITTER" to find this quickly.
const JITTER_WEBCODECS = 100; // ms
const JITTER_MSE       = 200; // ms — MSE_JITTER

function applyMode(mode) {
  const useCanvas = mode === "webcodecs" || (mode === "auto" && hasWebCodecs);

  // Remove existing child element and replace with the correct one
  const existing = player.querySelector("canvas, video");
  if (existing) existing.remove();

  if (useCanvas) {
    const canvas = document.createElement("canvas");
    player.appendChild(canvas);
    player.setAttribute("jitter", JITTER_WEBCODECS);
    jitterSlider.value = JITTER_WEBCODECS;
    jitterLabel.textContent = `${JITTER_WEBCODECS} ms`;
    infoMode.innerHTML = badge("WebCodecs (canvas)", "blue");
  } else {
    const video = document.createElement("video");
    video.autoplay = true;
    video.muted = true;
    player.appendChild(video);
    player.setAttribute("jitter", JITTER_MSE);
    jitterSlider.value = JITTER_MSE;
    jitterLabel.textContent = `${JITTER_MSE} ms`;
    infoMode.innerHTML = badge("MSE (video)", "yellow");
  }
}

// Apply initial mode — forced MSE if ?fallbackPlayer=true, otherwise auto
const initialMode = forceFallback ? "mse" : "auto";
applyMode(initialMode);
document.querySelector(`input[name=mode][value="${initialMode}"]`).checked = true;

// Wire up radio buttons
// No need to re-set url/name: the MutationObserver inside moq-watch detects the
// new child element and reactively re-initialises the backend on its own.
modeRadios.forEach((radio) => {
  radio.addEventListener("change", () => applyMode(radio.value));
});

// ─── Volume control ───────────────────────────────────────────────────────────
// Player starts muted (muted attribute on <moq-watch>). Slider unmutes on first move.
function applyVolume(pct) {
  const muted = pct === 0;
  if (muted) {
    player.setAttribute("muted", "");
    volumeIcon.textContent = "🔇";
    volumeLabel.textContent = "muted";
  } else {
    player.removeAttribute("muted");
    player.setAttribute("volume", (pct / 100).toFixed(2));
    volumeIcon.textContent = pct < 50 ? "🔉" : "🔊";
    volumeLabel.textContent = `${pct}%`;
  }
}

volumeSlider.addEventListener("input", () => applyVolume(Number(volumeSlider.value)));
volumeIcon.addEventListener("click", () => {
  if (player.hasAttribute("muted")) {
    // Unmute at last used volume, or 50% if never set
    const last = Number(volumeSlider.value) || 50;
    volumeSlider.value = last;
    applyVolume(last);
  } else {
    volumeSlider.value = 0;
    applyVolume(0);
  }
});

// ─── Jitter slider ────────────────────────────────────────────────────────────
jitterSlider.addEventListener("input", () => {
  const ms = Number(jitterSlider.value);
  jitterLabel.textContent = `${ms} ms`;
  player.setAttribute("jitter", ms);
});

// ─── Debug stats panel ────────────────────────────────────────────────────────
// Polls player.backend signals every second.
// FPS is calculated from the frameCount delta (WebCodecs only; N/A for MSE).
let prevFrameCount = 0;

function formatBytes(n) {
  if (n === undefined || n === null) return "—";
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(2)} MB`;
}

setInterval(() => {
  const b = player.backend;
  if (!b) return;

  // FPS — WebCodecs only (MSE stats not implemented in library)
  const vstats = b.video.stats.peek();
  if (vstats) {
    const fps = vstats.frameCount - prevFrameCount;
    prevFrameCount = vstats.frameCount;
    dbgFps.textContent = `${fps} fps`;
    dbgFps.className = "dbg-value ok";
    dbgVbytes.textContent = formatBytes(vstats.bytesReceived);
  } else {
    dbgFps.textContent = "N/A (MSE)";
    dbgFps.className = "dbg-value";
    dbgVbytes.textContent = "—";
  }

  // Buffer size — works for both modes
  const buffered = b.video.buffered.peek();
  if (buffered && buffered.length > 0) {
    const range = buffered[buffered.length - 1];
    const sizeMs = range.end - range.start;
    dbgBuf.textContent = `${sizeMs.toFixed(0)} ms`;
  } else {
    dbgBuf.textContent = "empty";
  }

  // Stalled
  const stalled = b.video.stalled.peek();
  dbgStalled.textContent = stalled ? "yes" : "no";
  dbgStalled.className = `dbg-value ${stalled ? "stalled" : "ok"}`;

  // Computed latency and active jitter from sync
  const latency = b.video.source.sync.latency.peek();
  dbgLatency.textContent = latency != null ? `${latency.toFixed(0)} ms` : "—";

  const activeJitter = b.jitter.peek();
  dbgJitter.textContent = activeJitter != null ? `${activeJitter} ms` : "—";

  // Codec from current video config
  const config = b.video.source.config.peek();
  dbgCodec.textContent = config?.codec ?? "—";
}, 1000);

// Disable WebCodecs radio if not supported
if (!hasWebCodecs) {
  const wcRadio = document.querySelector("input[value=webcodecs]");
  if (wcRadio) {
    wcRadio.disabled = true;
    wcRadio.closest("label").style.opacity = "0.4";
    wcRadio.closest("label").title = "WebCodecs not supported in this browser";
  }
}

// ─── Build URL candidates ─────────────────────────────────────────────────────
/**
 * Given any input (full URL, hostname, hostname:port), return the canonical
 * hostname (without port) to use for building the 4 candidates.
 */
function extractHost(raw) {
  // If it looks like a URL (has ://) parse it
  if (raw && raw.includes("://")) {
    try { return new URL(raw).hostname; } catch { /* fall through */ }
  }
  // Otherwise treat as host[:port] — strip port
  if (raw) return raw.split(":")[0];
  // Default: page hostname
  return window.location.hostname || "localhost";
}

const host = extractHost(rawUrl);

// Only probe candidates matching the page protocol.
// HTTP page can't use WebTransport (insecure context) and HTTPS page blocks HTTP mixed content.
// Try default port first, then 4443.
const proto = window.location.protocol; // "https:" or "http:"
const candidates = [
  `${proto}//${host}/moq`,
  `${proto}//${host}:4443/moq`,
];

// Warn on HTTP — WebTransport won't work, but still let them probe in case WebSocket fallback works.
if (proto === "http:") {
  const httpsUrl = window.location.href.replace(/^http:/, "https:");
  const warn = document.createElement("div");
  warn.className = "banner banner-warn";
  warn.innerHTML =
    `⚠️ <strong>Page loaded over HTTP.</strong> WebTransport requires HTTPS — playback may fail. ` +
    `<a href="${httpsUrl}" style="color:inherit;font-weight:bold;">Switch to HTTPS →</a>`;
  errors.appendChild(warn);
}

// ─── Render URL table rows ────────────────────────────────────────────────────
candidates.forEach((url, i) => {
  const tr = document.createElement("tr");
  tr.id = `url-row-${i}`;
  tr.innerHTML = `
    <td>${i + 1}</td>
    <td>${url}</td>
    <td id="url-status-${i}"><span class="status-dot dot-pending"></span>pending</td>
  `;
  urlRows.appendChild(tr);
});

function setRowStatus(i, state, label) {
  const cell = document.getElementById(`url-status-${i}`);
  const dotClass = { pending: "dot-pending", trying: "dot-trying", ok: "dot-ok", fail: "dot-fail" }[state];
  cell.innerHTML = `<span class="status-dot ${dotClass}"></span>${label}`;
  const row = document.getElementById(`url-row-${i}`);
  row.className = state === "ok" ? "url-row active" : "url-row";
}

// ─── Probe a URL ──────────────────────────────────────────────────────────────
/**
 * Probe whether an HTTP server is reachable at `url`.
 * We use a no-cors HEAD fetch — a TypeError means the server is unreachable.
 * Any other outcome (even CORS rejection) means it responded.
 */
async function probeUrl(url, timeoutMs = 4000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    await fetch(url, { method: "HEAD", mode: "no-cors", signal: controller.signal });
    return true; // responded (opaque response is fine)
  } catch (e) {
    if (e.name === "AbortError") return false; // timed out
    // TypeError = network failure (server not reachable)
    // Other errors = server responded but rejected (still reachable)
    return !(e instanceof TypeError);
  } finally {
    clearTimeout(timer);
  }
}

// ─── Try candidates in order ──────────────────────────────────────────────────
async function findWorkingUrl() {
  for (let i = 0; i < candidates.length; i++) {
    const url = candidates[i];
    setRowStatus(i, "trying", "trying…");
    infoStatus.innerHTML = badge(`probing ${i + 1}/${candidates.length}`, "yellow");

    const ok = await probeUrl(url);

    if (ok) {
      setRowStatus(i, "ok", "✓ reachable");
      // Mark remaining as skipped
      for (let j = i + 1; j < candidates.length; j++) {
        setRowStatus(j, "pending", "skipped");
      }
      return url;
    } else {
      setRowStatus(i, "fail", "✗ unreachable");
    }
  }
  return null;
}

// ─── Quality discovery ────────────────────────────────────────────────────────
// The npm @moq/lite@0.1.5 does not have connection.announced as a Signal.
// Instead we use Established.announced() — an async iterator that emits
// { path, active } entries as the relay announces/unannounces tracks.

let currentStreamId = broadcastName ?? "";
let currentBroadcasts = new Set();
let currentSelected = "";

function renderQualities() {
  qualityButtons.innerHTML = "";

  if (!currentStreamId) {
    qualityPanel.style.display = "none";
    return;
  }

  // AMS names broadcasts like: live/streamId/source, live/streamId/240p, etc.
  // Match any broadcast that has the stream ID as a path segment.
  const relevant = [...currentBroadcasts].filter((name) =>
    name.split("/").includes(currentStreamId),
  );

  if (relevant.length === 0) {
    qualityPanel.style.display = "none";
    return;
  }

  qualityPanel.style.display = "flex";

  for (const name of relevant) {
    const isSelected = name === currentSelected;
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = name.split("/").pop() ?? name;
    btn.title = name;
    btn.className = "quality-btn" + (isSelected ? " active" : "");
    btn.addEventListener("click", () => {
      player.name = name;
    });
    qualityButtons.appendChild(btn);
  }
}

// Track the active connection so we can detect stale loops on reconnect.
let activeConn = null;

player.connection.established.subscribe(async (conn) => {
  // Clear stale broadcasts whenever the connection changes.
  currentBroadcasts = new Set();
  renderQualities();

  if (!conn) return;
  activeConn = conn;

  const announced = conn.announced();

  try {
    for (;;) {
      const entry = await announced.next();
      if (!entry) break; // connection closed

      // Ignore updates from a stale connection (reconnect happened).
      if (activeConn !== conn) break;

      const name = entry.path.toString();
      if (entry.active) {
        currentBroadcasts = new Set([...currentBroadcasts, name]);
      } else {
        currentBroadcasts = new Set([...currentBroadcasts].filter((p) => p !== name));
      }
      renderQualities();
    }
  } catch {
    // Connection dropped — next established event will restart discovery.
  }
});

// Keep active quality button highlighted when player.broadcast.name changes.
player.broadcast.name.subscribe((name) => {
  currentSelected = name.toString();
  renderQualities();
});

// ─── Stream ID form ───────────────────────────────────────────────────────────
// Apply a new stream ID to the player (works before and after relay is connected).
function applyStreamId(id) {
  if (!id) return;
  currentStreamId = id;
  player.setAttribute("name", id);
  infoName.textContent = id;
  renderQualities();
  // Reflect the change in the URL bar without reloading the page.
  const url = new URL(window.location);
  url.searchParams.set("name", id);
  window.history.replaceState({}, "", url);
}

streamForm.addEventListener("submit", (e) => {
  e.preventDefault();
  applyStreamId(streamInput.value.trim());
});

// ─── Main ─────────────────────────────────────────────────────────────────────
(async () => {
  const workingUrl = await findWorkingUrl();

  if (!workingUrl) {
    showError("Could not reach any relay URL. Check that the relay server is running and the host is correct.");
    infoUrl.textContent = "none";
    infoStatus.innerHTML = badge("unreachable", "red");
    return;
  }

  infoUrl.textContent = workingUrl;
  infoStatus.innerHTML = badge("connecting…", "yellow");

  player.setAttribute("url", workingUrl);

  if (broadcastName) {
    player.setAttribute("name", broadcastName);
    infoStatus.innerHTML = badge("connected", "green");
  } else {
    infoStatus.innerHTML = badge("enter stream id", "yellow");
    streamInput.focus();
  }
})();
