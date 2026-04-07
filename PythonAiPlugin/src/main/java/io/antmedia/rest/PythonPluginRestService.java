package io.antmedia.rest;

import java.util.List;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.antmedia.app.JepPythonBridge;
import io.antmedia.app.PluginResultDatabase;
import io.antmedia.plugin.PythonPlugin;

@Component
@Path("/python-plugin")
public class PythonPluginRestService {

  @Context
  protected ServletContext servletContext;
  Gson gson = new Gson();

  @POST
  @Path("/register/{streamId}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response register(@PathParam("streamId") String streamId) {
    PythonPlugin app = getPluginApp();
    app.register(streamId);

    return Response.status(Status.OK).entity("").build();
  }

  /**
   * Enqueue a vision job for the Python {@code OllamaVisionQueuePlugin} (local Ollama, ollama-vision-mcp-style modes).
   * Body: {@code { "streamId", "mode", "prompt" (optional) }}. Modes: analyze_image, describe_image, identify_objects,
   * read_text, custom. Results land in SQLite table {@code ollama_vision_queue_results}; query via
   * {@code GET .../detections/ollama_vision_queue_results/{streamId}/seconds/{seconds}}.
   */
  @POST
  @Path("/vision-queue/enqueue")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response enqueueVisionQueue(String body) {
    if (body == null || body.isBlank()) {
      return Response.status(Status.BAD_REQUEST).entity("{\"error\":\"request body required\"}").build();
    }
    JsonObject req;
    try {
      req = JsonParser.parseString(body).getAsJsonObject();
    } catch (Exception e) {
      return Response.status(Status.BAD_REQUEST).entity("{\"error\":\"invalid JSON\"}").build();
    }
    if (!req.has("streamId") || !req.has("mode")) {
      return Response.status(Status.BAD_REQUEST)
          .entity("{\"error\":\"streamId and mode are required\"}")
          .build();
    }
    String streamId = req.get("streamId").getAsString();
    String mode = req.get("mode").getAsString();
    String prompt = "";
    if (req.has("prompt") && !req.get("prompt").isJsonNull()) {
      prompt = req.get("prompt").getAsString();
    }
    if (streamId == null || streamId.isBlank() || mode == null || mode.isBlank()) {
      return Response.status(Status.BAD_REQUEST)
          .entity("{\"error\":\"streamId and mode must be non-empty\"}")
          .build();
    }
    JepPythonBridge bridge = JepPythonBridge.getInstance();
    if (!bridge.isInitialized()) {
      return Response.status(Status.SERVICE_UNAVAILABLE)
          .entity("{\"error\":\"Python/JEP bridge not initialized\"}")
          .build();
    }
    boolean ok = bridge.enqueueOllamaVisionJob(streamId.trim(), mode.trim(), prompt);
    JsonObject out = new JsonObject();
    out.addProperty("accepted", ok);
    out.addProperty("streamId", streamId.trim());
    out.addProperty("mode", mode.trim());
    if (!ok) {
      out.addProperty(
          "hint",
          "Check mode spelling (analyze_image, describe_image, identify_objects, read_text, custom); "
              + "analyze_image and custom require a non-empty prompt.");
    }
    return ok ? Response.ok(gson.toJson(out)).build()
        : Response.status(Status.BAD_REQUEST).entity(gson.toJson(out)).build();
  }

  /**
   * Continuous yes/no monitoring: multiple prompts per stream. Each interval the vision model is asked
   * (JSON detected/confidence); matches are stored in SQLite table {@code monitor_alerts}.
   * Body: {@code { "streamId", "prompts": ["...", "..."], "threshold" (0..1), "intervalSec" }}.
   */
  @POST
  @Path("/vision-monitor/config")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response setVisionMonitor(String body) {
    if (body == null || body.isBlank()) {
      return Response.status(Status.BAD_REQUEST).entity("{\"error\":\"request body required\"}").build();
    }
    JsonObject req;
    try {
      req = JsonParser.parseString(body).getAsJsonObject();
    } catch (Exception e) {
      return Response.status(Status.BAD_REQUEST).entity("{\"error\":\"invalid JSON\"}").build();
    }
    if (!req.has("streamId") || !req.has("prompts")) {
      return Response.status(Status.BAD_REQUEST)
          .entity("{\"error\":\"streamId and prompts (array) are required\"}")
          .build();
    }
    String streamId = req.get("streamId").getAsString().trim();
    if (streamId.isBlank()) {
      return Response.status(Status.BAD_REQUEST).entity("{\"error\":\"streamId must be non-empty\"}").build();
    }
    if (!req.get("prompts").isJsonArray()) {
      return Response.status(Status.BAD_REQUEST).entity("{\"error\":\"prompts must be a JSON array\"}").build();
    }
    String promptsJson = gson.toJson(req.get("prompts").getAsJsonArray());
    double threshold = 0.6;
    if (req.has("threshold") && !req.get("threshold").isJsonNull()) {
      threshold = req.get("threshold").getAsDouble();
      if (threshold < 0.0 || threshold > 1.0) {
        threshold = 0.6;
      }
    }
    double intervalSec = 10.0;
    if (req.has("intervalSec") && !req.get("intervalSec").isJsonNull()) {
      intervalSec = Math.max(0.5, req.get("intervalSec").getAsDouble());
    }
    JepPythonBridge bridge = JepPythonBridge.getInstance();
    if (!bridge.isInitialized()) {
      return Response.status(Status.SERVICE_UNAVAILABLE)
          .entity("{\"error\":\"Python/JEP bridge not initialized\"}")
          .build();
    }
    boolean ok = bridge.setVisionMonitorConfig(streamId, promptsJson, threshold, intervalSec);
    JsonObject out = new JsonObject();
    out.addProperty("ok", ok);
    out.addProperty("streamId", streamId);
    return ok ? Response.ok(gson.toJson(out)).build()
        : Response.status(Status.BAD_REQUEST).entity("{\"error\":\"failed to apply monitor config\"}").build();
  }

  @POST
  @Path("/vision-monitor/clear/{streamId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response clearVisionMonitor(@PathParam("streamId") String streamId) {
    if (streamId == null || streamId.isBlank()) {
      return Response.status(Status.BAD_REQUEST).entity("{\"error\":\"streamId required\"}").build();
    }
    JepPythonBridge bridge = JepPythonBridge.getInstance();
    if (!bridge.isInitialized()) {
      return Response.status(Status.SERVICE_UNAVAILABLE)
          .entity("{\"error\":\"Python/JEP bridge not initialized\"}")
          .build();
    }
    boolean ok = bridge.clearVisionMonitor(streamId.trim());
    JsonObject out = new JsonObject();
    out.addProperty("ok", ok);
    return Response.ok(gson.toJson(out)).build();
  }

  @GET
  @Path("/detections/{model}/{streamId}/last/{seconds}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDetectionsByLastSeconds(
      @PathParam("model") String model,
      @PathParam("streamId") String streamId,
      @PathParam("seconds") int seconds) {
    return getDetectionsBySeconds(model, streamId, seconds);
  }

  @GET
  @Path("/detections/{model}/{streamId}/seconds/{seconds}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDetectionsBySeconds(
      @PathParam("model") String model,
      @PathParam("streamId") String streamId,
      @PathParam("seconds") int seconds) {
    if (seconds <= 0) {
      return Response.status(Status.BAD_REQUEST)
          .entity("{\"error\": \"seconds must be a positive number\"}")
          .build();
    }

    if (!isValidModelName(model)) {
      return Response.status(Status.BAD_REQUEST)
          .entity("{\"error\": \"model must be a valid table name\"}")
          .build();
    }

    PluginResultDatabase db = PluginResultDatabase.getInstance();
    List<String> results = db.getResultsByStreamIdAndRecentSeconds(model, streamId, seconds);
    String json = "[" + String.join(",", results) + "]";
    return Response.ok(json).build();
  }

  @GET
  @Path("/detections/{model}/{streamId}/count/{lastn}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getDetectionsByCount(
      @PathParam("model") String model,
      @PathParam("streamId") String streamId,
      @PathParam("lastn") int lastn) {
    if (lastn <= 0) {
      return Response.status(Status.BAD_REQUEST)
          .entity("{\"error\": \"lastn must be a positive number\"}")
          .build();
    }

    if (!isValidModelName(model)) {
      return Response.status(Status.BAD_REQUEST)
          .entity("{\"error\": \"model must be a valid table name\"}")
          .build();
    }

    PluginResultDatabase db = PluginResultDatabase.getInstance();
    List<String> results = db.getResultsByStreamId(model, streamId, lastn);
    String json = "[" + String.join(",", results) + "]";
    return Response.ok(json).build();
  }

  private boolean isValidModelName(String model) {
    return model != null && model.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
  }

  private PythonPlugin getPluginApp() {
    ApplicationContext appCtx = (ApplicationContext) servletContext
        .getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
    return (PythonPlugin) appCtx.getBean("plugin.pythonplugin");
  }
}
