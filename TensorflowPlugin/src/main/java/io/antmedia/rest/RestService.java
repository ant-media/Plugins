package io.antmedia.rest;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import io.antmedia.plugin.TensorflowPlugin;
import io.antmedia.rest.model.Result;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

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
	public Response start(@PathParam("streamId") String streamId, 
			@Parameter(description = "To create image files instead of realtime demonstrations.", required = false) @QueryParam("offline") boolean offline) {
		TensorflowPlugin app = getPluginApp();
		
		boolean result = app.startDetection(streamId, !offline);

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
