package io.antmedia.rest;

import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.plugin.ClipCreatorPlugin;
import io.antmedia.plugin.Mp4CreationResponse;
import io.antmedia.rest.model.Result;
import io.lindstrom.m3u8.model.MediaPlaylist;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.util.List;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

@Component
@Path("/clip-creator")
public class ClipCreatorRestService {

	@Context
	protected ServletContext servletContext;
	
	@Operation(description = "Starts periodic recording with the given period in seconds. If there is an active periodic recording, it stops that and starts a new one.")
	@POST
	@Path("/periodic-recording/{periodSeconds}")
	@Produces({MediaType.APPLICATION_JSON})
	public Result startPeriodicRecording(@Parameter(description = "Recording interval in seconds", required = true) @PathParam("periodSeconds") int periodSeconds) {
		ClipCreatorPlugin clipCreator = getPluginApp();
		return clipCreator.startPeriodicRecording(periodSeconds);
	}
	
	
	@Operation(description = "Stops the periodic recording and stops the recording of all active streams")
	@DELETE
	@Path("/periodic-recording")
	@Produces({MediaType.APPLICATION_JSON})
	public Result stopPeriodicRecording() {
		ClipCreatorPlugin clipCreator = getPluginApp();
		return clipCreator.stopPeriodicRecording();
	}
	

	@Operation(description = "Get the mp4 recording for the moment it's called since the last recording moment")
	@POST
	@Path("/mp4/{streamId}")
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM})
	public Response createMp4(
			@Parameter(description = "streamId of the broadcast that recording will be created", required = true) @PathParam("streamId") String streamId,
			@Parameter(description = "The download option. If it's true, server returns the created mp4 file", required = true) @QueryParam("returnFile") @DefaultValue("false") boolean returnFile) 
	{

		
		ClipCreatorPlugin clipCreator = getPluginApp();

		Broadcast broadcast = clipCreator.getDataStore().get(streamId);
		if (broadcast == null) {
			return Response.status(Status.EXPECTATION_FAILED)
					.entity(new Result(false, "No broadcast exists for stream " + streamId)).build();
		}

		Mp4CreationResponse response = clipCreator.convertHlsToMp4(broadcast, false);
		if (response == null || !response.isSuccess()) {
			return Response.status(Status.EXPECTATION_FAILED)
					.entity(new Result(false, response != null ? response.getMessage() : "Mp4 creation failed"))
					.build();
		}

		if (returnFile) {
			return Response.ok(response.getFile())
					.header("Content-Disposition", "attachment; filename=\"" + response.getFile().getName() + "\"")
					.header("X-vodId", response.getVodId())
					.build();
		} else {
			Result result = new Result(true,  "MP4 created successfully for stream " + streamId);
			result.setDataId(response.getVodId());
			return Response.ok(result)
					.build();
		}

	}
	
	@Operation(description = "Delete the mp4 files in the disk that are not recorded in the database. If there are unmatched files, it may delete them according to the parameter ")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "If there are unmatched files or not deleted them Result#success is false. If operations are successfull, its value is true. It gives extra information in the message field",
					content = @Content(
							mediaType = "application/json",
							schema = @Schema(implementation = Result.class)
							))
	})
	@DELETE
	@Path("/mp4-not-in-db")
	@Produces({MediaType.APPLICATION_JSON})
	public Result deleteMp4sNotInDB() 
	{
		ClipCreatorPlugin clipCreator = getPluginApp();
		return clipCreator.deleteMp4sNotInDB();
		
	}
	
	

	private ClipCreatorPlugin getPluginApp() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (ClipCreatorPlugin) appCtx.getBean("plugin.clip-creator");
	}

	public ServletContext getServletContext() {
		return servletContext;
	}

	public void setServletContext(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

}
