package io.antmedia.rest;

import io.antmedia.plugin.ClipCreatorPlugin;
import io.antmedia.plugin.CreateMp4Response;
import io.antmedia.rest.model.Result;
import io.lindstrom.m3u8.model.MediaPlaylist;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.util.ArrayList;

@Component
@Path("/clip-creator")
public class RestService {

	@Context
	protected ServletContext servletContext;

	@POST
	@Path("/mp4/{streamId}")
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM})
	public Response createMp4(
			@PathParam("streamId") String streamId,
			@QueryParam("returnFile") @DefaultValue("false") boolean returnFile) {

		ClipCreatorPlugin app = getPluginApp();
		File m3u8File = app.getM3u8File(streamId);

		if (m3u8File == null) {
			return Response.status(Status.EXPECTATION_FAILED)
					.entity(new Result(false, "No m3u8 HLS playlist exists for stream " + streamId))
					.build();
		}

		Long lastMp4CreateTime = app.getLastMp4CreateTimeForStream().get(streamId);
		long now = System.currentTimeMillis();
		MediaPlaylist playList = app.readPlaylist(m3u8File);
		ArrayList<File> tsFilesToMerge = new ArrayList<>();

		if (lastMp4CreateTime == null) {
			tsFilesToMerge.addAll(app.getSegmentFilesWithinTimeRange(
					playList,
					app.getClipCreatorSettings().getMp4CreationIntervalSeconds(),
					m3u8File));
		} else {
			tsFilesToMerge.addAll(app.getSegmentFilesWithinTimeRange(
					playList,
					lastMp4CreateTime,
					now,
					m3u8File));
		}

		if (tsFilesToMerge.isEmpty()) {
			return Response.status(Status.EXPECTATION_FAILED)
					.entity(new Result(false, "No HLS playlist segment exists for stream " + streamId + " in this interval."))
					.build();
		}

		CreateMp4Response createMp4Response = app.convertHlsToMp4(m3u8File, tsFilesToMerge, streamId);
		if (createMp4Response == null) {
			return Response.status(Status.EXPECTATION_FAILED)
					.entity(new Result(false, "Could not create MP4 for " + streamId))
					.build();
		}

		if (returnFile) {
			return Response.ok(createMp4Response.getFile())
					.header("Content-Disposition", "attachment; filename=\"" + createMp4Response.getFile().getName() + "\"")
					.header("X-vodId", createMp4Response.getVodId())
					.build();
		} else {
			Result result = new Result(true,  "MP4 created successfully for stream " + streamId);
			result.setDataId(createMp4Response.getVodId());
			return Response.ok(result)
					.build();
		}

	}

	@POST
	@Path("/start/{mp4CreationIntervalSeconds}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response startPeriodicCreation(@PathParam("mp4CreationIntervalSeconds") int mp4CreationIntervalSeconds) {
		ClipCreatorPlugin app = getPluginApp();
		app.startPeriodicCreationTimer(mp4CreationIntervalSeconds);
		return Response.ok(new Result(true)).build();
	}

	@POST
	@Path("/stop")
	@Produces(MediaType.APPLICATION_JSON)
	public Response stopPeriodicCreation() {
		ClipCreatorPlugin app = getPluginApp();
		app.stopPeriodicCreationTimer();
		return Response.ok(new Result(true)).build();
	}

	@POST
	@Path("/reload")
	@Produces(MediaType.APPLICATION_JSON)
	public Response reloadSettings() {
		ClipCreatorPlugin app = getPluginApp();
		app.reloadSettings();
		return Response.ok(new Result(true)).build();
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
