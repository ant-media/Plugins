package io.antmedia.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.filter.utils.FilterConfiguration;
import io.antmedia.plugin.FiltersManager;
import io.antmedia.plugin.MCUManager;
import io.antmedia.rest.model.Result;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Component
@Path("/v2/filters")
public class FilterRestService {

	@Context
	protected ServletContext servletContext;
	
	private static final Logger logger = LoggerFactory.getLogger(FilterRestService.class);


	@ApiOperation(value = "Creates or update the filter. If the filterId of the FilterConfiguration is already available, it just updates the configuration. Otherwise it creates the filter", notes = "", response = Result.class)
	@POST
	@Path("/create")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result create(@ApiParam(value="Filter object with the updates") FilterConfiguration filterConfiguration) 
	{
		return getFiltersManager().createFilter(filterConfiguration, getAppAdaptor());
	}
	
	@ApiOperation(value = "Returns the list of filters effective in the application", notes = "", response = Result.class)
	@GET
	@Path("/list/{offset}/{size}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public List<FilterConfiguration> getFilters(
			@ApiParam(value = "This is the offset of the list, it is useful for pagination. If you want to use sort mechanism, we recommend using Mongo DB.", required = true) @PathParam("offset") int offset,
			@ApiParam(value = "Number of items that will be fetched. If there is not enough item in the datastore, returned list size may less then this value", required = true) @PathParam("size") int size) {

		
		/* FIXME:
		 * implement size, sort if necessary
		 */
		return getFiltersManager().getfilters();
	}

	@ApiOperation(value = "Delete the filter according to the filterId", notes = "", response = Result.class)
	@DELETE
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result delete(@ApiParam(value="Filter id for deleting filter") @PathParam("id") String id) {
		return new Result(getFiltersManager().delete(id, getAppAdaptor()));
	}
	
	@ApiOperation(value = "Creates MCU filter for non MCU room")
	@POST
	@Path("/room-mcu-filter/{roomId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result createMCU(@ApiParam(value="Room Id") @PathParam("roomId") String roomId) 
	{
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		MCUManager mcuManager = (MCUManager) appCtx.getBean(MCUManager.BEAN_NAME);
		mcuManager.addCustomRoom(roomId);
		
		return new Result(true);
	}
	
	@ApiOperation(value = "Deletes MCU filter for non MCU room")
	@DELETE
	@Path("/room-mcu-filter/{roomId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result deleteMCU(@ApiParam(value="Room Id") @PathParam("roomId") String roomId) 
	{
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		MCUManager mcuManager = (MCUManager) appCtx.getBean(MCUManager.BEAN_NAME);
		mcuManager.removeCustomRoom(roomId);
		
		return new Result(true);
	}
	
	@ApiOperation(value = "Set the plugin type of the MCU function. This type is in application specific not room specific", notes = "", response = Result.class)
	@PUT
	@Path("/{id}/mcu-plugin-type")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result setPluginType(@ApiParam(value="Change the plugin type for a flter: synchronous | asynchronous (default) | lastpoint") @QueryParam("type") String type) {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		MCUManager mcuManager = (MCUManager) appCtx.getBean(MCUManager.BEAN_NAME);
		mcuManager.setPluginType(type);
		return new Result(true);
	}
	
	@ApiOperation(value = "Set a filter specific to the MCU room. When this method is used, it always uses this filter. If you want to change to the default behaviour, you need to reset the MCU filter", notes = "", response = Result.class)
	@PUT
	@Path("/mcu-filter")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result setCustomMCUFilter(@ApiParam(value="Filter object with the updates") FilterConfiguration filterConfiguration) {
		Result result = create(filterConfiguration);
		
		if (result.isSuccess()) {
			ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
			MCUManager mcuManager = (MCUManager) appCtx.getBean(MCUManager.BEAN_NAME);
			mcuManager.customFilterAdded(filterConfiguration.getFilterId());
		}
		
		return result;
	}
	
	@ApiOperation(value = "Reset the default MCU filter", notes = "", response = Result.class)
	@DELETE
	@Path("/mcu-filter/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result resetMCUFilter(@ApiParam(value="Filter object with the updates") @PathParam("id") String filterId) {
		
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		MCUManager mcuManager = (MCUManager) appCtx.getBean(MCUManager.BEAN_NAME);
		
		return new Result(mcuManager.customFilterRemoved(filterId));
	}
	
	
	private FiltersManager getFiltersManager() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (FiltersManager) appCtx.getBean(FiltersManager.BEAN_NAME);
	}
	
	private AntMediaApplicationAdapter getAppAdaptor() {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		return (AntMediaApplicationAdapter) appCtx.getBean(AntMediaApplicationAdapter.BEAN_NAME);
	}
	
	public void setServletContext(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

}
