package io.antmedia.rest;

import com.google.gson.Gson;
import io.antmedia.plugin.HLSMergerPlugin;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.io.FileNotFoundException;

@Component
@Path("/v1/hls-merger")
public class HLSMergerRestService {

	@Context
	protected ServletContext servletContext;
	Gson gson = new Gson();

	
	@POST
	@Path("/multi-resolution-stream/{file-name}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response start(@PathParam("file-name") String fileName, @Parameter(description = "Stream Id", required = true) String[] streamIds) {
		HLSMergerPlugin app = getPluginApp();
		boolean result = app.mergeStreams(fileName, streamIds);

		if (result) {
			return Response.status(Status.OK).entity("").build();
		}
		return Response.status(Status.INTERNAL_SERVER_ERROR).entity("").build();
	}
	
	@POST
	@Path("{video-stream-id}/multi-audio-stream/{file-name}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response addMultipleAudio(@PathParam("file-name") String fileName, 
									  @PathParam("video-stream-id") String videoStreamId, 
	                                  @Parameter(description = "Audio Stream Ids", required = true) String[] audioStreamIds) {
	    HLSMergerPlugin app = getPluginApp();
	    boolean result = app.addMultipleAudioStreams(fileName, videoStreamId, audioStreamIds);

	    if (result) {
	        return Response.status(Status.OK).entity("").build();
	    }
	    return Response.status(Status.INTERNAL_SERVER_ERROR).entity("").build();
	}


	@DELETE
	@Path("/multi-resolution-stream/{file-name}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response stopMultiResolutionStream(@PathParam("file-name") String fileName) {
		HLSMergerPlugin app = getPluginApp();
		boolean result = app.stopMerge(fileName);

		if (result) {
			return Response.status(Status.OK).entity("").build();
		}
		return Response.status(Status.INTERNAL_SERVER_ERROR).entity("").build();
	}
	
	@DELETE
	@Path("/multi-audio-stream/{file-name}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response stopMultiAudioStream(@PathParam("file-name") String fileName) {
		HLSMergerPlugin app = getPluginApp();
		boolean result = app.stopMerge(fileName);

		if (result) {
			return Response.status(Status.OK).entity("").build();
		}
		return Response.status(Status.INTERNAL_SERVER_ERROR).entity("").build();
	}
	
	private HLSMergerPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (HLSMergerPlugin) appCtx.getBean("plugin.hls-merger");
	}
}
