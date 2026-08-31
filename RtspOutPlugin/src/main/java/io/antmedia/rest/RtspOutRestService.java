package io.antmedia.rest;

import java.util.Map;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import io.antmedia.plugin.RtspOutPlugin;
import io.antmedia.rest.model.Result;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Component
@Path("/rtsp-out")
public class RtspOutRestService {

	@Context
	protected ServletContext servletContext;

	@GET
	@Path("/status")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> getStatus() {
		return getPlugin().getStatus();
	}

	@POST
	@Path("/enable/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Result enable(@PathParam("streamId") String streamId) {
		RtspOutPlugin plugin = getPlugin();
		plugin.enable(streamId);
		return new Result(true, "RTSP output enabled for stream " + streamId + " at " + plugin.getRtspUrl(streamId));
	}

	@POST
	@Path("/disable/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Result disable(@PathParam("streamId") String streamId) {
		getPlugin().disable(streamId);
		return new Result(true, "RTSP output disabled for stream " + streamId);
	}

	private RtspOutPlugin getPlugin() {
		ApplicationContext appCtx = (ApplicationContext) servletContext
				.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (RtspOutPlugin) appCtx.getBean(RtspOutPlugin.BEAN_NAME);
	}
}
