package io.antmedia.rest;

import com.google.gson.Gson;
import io.antmedia.plugin.HLSMergerPlugin;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.io.FileNotFoundException;

@Component
@Path("/hls-merger")
public class RestService {

	@Context
	protected ServletContext servletContext;
	Gson gson = new Gson();

	
	@POST
	@Path("/merge/{fileName}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response start(@PathParam("fileName") String fileName, @Parameter(description = "Stream Id", required = true) String[] streamIds) {
		HLSMergerPlugin app = getPluginApp();
		boolean result = app.mergeStreams(fileName, streamIds);

		if (result) {
			return Response.status(Status.OK).entity("").build();
		}
		return Response.status(Status.INTERNAL_SERVER_ERROR).entity("").build();
	}

	@POST
	@Path("/stop/{fileName}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response stop(@PathParam("fileName") String fileName) {
		HLSMergerPlugin app = getPluginApp();
		boolean result = app.stopMerge(fileName);

		if (result) {
			return Response.status(Status.OK).entity("").build();
		}
		return Response.status(Status.INTERNAL_SERVER_ERROR).entity("").build();
	}
	
	@GET
	@Path("/stats")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public String getStats() {
		HLSMergerPlugin app = getPluginApp();
		return "hello";
	}
	
	private HLSMergerPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (HLSMergerPlugin) appCtx.getBean("plugin.hls-merger");
	}
}
