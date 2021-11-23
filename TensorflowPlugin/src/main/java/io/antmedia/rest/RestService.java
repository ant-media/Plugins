package io.antmedia.rest;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import io.antmedia.plugin.TensorflowPlugin;
import io.antmedia.rest.model.Result;

@Component
@Path("/v2/tensorflow")
public class RestService {

	@Context
	protected ServletContext servletContext;

	/*
	 * Start object detection for the given stream id
	 */
	@POST
	@Path("/{streamId}/start")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response start(@PathParam("streamId") String streamId) {
		TensorflowPlugin app = getPluginApp();
		boolean result = app.startDetection(streamId, false);

		return Response.status(Status.OK).entity(new Result(result)).build();
	}
	
	/*
	 * Start object detection for the given stream id in Real Time
	 */
	@POST
	@Path("/{streamId}/startRT")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response startRealTime(@PathParam("streamId") String streamId) {
		TensorflowPlugin app = getPluginApp();
		boolean result = app.startDetection(streamId, true);

		return Response.status(Status.OK).entity(new Result(result)).build();
	}
	
	/*
	 * Stop object detection for the given stream id
	 */
	@POST
	@Path("/{streamId}/stop")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response stop(@PathParam("streamId") String streamId) {
		TensorflowPlugin app = getPluginApp();
		boolean result = app.stopDetection(streamId);

		return Response.status(Status.OK).entity(new Result(result)).build();
	}
	
	private TensorflowPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (TensorflowPlugin) appCtx.getBean("plugin.tensorflow");
	}
}
