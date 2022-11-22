package io.antmedia.rest;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import com.google.gson.Gson;

import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.plugin.ZixiPlugin;
import io.antmedia.rest.model.Result;

@Component
@Path("/zixi")
public class ZixiRestService {

	@Context
	protected ServletContext servletContext;
	Gson gson = new Gson();

	
	@POST
	@Path("/client")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result createZixiClient(Broadcast broadcast) {
		//broadcast object should have streamURL
		return  getZixiPlugin().startClient(broadcast);
	}
	
	@DELETE
	@Path("/client/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result deleteZixiClient(@PathParam("streamId") String streamId) {
		return  getZixiPlugin().stopClient(streamId);
	}
	
	@POST
	@Path("/feeder")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result createZixiFeeder(String streamId) //, String zixiEndpointURL) 
	{
		return null;// getZixiPlugin().startFeeder(streamId, zixiEndpointURL);
	}
	
	
	@DELETE
	@Path("/feeder")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result deleteZixiFeeder(String streamId) //, String zixiEndpointURL) 
	{
		return null;// getZixiPlugin().stopFeeder(streamId, zixiEndpointURL);
	}
	
	private ZixiPlugin getZixiPlugin() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return appCtx.getBean(ZixiPlugin.class);
	}
}
