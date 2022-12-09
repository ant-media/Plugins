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
	 * Creates a ZixiClient to pull the stream from ZixiBroadcaster to Ant Media Server.
	 * 
	 * 
	 * @param broadcast: The broadcast object that  have streamURL defined as zixi url such as zixi://127.0.0.1:2077/stream1 
	 * @param start: start is the query parameter and If start is true, it automatically starts the ZixiClient. By default it's false
	 * 
	 * @return
	 * The status of the operation. If start is false, it returns the stream id in the dataId field
	 * The dataId field can be use start/stop the Zixi Client.
	 */
	@POST
	@Path("/client")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result createZixiClient(Broadcast broadcast, @QueryParam("start") boolean start) {
		//broadcast object should have streamURL
		return  getZixiPlugin().startClient(broadcast, start);
	}


	/**
	 * Starts a ZixiClient that is created but not actively streaming 
	 * 
	 * @param streamId: The stream id to be started, it's the value returned by {@link #createZixiClient(Broadcast, boolean)}
	 * 
	 * @return the status of the operation
	 */
	@POST
	@Path("/client/start/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result startZixiClient(@PathParam("streamId") String streamId) {
		return  getZixiPlugin().startClient(streamId);
	}


	/**
	 * Stops the ZixiClient that is actively streaming
	 * 
	 * @param streamId: The stream id of the ZixiClient that is actively streaming
	 * 
	 * @return the status of the operation
	 */
	@POST
	@Path("/client/stop/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result stopZixiClient(@PathParam("streamId") String streamId) {
		//broadcast object should have streamURL
		return  getZixiPlugin().stopClient(streamId);
	}

	
	/**
	 * Deletes the ZixiClient that was created on the server side. If it's streaming, it also stops the streaming
	 * 
	 * @param streamId: The stream id of the ZixiClient to be deleted
	 * 
	 * @return the status of the operation
	 */
	@DELETE
	@Path("/client/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result deleteZixiClient(@PathParam("streamId") String streamId) {
		return  getZixiPlugin().deleteClient(streamId);
	}
	
	/**
	 * Pushes the stream with having `streamId` on Ant Media Server to the ZixiBroadcaster through zixiEndpointURL such as zixi://127.0.0.1:2088/stream2
	 *  
	 * @param streamId: The id of the stream that is active on the Ant Media Server
	 * @param zixiEndpointURL: The Zixi URL that Ant Media Server will push the stream to
	 * 
	 * @return the status of the operation
	 */
	@POST
	@Path("/feeder/{streamId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	//TODO: Send zixi url with JSON object not query param
	public Result createZixiFeeder(@PathParam("streamId") String streamId, @QueryParam("url") String zixiEndpointURL) 
	{
		return getZixiPlugin().startFeeder(streamId, zixiEndpointURL);
	}
	
	/**
	 * Stops pushing the stream to ZixiBroadcaster
	 * 
	 * @param streamId: The id of the stream that is being pushed to the ZixiBroadcaster through zixi url
	 * 
	 * @param zixiEndpointURL: The endpoint url that will be stopped pushing. It's not a mandatory field. 
	 *  If it's not specified, it stops all Zixi pushes that is belonged to stream Id
	 * 
	 * @return the status of the operation
	 */
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
