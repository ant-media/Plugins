package io.antmedia.rest;

import io.antmedia.plugin.SnapshotPlugin;
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

@Component
@Path("/snapshot-plugin")
public class SnapshotRestService {

	@Context
	protected ServletContext servletContext;
	
	@POST
	@Path("/snapshot/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response snapshot(@PathParam("streamId") String streamId) {
		SnapshotPlugin app = getPluginApp();
		boolean success = app.takeSnapshot(streamId);
		
		if (success) {
			return Response.status(Status.OK).entity("{\"success\": true, \"message\": \"Snapshot scheduled\"}").build();
		} else {
			return Response.status(Status.BAD_REQUEST).entity("{\"success\": false, \"message\": \"Stream not active\"}").build();
		}
	}
	
	@POST
	@Path("/timelapse/start/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response startTimelapse(@PathParam("streamId") String streamId) {
		SnapshotPlugin app = getPluginApp();
		boolean success = app.startTimelapse(streamId);
		
		if (success) {
			return Response.status(Status.OK).entity("{\"success\": true, \"message\": \"Timelapse started\"}").build();
		} else {
			return Response.status(Status.BAD_REQUEST).entity("{\"success\": false, \"message\": \"Stream not active\"}").build();
		}
	}
	
	@POST
	@Path("/timelapse/stop/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response stopTimelapse(@PathParam("streamId") String streamId) {
		SnapshotPlugin app = getPluginApp();
		boolean success = app.stopTimelapse(streamId);
		
		if (success) {
			return Response.status(Status.OK).entity("{\"success\": true, \"message\": \"Timelapse stopped\"}").build();
		} else {
			return Response.status(Status.BAD_REQUEST).entity("{\"success\": false, \"message\": \"Timelapse not active or stream not found\"}").build();
		}
	}
	
	@GET
	@Path("/timelapse/status/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTimelapseStatus(@PathParam("streamId") String streamId) {
		SnapshotPlugin app = getPluginApp();
		boolean active = app.getTimelapseStatus(streamId);
		
		String status = active ? "active" : "inactive";
		return Response.status(Status.OK).entity("{\"success\": true, \"status\": \"" + status + "\"}").build();
	}
	
	private SnapshotPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (SnapshotPlugin) appCtx.getBean("plugin.snapshot-plugin");
	}
}
