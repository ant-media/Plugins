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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.WebApplicationContext;

import io.antmedia.plugin.PythonWrapperPlugin;

@Component
@Path("/python-wrapper-plugin")
public class RestService {

	@Context
	protected ServletContext servletContext;
	
	@POST
	@Path("/register/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response register(@RequestBody Endpoint request, @PathParam("streamId") String streamId) {
		PythonWrapperPlugin app = getPluginApp();
		app.register(streamId, request.getPythonScriptPath());

		return Response.status(Status.OK).entity("").build();
	}
	
	@GET
	@Path("/stats")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public String getStats() {
		PythonWrapperPlugin app = getPluginApp();
		return app.getStats();
	}
	
	private PythonWrapperPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (PythonWrapperPlugin) appCtx.getBean("plugin.pythonwrapperplugin");
	}
}
