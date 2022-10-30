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

import io.antmedia.plugin.WebpageRecordingPlugin;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.WebApplicationContext;

import com.google.gson.Gson;

import java.net.URISyntaxException;

@Component
@Path("/webpage-recording-plugin")
public class RestService {

	@Context
	protected ServletContext servletContext;
	Gson gson = new Gson();

	
	@POST
	@Path("/startWebpageRecording")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response startWebpageRecording(@RequestBody StartWebpageRecordingRequest request) throws URISyntaxException, InterruptedException {
		WebpageRecordingPlugin app = getPluginApp();
		app.startWebpageRecording(request.getStreamId(), request.getWebsocketUrl(), request.getWebpageUrl());

		return Response.status(Status.OK).entity("").build();
	}
	
	@GET
	@Path("/stopWebpageRecording/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response stopWebpageRecording(@PathParam("streamId") String streamId) throws InterruptedException {
		WebpageRecordingPlugin app = getPluginApp();
		app.stopWebpageRecording(streamId);

		return Response.status(Status.OK).entity("").build();
	}
	
	private WebpageRecordingPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (WebpageRecordingPlugin) appCtx.getBean("plugin.webpageRecordingPlugin");
	}
}
