package io.antmedia.rest;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import io.antmedia.plugin.PythonWrapperPlugin;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Component
@Path("/v1/python-wrapper-plugin")
public class RestService {

	@Context
	protected ServletContext servletContext;

	@POST
	@Path("/start/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response startPythonProcess(@PathParam("streamId") String streamId) {
		PythonWrapperPlugin app = getPythonWrapperApp();
		app.startPythonProcess(streamId);

		return Response.status(Status.OK).entity("").build();
	}

	@POST
	@Path("/stop/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response stopPythonProcess(@PathParam("streamId") String streamId) {
		PythonWrapperPlugin app = getPythonWrapperApp();
		app.stopPythonProcess(streamId);

		return Response.status(Status.OK).entity("").build();
	}
	
	private PythonWrapperPlugin getPythonWrapperApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (PythonWrapperPlugin) appCtx.getBean("plugin.pythonwrapperplugin");
	}
}
