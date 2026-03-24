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
