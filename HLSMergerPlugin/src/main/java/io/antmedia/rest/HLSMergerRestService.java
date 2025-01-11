package io.antmedia.rest;

import com.google.gson.Gson;

import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.plugin.HLSMergerPlugin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

	
	@Operation(description = "Creates a master file with the fileName that includes the M3U8 endpoints "
			+ "of the streams that are specified in the streamIds. ")
	@ApiResponse(responseCode = "200", description = "If the operation is successful")
	@ApiResponse(responseCode = "400", description = "If the operation has failed. Check the streamId "
			+ "you have provided are correct and the streams are running.")
	@POST
	@Path("/multi-resolution-stream/{file-name}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response start(@Parameter(description = "master m3u8 file name that will contains the m3u8 endpoints of the streamIds", required = true) 
							@PathParam("file-name") String fileName, 
						@Parameter(description = "Stream Ids that will be listed in master file", required = true) String[] streamIds) {
		HLSMergerPlugin app = getPluginApp();
		boolean result = app.mergeStreams(fileName, streamIds);

		if (result) {
			return Response.status(Status.OK).entity("").build();
		}
		return Response.status(Status.BAD_REQUEST).entity("").build();
	}
	
	@Operation(description = "Deletes and clears the resources of the master file that is created with POST method")
	@ApiResponse(responseCode = "200", description = "If the operation is successful")
	@ApiResponse(responseCode = "400", description = "If the operation has failed. Make sure that your parameter is correct and it's active")
	@DELETE
	@Path("/multi-resolution-stream/{file-name}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response stopMultiResolutionStream(@Parameter(description = "master m3u8 file name that will "
															+ "be deleted and resources will be released for it", required = true)  
											 @PathParam("file-name") String fileName) {
		HLSMergerPlugin app = getPluginApp();
		boolean result = app.stopMerge(fileName);

		if (result) {
			return Response.status(Status.OK).entity("").build();
		}
		return Response.status(Status.BAD_REQUEST).entity("").build();
	}
	
	@Operation(description = "Adds the audio stream to the video stream that is specified with videoStreamId")
	@ApiResponse(responseCode = "200", description = "If the operation is successful")
	@ApiResponse(responseCode = "400", description = "If the operation has failed. Check the videoStreamId and audioStreamId")
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
	    return Response.status(Status.BAD_REQUEST).entity("").build();
	}
	
	@Operation(description = "Deletes the audio stream from the video stream that is specified with videoStreamId")
	@ApiResponse(responseCode = "200", description = "If the operation is successful")
	@ApiResponse(responseCode = "400", description = "If the operation has failed. Check the videoStreamId and audioStreamId")
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
		return Response.status(Status.BAD_REQUEST).entity("").build();
	}
	
	private HLSMergerPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (HLSMergerPlugin) appCtx.getBean("plugin.hls-merger");
	}
}
