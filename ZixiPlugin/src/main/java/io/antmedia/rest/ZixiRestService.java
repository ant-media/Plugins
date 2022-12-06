package io.antmedia.rest;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.plugin.ZixiPlugin;
import io.antmedia.rest.model.Result;

@Component
@Path("/zixi")
public class ZixiRestService {

	@Context
	protected ServletContext servletContext;

	/**
	 * 
	 */
	@POST
	@Path("/client")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result createZixiClient(Broadcast broadcast, @PathParam("start") boolean start) {
		//broadcast object should have streamURL
		return  getZixiPlugin().startClient(broadcast, start);
	}


	@POST
	@Path("/client/start/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result startZixiClient(@PathParam("streamId") String streamId) {
		//broadcast object should have streamURL
		return  getZixiPlugin().startClient(streamId);
	}


	@POST
	@Path("/client/stop/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result stopZixiClient(@PathParam("streamId") String streamId) {
		//broadcast object should have streamURL
		return  getZixiPlugin().stopClient(streamId);
	}

	
	@DELETE
	@Path("/client/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result deleteZixiClient(@PathParam("streamId") String streamId) {
		return  getZixiPlugin().deleteClient(streamId);
	}
	
	@POST
	@Path("/feeder/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	//TODO: Send zixi url with JSON object not query param
	public Result createZixiFeeder(@PathParam("streamId") String streamId, @QueryParam("url") String zixiEndpointURL) 
	{
		return getZixiPlugin().startFeeder(streamId, zixiEndpointURL);
	}
	
	
	@DELETE
	@Path("/feeder/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	//TODO: Send zixi url with JSON object not query param
	public Result deleteZixiFeeder(@PathParam("streamId") String streamId,  @QueryParam("url") String zixiEndpointURL) 
	{
		return getZixiPlugin().stopFeeder(streamId, zixiEndpointURL);
	}
	
	private ZixiPlugin getZixiPlugin() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return appCtx.getBean(ZixiPlugin.class);
	}
}
