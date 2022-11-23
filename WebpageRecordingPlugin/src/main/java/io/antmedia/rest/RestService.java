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
import javax.ws.rs.core.UriInfo;

import io.antmedia.plugin.WebpageRecordingPlugin;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.WebApplicationContext;

import com.google.gson.Gson;

import java.net.URISyntaxException;

@Component
@Path("/v2/webpage/record")
public class RestService {

	@Context
	protected ServletContext servletContext;
	Gson gson = new Gson();

	
	@POST
	@Path("/start")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response startWebpageRecording(@RequestBody StartWebpageRecordingRequest request, @Context UriInfo uriInfo) throws URISyntaxException, InterruptedException {
		if (uriInfo == null) {
			return Response.status(Response.Status.BAD_REQUEST).build();
		}
		
		String websocketScheme = ("https".equals(uriInfo.getBaseUri().getScheme())) ? "wss" : "ws";
		String applicationName = uriInfo.getBaseUri().getPath().split("/")[1];
		String websocketUrl = websocketScheme + "://" + uriInfo.getBaseUri().getHost() + ":" + uriInfo.getBaseUri().getPort() + "/" + applicationName + "/websocket";

		WebpageRecordingPlugin app = getPluginApp();
		ResponsePair responsePair = app.startWebpageRecording(request.getStreamId(), websocketUrl, request.getWebpageUrl());

		return Response.status(responsePair.getResponseCode()).entity(responsePair.getResponse()).build();
	}
	
	@GET
	@Path("/stop/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response stopWebpageRecording(@PathParam("streamId") String streamId) throws InterruptedException {
		WebpageRecordingPlugin app = getPluginApp();
		ResponsePair responsePair = app.stopWebpageRecording(streamId);

		return Response.status(responsePair.getResponseCode()).entity(responsePair.getResponse()).build();
	}
	
	private WebpageRecordingPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (WebpageRecordingPlugin) appCtx.getBean("plugin.webpageRecordingPlugin");
	}
}
