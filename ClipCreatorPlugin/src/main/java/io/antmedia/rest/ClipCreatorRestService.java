package io.antmedia.rest;

import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.plugin.ClipCreatorPlugin;
import io.antmedia.plugin.Mp4CreationResponse;
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
public class ClipCreatorRestService {

	@Context
	protected ServletContext servletContext;

	@POST
	@Path("/mp4/{streamId}")
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM})
	public Response createMp4(
			@PathParam("streamId") String streamId,
			@QueryParam("returnFile") @DefaultValue("false") boolean returnFile) 
	{

		ClipCreatorPlugin app = getPluginApp();
		File m3u8File = app.getM3u8File(streamId);

		if (m3u8File == null) {
			return Response.status(Status.EXPECTATION_FAILED)
					.entity(new Result(false, "No m3u8 HLS playlist exists for stream " + streamId))
					.build();
		}

		Broadcast broadcast = app.getDataStore().get(streamId);
		if (broadcast == null) {
			return Response.status(Status.EXPECTATION_FAILED)
					.entity(new Result(false, "No broadcast exists for stream " + streamId)).build();
		}

		Mp4CreationResponse createMp4Response = app.convertHlsToMp4(broadcast, false);
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
