import "@moq/publish/element";
import "@moq/publish/support/element";

// ─── Elements ─────────────────────────────────────────────────────────────────
const publisher    = document.getElementById("publisher");
const errors       = document.getElementById("errors");
const urlRows      = document.getElementById("url-rows");
const infoName     = document.getElementById("info-name");
const infoUrl      = document.getElementById("info-url");
const infoStatus   = document.getElementById("info-status");
const streamInput  = document.getElementById("stream-input");
const sourceRadios = document.querySelectorAll("input[name=source]");
const mutedCheck   = document.getElementById("chk-muted");
const invisCheck   = document.getElementById("chk-invisible");
const btnPublish        = document.getElementById("btn-publish");
const btnStop           = document.getElementById("btn-stop");
const broadcastPreview  = document.getElementById("broadcast-preview");

// ─── Helpers ──────────────────────────────────────────────────────────────────
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

// App name comes from the URL path: /live/moq/publish.html → "live"
const appName = window.location.pathname.split("/").filter(Boolean)[0] ?? "live";

// Stream ID: ?name= or ?broadcast= or ?id=, otherwise generate a random one.
function randomStreamId() {
  return Math.random().toString(36).slice(2, 9);
}

const initialStreamId =
  params.get("name") ??
  params.get("broadcast") ??
  params.get("id") ??
  randomStreamId();

// Source host: ?url= (full URL or just hostname[:port]), or ?host=, or page hostname
const rawUrl = params.get("url") ?? params.get("host") ?? null;

streamInput.value = initialStreamId;
infoName.textContent = `${appName}/${initialStreamId}/publish`;

// Live-update the broadcast path preview as user types.
function updateBroadcastPreview() {
  const id = streamInput.value.trim();
  broadcastPreview.textContent = id ? `→ ${appName}/${id}/publish` : "";
}
streamInput.addEventListener("input", updateBroadcastPreview);
updateBroadcastPreview();

// ─── WebCodecs check (required for encoding) ──────────────────────────────────
const hasWebCodecs = typeof VideoEncoder !== "undefined";

if (!hasWebCodecs) {
  const warn = document.createElement("div");
  warn.className = "banner banner-warn";
  warn.innerHTML =
    `⚠️ <strong>WebCodecs is not supported in this browser.</strong> ` +
    `Publishing requires WebCodecs for video encoding. Chrome 94+, Edge 94+, or Safari 17.4+ required.`;
  errors.appendChild(warn);
}

// ─── Source switching ─────────────────────────────────────────────────────────
sourceRadios.forEach((radio) => {
  radio.addEventListener("change", () => {
    publisher.setAttribute("source", radio.value); // "camera" | "screen"
  });
});

// ─── Mute / invisible toggles ─────────────────────────────────────────────────
mutedCheck.addEventListener("change", () => {
  mutedCheck.checked
    ? publisher.setAttribute("muted", "")
    : publisher.removeAttribute("muted");
});

invisCheck.addEventListener("change", () => {
  invisCheck.checked
    ? publisher.setAttribute("invisible", "")
    : publisher.removeAttribute("invisible");
});

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
      `Publishing will fall back to WebSocket (higher latency). ` +
      `<a href="${httpsUrl}" style="color:inherit;font-weight:bold;">Switch to HTTPS →</a> for low-latency.`;
  } else {
    warn.innerHTML =
      `⚠️ <strong>WebTransport not supported in this browser.</strong> ` +
      `Falling back to WebSocket — try Chrome/Edge 97+ or Firefox 114+ for low-latency.`;
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

  // http:// uses the lib's dev cert-pin flow — probe that endpoint instead of WT.
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

async function findWorkingUrl() {
  for (let i = 0; i < candidates.length; i++) {
    const url = candidates[i];
    setRowStatus(i, "trying", "trying…");
    infoStatus.innerHTML = badge(`probing ${i + 1}/${candidates.length}`, "yellow");

    const ok = await probeUrl(url);

    if (ok) {
      setRowStatus(i, "ok", "✓ reachable");
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

// ─── Phase 1: Start preview immediately ──────────────────────────────────────
// Setting source="camera" triggers getUserMedia(). The <video> child inside
// <moq-publish> automatically receives the raw video track for local preview.
// No relay URL is set yet — nothing is published to the network.
publisher.setAttribute("source", "camera");
infoStatus.innerHTML = badge("preview", "blue");

// ─── Phase 2: Publish on button click ────────────────────────────────────────
btnPublish.addEventListener("click", async () => {
  const streamId = streamInput.value.trim();
  if (!streamId) {
    streamInput.focus();
    return;
  }

  btnPublish.disabled = true;
  infoStatus.innerHTML = badge("probing relay…", "yellow");

  const workingUrl = await findWorkingUrl();

  if (!workingUrl) {
    showError("Could not reach any relay URL. Check that the relay server is running and the host is correct.");
    infoUrl.textContent = "none";
    infoStatus.innerHTML = badge("unreachable", "red");
    btnPublish.disabled = false;
    return;
  }

  // AMS expects: {appName}/{streamId}/publish
  // MoQAnnouncePoller polls /announced/moq/{appName} and detects paths ending in /publish.
  const broadcastName = `${appName}/${streamId}/publish`;

  infoUrl.textContent = workingUrl;
  infoName.textContent = broadcastName;

  // Force H.264. moq-cli's fmp4 exporter only supports H.264/H.265, but browsers with
  // VP9 hardware encoding (macOS/Apple Silicon) auto-select VP9 first → publish fails with
  // "unsupported video codec for fmp4 export". The encoder filters its candidate list with
  // `codec.startsWith(required)`, so "avc1" narrows selection to avc1.* (still tries
  // hardware first, then software) without locking a specific profile.
  // Done here (not at module load) so `publisher.broadcast` is guaranteed initialized,
  // and before `url` is set so the encoder's reactive config picks it up before connecting.
  publisher.broadcast.video.hd.config.set({ codec: "avc1" });

  publisher.setAttribute("url", workingUrl);
  publisher.setAttribute("name", broadcastName);

  infoStatus.innerHTML = badge("publishing", "green");
  btnPublish.style.display = "none";
  btnStop.style.display = "inline-block";
  streamInput.disabled = true;
});

// ─── Stop publishing ──────────────────────────────────────────────────────────
// Removing the url attribute disconnects the relay while keeping the
// camera/preview stream alive locally.
btnStop.addEventListener("click", () => {
  publisher.removeAttribute("url");
  infoUrl.textContent = "—";
  infoStatus.innerHTML = badge("preview", "blue");
  btnStop.style.display = "none";
  btnPublish.style.display = "inline-block";
  btnPublish.disabled = false;
  streamInput.disabled = false;
  candidates.forEach((_, i) => setRowStatus(i, "pending", "pending"));
});
