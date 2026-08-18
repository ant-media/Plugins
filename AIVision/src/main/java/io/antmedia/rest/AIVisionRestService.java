package io.antmedia.rest;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import io.antmedia.plugin.AIVisionAnalysisRequest;
import io.antmedia.plugin.AIVisionAnalysisResponse;
import io.antmedia.plugin.AIVisionImage;
import io.antmedia.plugin.AIVisionPlugin;
import io.antmedia.plugin.AIVisionSettings;
import io.antmedia.rest.model.Result;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Component
@Path("/v2/aivision")
public class AIVisionRestService {

	@Context
	protected ServletContext servletContext;

	@POST
	@Path("/register/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Result register(@PathParam("streamId") String streamId) {
		return getPluginApp().register(streamId);
	}

	@DELETE
	@Path("/register/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Result unregister(@PathParam("streamId") String streamId) {
		return getPluginApp().unregister(streamId);
	}

	@POST
	@Path("/analyze")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response analyze(AIVisionAnalysisRequest request) {
		AIVisionAnalysisResponse response = getPluginApp().analyze(request.getStreamId(), request.getPrompt(), request.getLastFrameTimestamp());
		return Response.ok(response).build();
	}

	@POST
	@Path("/settings")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public AIVisionSettings updateSettings(AIVisionSettings settings) {
		return getPluginApp().updateSettings(settings);
	}

	@GET
	@Path("/settings")
	@Produces(MediaType.APPLICATION_JSON)
	public AIVisionSettings getSettings() {
		return getPluginApp().getSettings();
	}

	@GET
	@Path("/latest/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getLatestImage(@PathParam("streamId") String streamId) {
		AIVisionImage image = getPluginApp().getLatestImage(streamId);
		if (image == null) {
			return Response.status(Status.NOT_FOUND).entity(new Result(false, "No image is available for stream " + streamId)).build();
		}
		AIVisionAnalysisResponse response = new AIVisionAnalysisResponse(true, "Latest image is available");
		response.setStreamId(streamId);
		response.setImagePath(image.getFile().getAbsolutePath());
		response.setImageUrl(image.getUrl());
		response.setFrameTimestamp(image.getTimestamp());
		return Response.ok(response).build();
	}

	private AIVisionPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (AIVisionPlugin) appCtx.getBean("plugin.aivision");
	}
}
