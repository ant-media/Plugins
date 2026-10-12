package io.antmedia.rest;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import io.antmedia.plugin.AIVisionAnalysisRequest;
import io.antmedia.plugin.AIVisionAnalysisResponse;
import io.antmedia.plugin.AIVisionPlugin;
import io.antmedia.plugin.AIVisionPeriodicAnalysisRequest;
import io.antmedia.plugin.AIVisionPeriodicAnalysisStatus;
import io.antmedia.plugin.AIVisionSettings;
import io.antmedia.rest.model.Result;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Component
@Path("/v2/aivision")
public class AIVisionRestService {

	@Context
	protected ServletContext servletContext;

	@POST
	@Path("/analyze")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response analyze(AIVisionAnalysisRequest request) {
		AIVisionAnalysisResponse response = getPluginApp().analyze(request.getStreamId(), request.getPrompt(), request.getLastFrameTimestamp());
		return Response.ok(response).build();
	}

	@POST
	@Path("/periodic")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Result startPeriodicAnalysis(AIVisionPeriodicAnalysisRequest request) {
		if (request == null) {
			return new Result(false, "request body is required");
		}
		return getPluginApp().startPeriodicAnalysis(request.getPrompt(), request.getPeriod(), request.getStreamIds(), request.isApplyToAll());
	}

	@DELETE
	@Path("/periodic/{requestId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Result stopPeriodicAnalysis(@PathParam("requestId") String requestId) {
		return getPluginApp().stopPeriodicAnalysis(requestId);
	}

	@GET
	@Path("/periodic")
	@Produces(MediaType.APPLICATION_JSON)
	public java.util.List<AIVisionPeriodicAnalysisStatus> getPeriodicAnalyses() {
		return getPluginApp().getPeriodicAnalyses();
	}

	@GET
	@Path("/periodic/{requestId}/results")
	@Produces(MediaType.APPLICATION_JSON)
	public java.util.List<AIVisionAnalysisResponse> getPeriodicResults(@PathParam("requestId") String requestId,
			@QueryParam("after") long afterResultId) {
		return getPluginApp().getPeriodicResults(requestId, afterResultId);
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

	private AIVisionPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (AIVisionPlugin) appCtx.getBean("plugin.aivision");
	}
}
