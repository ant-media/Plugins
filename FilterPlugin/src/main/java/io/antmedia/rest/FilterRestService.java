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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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


	@Operation(summary = "Creates or update the filter",
	           responses = {@ApiResponse(responseCode = "200", description = "Filter created or updated")})
	@POST
	@Path("/create")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result create(@Parameter(description="Filter object with the updates") FilterConfiguration filterConfiguration) {
	    return getFiltersManager().createFilter(filterConfiguration, getAppAdaptor());
	}

	@Operation(summary = "Returns the list of filters effective in the application",
	           responses = {@ApiResponse(responseCode = "200", description = "List of filters returned")})
	@GET
	@Path("/list/{offset}/{size}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public List<FilterConfiguration> getFilters(
	        @Parameter(description = "This is the offset of the list, it is useful for pagination. If you want to use sort mechanism, we recommend using Mongo DB.", required = true) @PathParam("offset") int offset,
	        @Parameter(description = "Number of items that will be fetched. If there is not enough item in the datastore, returned list size may less then this value", required = true) @PathParam("size") int size) {
	    return getFiltersManager().getfilters();
	}

	@Operation(summary = "Delete the filter according to the filterId",
	           responses = {@ApiResponse(responseCode = "200", description = "Filter deleted")})
	@DELETE
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result delete(@Parameter(description="Filter id for deleting filter") @PathParam("id") String id) {
	    return new Result(getFiltersManager().delete(id, getAppAdaptor()));
	}

	@Operation(summary = "Creates MCU filter for non MCU room",
	           responses = {@ApiResponse(responseCode = "200", description = "MCU filter created for room")})
	@POST
	@Path("/room-mcu-filter/{roomId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result createMCU(@Parameter(description="Room Id") @PathParam("roomId") String roomId) {
	    ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
	    MCUManager mcuManager = (MCUManager) appCtx.getBean(MCUManager.BEAN_NAME);
	    mcuManager.addCustomRoom(roomId);
	    return new Result(true);
	}

	@Operation(summary = "Deletes MCU filter for non MCU room",
	           responses = {@ApiResponse(responseCode = "200", description = "MCU filter deleted for room")})
	@DELETE
	@Path("/room-mcu-filter/{roomId}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result deleteMCU(@Parameter(description="Room Id") @PathParam("roomId") String roomId) {
	    ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
	    MCUManager mcuManager = (MCUManager) appCtx.getBean(MCUManager.BEAN_NAME);
	    mcuManager.removeCustomRoom(roomId);
	    return new Result(true);
	}

	@Operation(summary = "Set the plugin type of the MCU function",
	           responses = {@ApiResponse(responseCode = "200", description = "MCU plugin type set")})
	@PUT
	@Path("/mcu-plugin-type")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result setPluginType(@Parameter(description="Change the plugin type for a filter: synchronous | asynchronous (default) | lastpoint") @QueryParam("type") String type) {
	    ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
	    MCUManager mcuManager = (MCUManager) appCtx.getBean(MCUManager.BEAN_NAME);
	    mcuManager.setPluginType(type);
	    return new Result(true);
	}

	@Operation(summary = "Set a filter specific to the MCU room",
	           responses = {@ApiResponse(responseCode = "200", description = "Custom MCU filter set")})
	@PUT
	@Path("/mcu-filter")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result setCustomMCUFilter(@Parameter(description="Filter object with the updates") FilterConfiguration filterConfiguration) {
	    Result result = create(filterConfiguration);
	    if (result.isSuccess()) {
	        ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
	        MCUManager mcuManager = (MCUManager) appCtx.getBean(MCUManager.BEAN_NAME);
	        mcuManager.customFilterAdded(filterConfiguration.getFilterId());
	    }
	    return result;
	}
	
	
	@Operation(summary = "Reset the default MCU filter",
	           responses = {@ApiResponse(responseCode = "200", description = "Default MCU filter reset")})
	@DELETE
	@Path("/mcu-filter/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result resetMCUFilter(@Parameter(description="Filter object with the updates") @PathParam("id") String filterId) {
		
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
