const sourceFrame = document.getElementById("sourceFrame");
const processedFrame = document.getElementById("processedFrame");
const streamIdInput = document.getElementById("streamIdInput");
const modelSelect = document.getElementById("modelSelect");
const windowSecondsInput = document.getElementById("windowSecondsInput");
const loadBtn = document.getElementById("loadBtn");
const refreshBtn = document.getElementById("refreshBtn");
const timelineBar = document.getElementById("timelineBar");
const prevDetectionBtn = document.getElementById("prevDetectionBtn");
const nextDetectionBtn = document.getElementById("nextDetectionBtn");
const detectionCounter = document.getElementById("detectionCounter");
const statusText = document.getElementById("statusText");

let detections = [];
let selectedDetectionIndex = -1;
let pollTimer = null;
let timelineStartSec = 0;
let timelineEndSec = 0;

function basePath() {
  return window.location.origin;
}

function currentAppName() {
  const params = new URLSearchParams(window.location.search);
  const fromQuery = params.get("app");
  if (fromQuery) {
    return fromQuery;
  }
  const pathParts = window.location.pathname.split("/").filter(Boolean);
  return pathParts.length > 0 ? pathParts[0] : "LiveApp";
}

function buildSourceHlsUrl(streamId) {
  return `${basePath()}/${currentAppName()}/streams/${streamId}.m3u8`;
}

function modelToPluginSuffix(model) {
  const mapping = {
    face_detections: "FaceDetectionPlugin",
    yolo_general_detections: "YoloGeneralDetectionPlugin",
    pose_detections: "YoloPoseDetectionPlugin",
  };
  return mapping[model] || "FaceDetectionPlugin";
}

function buildProcessedHlsUrl(streamId, model) {
  const pluginSuffix = modelToPluginSuffix(model);
  return `${basePath()}/${currentAppName()}/streams/${streamId}_${pluginSuffix}.m3u8`;
}

function buildPlayPageUrl(streamId) {
  return `${basePath()}/${currentAppName()}/play.html?id=${encodeURIComponent(streamId)}&playOrder=hls`;
}

function buildDetectionApiUrl(model, streamId, seconds) {
  return `${basePath()}/${currentAppName()}/rest/python-plugin/detections/${encodeURIComponent(model)}/${encodeURIComponent(streamId)}/last/${seconds}`;
}

function setStatus(text) {
  statusText.textContent = text;
}

function parseDetectionTimestamp(d) {
  if (typeof d.timestamp_sec === "number") {
    return d.timestamp_sec;
  }
  if (typeof d.timestamp_sec === "string") {
    const v = Number.parseFloat(d.timestamp_sec);
    return Number.isFinite(v) ? v : 0;
  }
  return 0;
}

function renderTimeline() {
  timelineBar.innerHTML = "";
  if (!detections.length) {
    detectionCounter.textContent = "No detections";
    return;
  }

  detectionCounter.textContent = `${detections.length} detections`;

  const windowSpan = Math.max(1, timelineEndSec - timelineStartSec);
  detections.forEach((d, idx) => {
    const t = parseDetectionTimestamp(d);
    const ratio = Math.min(1, Math.max(0, (t - timelineStartSec) / windowSpan));
    const mark = document.createElement("div");
    mark.className = "timeline-mark";
    if (idx === selectedDetectionIndex) {
      mark.classList.add("active");
    }
    mark.style.left = `${ratio * 100}%`;
    mark.title = `t=${t.toFixed(2)} sec`;
    mark.addEventListener("click", (e) => {
      e.stopPropagation();
      jumpToDetection(idx);
    });
    timelineBar.appendChild(mark);
  });
}

function jumpToDetection(index) {
  if (index < 0 || index >= detections.length) {
    return;
  }
  selectedDetectionIndex = index;
  const t = parseDetectionTimestamp(detections[index]);
  setStatus(`Selected detection ${index + 1}/${detections.length} at ${t.toFixed(2)}s (seek disabled in embedded play.html).`);
  renderTimeline();
}

function jumpRelative(step) {
  if (!detections.length) {
    return;
  }
  let next = selectedDetectionIndex + step;
  if (selectedDetectionIndex < 0) {
    next = step > 0 ? 0 : detections.length - 1;
  }
  next = Math.max(0, Math.min(detections.length - 1, next));
  jumpToDetection(next);
}

async function refreshDetections() {
  const streamId = streamIdInput.value.trim();
  const model = modelSelect.value;
  const seconds = Number.parseInt(windowSecondsInput.value, 10) || 30;
  if (!streamId) {
    setStatus("Please enter stream ID.");
    return;
  }
  setStatus("Loading detections...");
  try {
    const resp = await fetch(buildDetectionApiUrl(model, streamId, seconds));
    if (!resp.ok) {
      throw new Error(`HTTP ${resp.status}`);
    }
    const data = await resp.json();
    detections = Array.isArray(data) ? data.slice() : [];
    detections.sort((a, b) => parseDetectionTimestamp(a) - parseDetectionTimestamp(b));

    if (detections.length) {
      timelineEndSec = parseDetectionTimestamp(detections[detections.length - 1]);
      timelineStartSec = Math.max(0, timelineEndSec - seconds);
    } else {
      timelineEndSec = 0;
      timelineStartSec = 0;
    }

    if (selectedDetectionIndex >= detections.length) {
      selectedDetectionIndex = detections.length - 1;
    }
    renderTimeline();
    setStatus(`Loaded ${detections.length} detections.`);
  } catch (err) {
    setStatus(`Failed to load detections: ${err.message}`);
  }
}

function loadStreams() {
  const streamId = streamIdInput.value.trim();
  const model = modelSelect.value;
  if (!streamId) {
    setStatus("Please enter stream ID.");
    return;
  }
  const sourcePlayUrl = buildPlayPageUrl(streamId);
  const processedPlayUrl = buildPlayPageUrl(`${streamId}_${modelToPluginSuffix(model)}`);
  sourceFrame.src = sourcePlayUrl;
  processedFrame.src = processedPlayUrl;
  setStatus("Streams loaded.");
  refreshDetections();
}

function schedulePolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
  }
  pollTimer = setInterval(() => {
    refreshDetections();
  }, 5000);
}

timelineBar.addEventListener("click", (e) => {
  if (!detections.length) {
    return;
  }
  const rect = timelineBar.getBoundingClientRect();
  const ratio = Math.min(1, Math.max(0, (e.clientX - rect.left) / rect.width));
  const target = timelineStartSec + ratio * Math.max(1, timelineEndSec - timelineStartSec);

  let nearestIndex = 0;
  let nearestDelta = Number.POSITIVE_INFINITY;
  detections.forEach((d, idx) => {
    const delta = Math.abs(parseDetectionTimestamp(d) - target);
    if (delta < nearestDelta) {
      nearestDelta = delta;
      nearestIndex = idx;
    }
  });
  jumpToDetection(nearestIndex);
});

loadBtn.addEventListener("click", loadStreams);
refreshBtn.addEventListener("click", refreshDetections);
prevDetectionBtn.addEventListener("click", () => jumpRelative(-1));
nextDetectionBtn.addEventListener("click", () => jumpRelative(1));

schedulePolling();
