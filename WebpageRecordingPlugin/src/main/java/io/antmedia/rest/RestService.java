package io.antmedia.rest;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import io.antmedia.plugin.WebpageRecordingPlugin;
import io.antmedia.rest.model.Result;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.WebApplicationContext;

@Component
@Path("/v1/webpage-recording")
public class RestService {

	@Context
	protected ServletContext servletContext;

	/*
	 * Start recording of a webpage with given id
	 * If id is not provided, a random id will be generated
	 *
	 * @PathParam id: webpage recording streamId
	 * @BodyParam url: url of the webpage to be recorded and it's mandatory
	 */
	@POST
	@Path("/start")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result startWebpageRecording(@RequestBody Endpoint request, @Context UriInfo uriInfo, @QueryParam("streamId") String streamId) {
		if (uriInfo == null) {
			return new Result(false, "Bad request");
		}

		String websocketScheme = ("https".equals(uriInfo.getBaseUri().getScheme())) ? "wss" : "ws";
		String applicationName = uriInfo.getBaseUri().getPath().split("/")[1];
		String websocketUrl = websocketScheme + "://" + uriInfo.getBaseUri().getHost() + ((uriInfo.getBaseUri().getPort() != -1 ) ? ":" + uriInfo.getBaseUri().getPort() + "/" : "/") + applicationName + "/websocket";

		WebpageRecordingPlugin app = getPluginApp();

		return app.startWebpageRecording(streamId, websocketUrl, request.getUrl());
	}

	/*
	 * Send command to a webpage with given id
	 *
	 * @PathParam id: webpage recording streamId
	 * @BodyParam event: event to be sent to the webpage
	 */
	@POST
	@Path("/send-command")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result webpageRecordingSendCommand(@RequestBody Endpoint request, @QueryParam("streamId") String streamId) {

		WebpageRecordingPlugin app = getPluginApp();

		return app.sendCommand(streamId, request.getEvent());
	}

	/*
	 * Stop recording of a webpage with given id
	 *
	 * @PathParam id: webpage recording streamId
	 */
	@POST
	@Path("/stop/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result stopWebpageRecording(@PathParam("id") String id) {
		WebpageRecordingPlugin app = getPluginApp();
		return app.stopWebpageRecording(id);
	}
	
	private WebpageRecordingPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (WebpageRecordingPlugin) appCtx.getBean("plugin.webpageRecordingPlugin");
	}
}
