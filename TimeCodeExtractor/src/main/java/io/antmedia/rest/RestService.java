package io.antmedia.rest;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import com.google.gson.Gson;

import io.antmedia.plugin.TimecodeExtractorPlugin;

@Component
@Path("/timecode-extractor")
public class RestService {

	@Context
	protected ServletContext servletContext;
	Gson gson = new Gson();

	
	@GET
	@Path("/timecodes/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response register(@PathParam("streamId") String streamId) {
		TimecodeExtractorPlugin plugin = getPluginComponent();
		
		return Response.status(Status.OK).entity(plugin.getTimeCodes(streamId)).build();
	}
	

	
	private TimecodeExtractorPlugin getPluginComponent() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (TimecodeExtractorPlugin) appCtx.getBean(TimecodeExtractorPlugin.COMPONENT_NAME);
	}
}
