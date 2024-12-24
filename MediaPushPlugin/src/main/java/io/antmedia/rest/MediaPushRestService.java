package io.antmedia.rest;

import io.antmedia.AntMediaApplicationAdapter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.HttpRequest;

import io.antmedia.model.Endpoint;
import io.antmedia.plugin.MediaPushPlugin;
import io.antmedia.rest.model.Result;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

@Component
@Path("/v1/media-push")
public class MediaPushRestService {

	static Logger log = LoggerFactory.getLogger(MediaPushRestService.class);

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
	public Result startMediaPush(@RequestBody Endpoint request, @Context HttpServletRequest httpRequest, @Context UriInfo uriInfo, @QueryParam("streamId") String streamId) {
		if (uriInfo == null) {
			return new Result(false, "Bad request");
		}

		String websocketUrl = getWebSocketURL(httpRequest, uriInfo);
		
		MediaPushPlugin mediaPushPlugin = getPluginApp();

		return mediaPushPlugin.startMediaPush(streamId, websocketUrl, request);
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
	public Result stopMediaPush(@PathParam("id") String id) {
		MediaPushPlugin app = getPluginApp();
		return app.stopMediaPush(id);
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

	public String getWebSocketURL(HttpServletRequest httpRequest, UriInfo uriInfo) {
		String scheme = httpRequest.getHeader("X-Forwarded-Proto");
		if (scheme == null) {
			scheme = uriInfo.getBaseUri().getScheme();
		}

		String host = httpRequest.getHeader("X-Forwarded-Host");
		if (host == null) {
			host = uriInfo.getBaseUri().getHost();
		}

		String portHeader = httpRequest.getHeader("X-Forwarded-Port");
		int port = (portHeader != null) ? Integer.parseInt(portHeader) : (uriInfo.getBaseUri() != null) ? uriInfo.getBaseUri().getPort() : -1;

		String websocketScheme = "https".equals(scheme) ? "wss" : "ws";
		String applicationName = (uriInfo.getBaseUri() != null) ? uriInfo.getBaseUri().getPath().split("/")[1] : getApplicationName();

        return websocketScheme + "://" + host + ((port != -1 ) ? ":" + port + "/" : "/") + applicationName + "/websocket";
	}

	public MediaPushPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (MediaPushPlugin) appCtx.getBean("plugin.mediaPushPlugin");
	}

	String getApplicationName() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		AntMediaApplicationAdapter app = (AntMediaApplicationAdapter) appCtx.getBean(AntMediaApplicationAdapter.BEAN_NAME);
		return app.getName();
	}
}
