import "@moq/watch/element";
import "@moq/watch/ui";
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
const publishLink  = document.getElementById("publish-link");
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

// Jitter buffer override: ?latency=<ms>, or "real-time" to size it from RTT instead.
// The page default lives on the element; see the note there before lowering it.
const latency = params.get("latency");
if (latency) player.setAttribute("latency", latency);

// Send the publish page to the same relay we were pointed at.
if (rawUrl) publishLink.href = `publish.html?url=${encodeURIComponent(rawUrl)}`;

// Pre-fill the stream input from URL params (if provided)
streamInput.value = broadcastName ?? "";
infoName.textContent = broadcastName ?? "—";

// ─── WebCodecs check ─────────────────────────────────────────────────────────
// Required: the library dropped its MSE renderer, a <canvas> is the only output.
if (typeof VideoDecoder === "undefined") {
  showError("WebCodecs is not supported in this browser. Chrome 94+, Edge 94+, or Safari 17.4+ required.");
  infoMode.innerHTML = badge("unsupported", "red");
} else {
  infoMode.innerHTML = badge("WebCodecs", "blue");
}

// ─── Debug stats panel ────────────────────────────────────────────────────────
// Polls the decoder and sync signals every second. FPS is the frameCount delta.
let prevFrameCount = 0;

function formatBytes(n) {
  if (n === undefined || n === null) return "—";
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(2)} MB`;
}

setInterval(() => {
  const stats = player.video.out.stats.peek();
  if (stats) {
    dbgFps.textContent = `${stats.frameCount - prevFrameCount} fps`;
    dbgFps.className = "dbg-value ok";
    prevFrameCount = stats.frameCount;
    dbgVbytes.textContent = formatBytes(stats.bytesReceived);
  }

  const buffered = player.video.out.buffered.peek();
  const range = buffered[buffered.length - 1];
  dbgBuf.textContent = range ? `${(range.end - range.start).toFixed(0)} ms` : "empty";

  const stalled = player.video.out.stalled.peek();
  dbgStalled.textContent = stalled ? "yes" : "no";
  dbgStalled.className = `dbg-value ${stalled ? "stalled" : "ok"}`;

  dbgLatency.textContent = `${player.sync.out.buffer.peek().toFixed(0)} ms`;
  dbgJitter.textContent = `${player.sync.out.jitter.peek().toFixed(0)} ms`;
  dbgCodec.textContent = player.video.source.out.config.peek()?.codec ?? "—";
}, 1000);

// ─── Build URL candidates ─────────────────────────────────────────────────────
function formatHost(hostname) {
  return hostname.includes(":") ? `[${hostname}]` : hostname;
}

function parseRawUrl(raw) {
  if (!raw) return {};
  if (raw.includes("://")) {
    try {
      const u = new URL(raw);
      return { hostname: u.hostname, port: u.port || null, fullUrl: raw };
    } catch {}
  }
  try {
    const u = new URL(`https://${raw}/`);
    return { hostname: u.hostname, port: u.port || null };
  } catch {
    return {};
  }
}

function buildCandidates(rawUrl) {
  const parsed = parseRawUrl(rawUrl);
  const hostname = parsed.hostname || window.location.hostname || "localhost";
  const h = formatHost(hostname);
  const pageIsSecure = window.location.protocol === "https:";
  const list = [];

  if (parsed.fullUrl) {
    try {
      const u = new URL(parsed.fullUrl);
      if (!u.pathname || u.pathname === "/") u.pathname = "/moq";
      list.push(u.toString().replace(/\/$/, ""));
    } catch {}
  } else if (parsed.port) {
    list.push(`${pageIsSecure ? "https:" : "http:"}//${h}:${parsed.port}/moq`);
  }

  if (pageIsSecure) {
    list.push(`https://${h}/moq`);
    list.push(`https://${h}:4443/moq`);
  } else {
    list.push(`http://${h}/moq`);
    list.push(`http://${h}:4443/moq`);
    list.push(`https://${h}/moq`);
    list.push(`https://${h}:4443/moq`);
  }

  return [...new Set(list)];
}

const candidates = buildCandidates(rawUrl);

if (typeof WebTransport === "undefined") {
  const warn = document.createElement("div");
  warn.className = "banner banner-warn";
  if (window.location.protocol !== "https:") {
    const httpsUrl = window.location.href.replace(/^http:/, "https:");
    warn.innerHTML =
      `⚠️ <strong>WebTransport unavailable on this page.</strong> ` +
      `Playback will fall back to WebSocket (higher latency). ` +
      `<a href="${httpsUrl}" style="color:inherit;font-weight:bold;">Switch to HTTPS →</a> for low-latency.`;
  } else {
    warn.innerHTML =
      `⚠️ <strong>WebTransport not supported in this browser.</strong> ` +
      `Falling back to WebSocket, try Chrome/Edge 97+ or Firefox 114+ for low-latency.`;
  }
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
async function probeUrl(url, timeoutMs = 4000) {
  const parsed = new URL(url);

  // http:// uses the lib's dev cert-pin flow, so probe that endpoint instead of WT.
  if (parsed.protocol === "http:") {
    const fp = new URL(parsed);
    fp.pathname = "/certificate.sha256";
    fp.search = "";
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const res = await fetch(fp.toString(), { signal: controller.signal });
      return res.ok;
    } catch {
      return false;
    } finally {
      clearTimeout(timer);
    }
  }

  if (typeof WebTransport === "undefined") return false;

  let wt;
  try { wt = new WebTransport(url); } catch { return false; }

  let timer;
  const timeout = new Promise((resolve) => {
    timer = setTimeout(() => resolve(false), timeoutMs);
  });

  try {
    return await Promise.race([wt.ready.then(() => true, () => false), timeout]);
  } finally {
    clearTimeout(timer);
    try { wt.close(); } catch {}
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
// AMS publishes each ABR rung as its own broadcast, so switching quality means
// pointing the player at a different name rather than picking a catalog rendition.

let currentStreamId = broadcastName ?? "";
const currentBroadcasts = new Set();
let currentSelected = broadcastName ?? "";

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
      currentSelected = name;
      renderQualities();
    });
    qualityButtons.appendChild(btn);
  }
}

// This stream spans reconnects: the library retracts everything on a drop and
// re-announces on reconnect, so the set stays accurate without tracking sessions.
(async () => {
  const announced = player.connection.announced();
  for (;;) {
    const entry = await announced.next();
    if (!entry) break;

    const name = entry.path.toString();
    if (entry.active) currentBroadcasts.add(name);
    else currentBroadcasts.delete(name);
    renderQualities();
  }
})();

// ─── Stream ID form ───────────────────────────────────────────────────────────
// Apply a new stream ID to the player (works before and after relay is connected).
function applyStreamId(id) {
  if (!id) return;
  currentStreamId = id;
  currentSelected = id;
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
