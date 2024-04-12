package io.antmedia.rest;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.WebApplicationContext;

import io.antmedia.model.Endpoint;
import io.antmedia.plugin.MediaPushPlugin;
import io.antmedia.rest.model.Result;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

@Component
@Path("/v1/media-push")
public class RestService {

	static Logger log = LoggerFactory.getLogger(RestService.class);

	@Context
	protected ServletContext servletContext;

	/*
	 * Start recording of a webpage with given id
	 * If id is not provided, a random id will be generated
	 *
	 * @PathParam id: media push streamId
	 * @BodyParam url: url of the webpage to be recorded and it's mandatory
	 */
	@POST
	@Path("/start")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result startMediaPush(@RequestBody Endpoint request, @Context UriInfo uriInfo, @QueryParam("streamId") String streamId) {
		if (uriInfo == null) {
			return new Result(false, "Bad request");
		}

		String websocketScheme = ("https".equals(uriInfo.getBaseUri().getScheme())) ? "wss" : "ws";
		String applicationName = uriInfo.getBaseUri().getPath().split("/")[1];

		String websocketUrl = websocketScheme + "://" + uriInfo.getBaseUri().getHost() + ((uriInfo.getBaseUri().getPort() != -1 ) ? ":" + uriInfo.getBaseUri().getPort() + "/" : "/") + applicationName + "/websocket";

		MediaPushPlugin mediaPushPlugin = getPluginApp();

		return mediaPushPlugin.startMediaPush(streamId, websocketUrl, request.getWidth(), request.getHeight(), request.getUrl(),request.getToken(), request.getRecordType());
	}

	/*
	 * Stop recording of a webpage with given id
	 *
	 * @PathParam id: media push streamId
	 */
	@POST
	@Path("/stop/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result stopMediaPush(@PathParam("id") String id, @Context UriInfo uriInfo) {
		MediaPushPlugin app = getPluginApp();

		String applicationName = uriInfo.getBaseUri().getPath().split("/")[1];
		String httpURL = uriInfo.getBaseUri().getScheme() +"://" + uriInfo.getBaseUri().getHost();
		if(uriInfo.getBaseUri().getPort() != -1) {
			httpURL += ":" + uriInfo.getBaseUri().getPort();
		}
		httpURL += "/" + applicationName + "/";

		return app.stopMediaPush(id, httpURL);
	}

	/*
	 * Send command to a webpage with given stream id
	 *
	 * @PathParam streamId: media push stream id
	 * @BodyParam jsCommand: jsCommand to be sent to the webpage
	 */
	@POST
	@Path("/send-command")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result mediaPushSendCommand(@RequestBody Endpoint request, @QueryParam("streamId") String streamId) {

		MediaPushPlugin app = getPluginApp();

		if (streamId == null || streamId.isEmpty()) {
			return new Result(false, "streamId query parameter is mandatory.");
		}

		if (request.getJsCommand() == null || request.getJsCommand().isEmpty()) {
			return new Result(false, "jsCommand is mandatory.");
		}

		return app.sendCommand(streamId, request.getJsCommand());
	}

	private MediaPushPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (MediaPushPlugin) appCtx.getBean("plugin.mediaPushPlugin");
	}
}
