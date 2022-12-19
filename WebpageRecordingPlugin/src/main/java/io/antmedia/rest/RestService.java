package io.antmedia.rest;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import io.antmedia.plugin.WebpageRecordingPlugin;
import io.antmedia.rest.model.Result;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.WebApplicationContext;

import java.net.URISyntaxException;

@Component
@Path("/webpage/record")
public class RestService {

	@Context
	protected ServletContext servletContext;

	
	@POST
	@Path("/start")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result startWebpageRecording(@RequestBody Endpoint request, @Context UriInfo uriInfo, @QueryParam("streamId") String streamId) throws URISyntaxException, InterruptedException {
		if (uriInfo == null) {
			return new Result(false, "Bad request");
		}
		
		String websocketScheme = ("https".equals(uriInfo.getBaseUri().getScheme())) ? "wss" : "ws";
		String applicationName = uriInfo.getBaseUri().getPath().split("/")[1];
		String websocketUrl = websocketScheme + "://" + uriInfo.getBaseUri().getHost() + ":" + uriInfo.getBaseUri().getPort() + "/" + applicationName + "/websocket";

		WebpageRecordingPlugin app = getPluginApp();

		return app.startWebpageRecording(streamId, websocketUrl, request.getUrl());
	}
	
	@POST
	@Path("/stop/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result stopWebpageRecording(@PathParam("streamId") String streamId) throws InterruptedException {
		WebpageRecordingPlugin app = getPluginApp();
		return app.stopWebpageRecording(streamId);
	}
	
	private WebpageRecordingPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (WebpageRecordingPlugin) appCtx.getBean("plugin.webpageRecordingPlugin");
	}
}
