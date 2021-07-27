package io.antmedia.rest;

import java.util.List;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import com.google.gson.Gson;

import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.IApplicationAdaptorFactory;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.DataStoreFactory;
import io.antmedia.filter.FilterConfiguration;
import io.antmedia.filter.MCUFilterConfiguration;
import io.antmedia.plugin.FiltersManager;
import io.antmedia.rest.model.Result;
import io.swagger.annotations.ApiParam;


@Component
@Path("/v2/filters")
public class FilterRestService {

	@Context
	protected ServletContext servletContext;
	Gson gson = new Gson();
	private DataStoreFactory dataStoreFactory;
	private DataStore dbStore;

	@POST
	@Path("/create")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result create(@ApiParam(value="Filter object with the updates") FilterConfiguration filterConfiguration) {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		AntMediaApplicationAdapter adaptor = ((IApplicationAdaptorFactory) appCtx.getBean(AntMediaApplicationAdapter.BEAN_NAME)).getAppAdaptor();
		FiltersManager filtersManager = (FiltersManager) appCtx.getBean(FiltersManager.BEAN_NAME);
		filtersManager.createFilter(filterConfiguration, adaptor);
		
		return new Result(true);
	}
	
	@POST
	@Path("/create-mcu")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result createMCU(@ApiParam(value="MCU Filter object with the updates") MCUFilterConfiguration filterConfiguration) {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		AntMediaApplicationAdapter adaptor = ((IApplicationAdaptorFactory) appCtx.getBean(AntMediaApplicationAdapter.BEAN_NAME)).getAppAdaptor();
		FiltersManager filtersManager = (FiltersManager) appCtx.getBean(FiltersManager.BEAN_NAME);
		filtersManager.createFilter(filterConfiguration, adaptor);
		
		return new Result(true);
	}
	
	@GET
	@Path("/list/{offset}/{size}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public List<FilterConfiguration> getFilters(
			@ApiParam(value = "This is the offset of the list, it is useful for pagination. If you want to use sort mechanism, we recommend using Mongo DB.", required = true) @PathParam("offset") int offset,
			@ApiParam(value = "Number of items that will be fetched. If there is not enough item in the datastore, returned list size may less then this value", required = true) @PathParam("size") int size) {

		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		FiltersManager filtersManager = (FiltersManager) appCtx.getBean(FiltersManager.BEAN_NAME);
		
		
		/* FIXME:
		 * implement size, sort if necessary
		 */
		return filtersManager.getfilters();
	}

	@DELETE
	@Path("/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Result delete(@ApiParam(value="Filter id for deleting filter") @PathParam("id") String id) {
		ApplicationContext appCtx = (ApplicationContext) servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		AntMediaApplicationAdapter adaptor = ((IApplicationAdaptorFactory) appCtx.getBean(AntMediaApplicationAdapter.BEAN_NAME)).getAppAdaptor();
		FiltersManager filtersManager = (FiltersManager) appCtx.getBean(FiltersManager.BEAN_NAME);
		filtersManager.delete(id, adaptor);
		
		return new Result(true);
	}

}
