// Copyright 2016 Yahoo Inc.
// Licensed under the terms of the Apache license. Please see LICENSE.md file distributed with this work for terms.
package com.yahoo.bard.webservice.web.endpoints;

import static com.yahoo.bard.webservice.config.BardFeatureFlag.UPDATED_METADATA_COLLECTION_NAMES;
import static com.yahoo.bard.webservice.web.endpoints.views.DefaultMetadataViewFormatters.tableMetadataFormatter;
import static javax.ws.rs.core.Response.Status.OK;

import com.yahoo.bard.webservice.application.ObjectMappersSuite;
import com.yahoo.bard.webservice.data.config.ResourceDictionaries;
import com.yahoo.bard.webservice.data.time.GranularityParser;
import com.yahoo.bard.webservice.druid.model.builders.DruidFilterBuilder;
import com.yahoo.bard.webservice.exception.MetadataExceptionHandler;
import com.yahoo.bard.webservice.logging.RequestLog;
import com.yahoo.bard.webservice.logging.blocks.TableRequest;
import com.yahoo.bard.webservice.table.LogicalTable;
import com.yahoo.bard.webservice.table.LogicalTableDictionary;
import com.yahoo.bard.webservice.table.resolver.QueryPlanningConstraint;
import com.yahoo.bard.webservice.util.SimplifiedIntervalList;
import com.yahoo.bard.webservice.util.TableUtils;
import com.yahoo.bard.webservice.web.MetadataObject;
import com.yahoo.bard.webservice.web.RequestMapper;
import com.yahoo.bard.webservice.web.ResponseFormatResolver;
import com.yahoo.bard.webservice.web.apirequest.TablesApiRequest;
import com.yahoo.bard.webservice.web.apirequest.TablesApiRequestImpl;
import com.yahoo.bard.webservice.web.apirequest.generator.having.HavingGenerator;
import com.yahoo.bard.webservice.web.endpoints.views.DefaultMetadataViewFormatters;
import com.yahoo.bard.webservice.web.endpoints.views.TableFullViewFormatter;
import com.yahoo.bard.webservice.web.endpoints.views.TableMetadataFormatter;
import com.yahoo.bard.webservice.web.util.BardConfigResources;

import com.codahale.metrics.annotation.Timed;

import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/**
 * Resource code for table resource endpoints.
 */
@Path("/tables")
@Singleton
public class TablesServlet extends EndpointServlet implements BardConfigResources {

    private static final Logger LOG = LoggerFactory.getLogger(TablesServlet.class);

    private final ResourceDictionaries resourceDictionaries;
    private final RequestMapper requestMapper;
    private final GranularityParser granularityParser;
    private final ResponseFormatResolver formatResolver;
    private final MetadataExceptionHandler exceptionHandler;
    private final DateTimeFormatter dateTimeFormatter;

    /**
     * Constructor.
     *
     * @param resourceDictionaries  Dictionary holder
     * @param requestMapper  Mapper to change the API request if needed
     * @param objectMappers  JSON tools
     * @param granularityParser  Helper for parsing granularities
     * @param formatResolver  The formatResolver for determining correct response format
     * @param exceptionHandler  Injection point for handling response exceptions
     * @param dateTimeFormatter  date time formatter
     */
    @Inject
    public TablesServlet(
            ResourceDictionaries resourceDictionaries,
            @Named(TablesApiRequest.REQUEST_MAPPER_NAMESPACE) RequestMapper requestMapper,
            ObjectMappersSuite objectMappers,
            GranularityParser granularityParser,
            ResponseFormatResolver formatResolver,
            @Named(TablesApiRequest.EXCEPTION_HANDLER_NAMESPACE) MetadataExceptionHandler exceptionHandler,
            DateTimeFormatter dateTimeFormatter
    ) {
        super(objectMappers);
        this.resourceDictionaries = resourceDictionaries;
        this.requestMapper = requestMapper;
        this.granularityParser = granularityParser;
        this.formatResolver = formatResolver;
        this.exceptionHandler = exceptionHandler;
        this.dateTimeFormatter = dateTimeFormatter;
    }

    /**
     * Get the URL of the logical table.
     *
     * @param logicalTable  Logical table to get the URL of
     * @param uriInfo  URI Info for the request
     *
     * @return The absolute URL for the logical table
     */
    public static String getLogicalTableUrl(LogicalTable logicalTable, UriInfo uriInfo) {
        return uriInfo.getBaseUriBuilder()
                .path(TablesServlet.class)
                .path(TablesServlet.class, "getTableByGrain")
                .build(logicalTable.getName(), logicalTable.getGranularity().getName())
                .toASCIIString();
    }

    /**
     * Get all the logical tables as a summary list.
     *
     * @param perPage  number of values to return per page
     * @param page  the page to start from
     * @param format  The name of the output format type
     * @param downloadFilename If present, indicates the response should be downloaded by the client with the provided
     * filename. Otherwise indicates the response should be rendered in the browser.
     * @param uriInfo  UriInfo of the request
     * @param containerRequestContext  The context of data provided by the Jersey container for this request
     *
     * @return The list of logical tables
     * <p>
     * {@code
     * {
     *     "tables": <List of Table Summaries>
     * }
     * }
     * @see TableMetadataFormatter#formatTables(Collection, UriInfo)
     */
    @GET
    @Timed
    public Response getAllTables(
            @DefaultValue("") @NotNull @QueryParam("perPage") String perPage,
            @DefaultValue("") @NotNull @QueryParam("page") String page,
            @QueryParam("format") String format,
            @QueryParam("filename") String downloadFilename,
            @Context UriInfo uriInfo,
            @Context final ContainerRequestContext containerRequestContext
    ) {
        if (format != null && format.toLowerCase(Locale.ENGLISH).equals("fullview")) {
            return getTablesRollupView(perPage, page, uriInfo, containerRequestContext);
        } else {
            return getTable(null, perPage, page, format, downloadFilename, containerRequestContext);
        }
    }


    /**
     * Get all grain-specific logical tables for a logical table name as a summary list.
     *
     * @param tableName  Table to get all the grain-specific logical tables for
     * @param perPage  number of values to return per page
     * @param page  the page to start from
     * @param format  The name of the output format type
     * @param downloadFilename If present, indicates the response should be downloaded by the client with the provided
     * filename. Otherwise indicates the response should be rendered in the browser.
     * @param containerRequestContext  The context of data provided by the Jersey container for this request
     *
     * @return The list of grain-specific logical tables
     * <p>
     * {@code
     * {
     *     "tables": <List of Table Summaries>
     * }
     * }
     * @see TableMetadataFormatter#formatTables(Collection, UriInfo)
     */
    @GET
    @Timed
    @Path("/{tableName}")
    public Response getTable(
            @PathParam("tableName") String tableName,
            @DefaultValue("") @NotNull @QueryParam("perPage") String perPage,
            @DefaultValue("") @NotNull @QueryParam("page") String page,
            @QueryParam("format") String format,
            @QueryParam("filename") String downloadFilename,
            @Context final ContainerRequestContext containerRequestContext
    ) {
        TablesApiRequestImpl tablesApiRequestImpl = null;
        UriInfo uriInfo = containerRequestContext.getUriInfo();
        try {
            RequestLog.startTiming(this);
            RequestLog.record(new TableRequest(tableName != null ? tableName : "all", "all"));

            tablesApiRequestImpl = new TablesApiRequestImpl(
                    tableName,
                    null,
                    formatResolver.apply(format, containerRequestContext),
                    downloadFilename,
                    perPage,
                    page,
                    this
            );

            if (requestMapper != null) {
                tablesApiRequestImpl = (TablesApiRequestImpl) requestMapper.apply(
                        tablesApiRequestImpl,
                        containerRequestContext
                );
            }

            Response response = paginateAndFormatResponse(
                    tablesApiRequestImpl,
                    containerRequestContext,
                    tableMetadataFormatter.formatTables(tablesApiRequestImpl.getTables(), uriInfo),
                    UPDATED_METADATA_COLLECTION_NAMES.isOn() ? "tables" : "rows",
                    null
            );
            LOG.trace("Tables Endpoint Response: {}", response.getEntity());
            return response;
        } catch (Throwable t) {
            return exceptionHandler.handleThrowable(
                    t,
                    Optional.ofNullable(tablesApiRequestImpl),
                    containerRequestContext
            );
        } finally {
            RequestLog.stopTiming(this);
        }
    }

    /**
     * Get <b>unconstrained</b> logical table details for a grain-specific logical table.
     * <p>
     * for getting <b>constrained</b> logical table details
     *
     * @param tableName  The name of the logical table being queried
     * @param grain  Logical table grain (part of the logical table ID)
     * @param containerRequestContext  The context of data provided by the Jersey container for this request
     *
     * @return The grain-specific logical table
     * @see TableMetadataFormatter#formatTable(LogicalTable, UriInfo)
     *
     * TODO: Need to delegate to constrained endpoint
     */
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{tableName}/{granularity}")
    public Response getTableByGrain(
            @PathParam("tableName") String tableName,
            @PathParam("granularity") String grain,
            @Context final ContainerRequestContext containerRequestContext
    ) {
        TablesApiRequestImpl tablesApiRequestImpl = null;
        UriInfo uriInfo = containerRequestContext.getUriInfo();
        try {
            RequestLog.startTiming(this);
            RequestLog.record(new TableRequest(tableName, grain));

            tablesApiRequestImpl = new TablesApiRequestImpl(
                    tableName,
                    grain,
                    null,
                    "",
                    "",
                    this
            );

            if (requestMapper != null) {
                tablesApiRequestImpl = (TablesApiRequestImpl) requestMapper.apply(
                        tablesApiRequestImpl,
                        containerRequestContext
                );
            }

            MetadataObject result = getConstrainedLogicalTableDetailView(tablesApiRequestImpl, uriInfo);
            String output = objectMappers.getMapper().writeValueAsString(result);
            LOG.trace("Tables Endpoint Response: {}", output);
            return Response.status(Response.Status.OK).entity(output).build();
        } catch (Throwable t) {
            return exceptionHandler.handleThrowable(
                    t,
                    Optional.ofNullable(tablesApiRequestImpl),
                    containerRequestContext
            );
        } finally {
            RequestLog.stopTiming(this);
        }
    }

    /**
     * Get <b>constrained</b> logical table details for a grain-specific logical table.
     * <p>
     * An example query to this endpoint is
     * <pre>
     *     {@code
     *     /tables/myTable/week/dim1/dim2?metrics=myMetric&filters=dim3|id-in[foo,bar]
     *     }
     * </pre>
     * TODO: filter physical tables by interval and filter clause, i.e. "filters=dim3|id-in[foo,bar]".
     * This query has an optional list of path separated grouping dimensions, an optional list of metrics, and an
     * optional filter clause.
     * <p>
     * The query would result in a table response with the metrics, dimensions, and available intervals restricted down
     * to the set of items that are still "reachable" given the constraints in the query (dim1, dim2, dim3, and
     * myMetric, in this case). So, if the table normally indicates that dim7 is one of it's dimensions, but there isn't
     * a backing physical table for myTable that has dim1, dim2, dim3, and myMetric along with dim7, then dim7 would not
     * be in the dimension list returned in the response.
     *
     * @param tableName  Logical table name
     * @param granularity  Logical table grain (part of the logical table ID)
     * @param dimensions  Requested list of dimensions (e.g. dim1, dim2)
     * @param metrics  Requested list of metrics (e.g. myMetric)
     * @param intervals  Requested list of intervals. This is a required
     * @param filters  Requested list of filters (e.g. dim3|id-in[foo,bar])
     * @param downloadFilename  Output filename for response
     * @param containerRequestContext  The context of data provided by the Jersey container for this request
     *
     * @return The grain-specific logical table
     *
     * See {@link #getConstrainedLogicalTableDetailView(TablesApiRequestImpl, UriInfo)}
     */
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{tableName}/{granularity}/{dimensions:.*}")
    public Response getTableByGrainAndConstraint(
            @PathParam("tableName") String tableName,
            @PathParam("granularity") String granularity,
            @PathParam("dimensions") List<PathSegment> dimensions,
            @QueryParam("metrics") String metrics,
            @QueryParam("dateTime") String intervals,
            @QueryParam("filters") String filters,
            @QueryParam("filename") String downloadFilename,
            @Context final ContainerRequestContext containerRequestContext
    ) {
        TablesApiRequestImpl tablesApiRequest = null;
        try {
            RequestLog.startTiming(this);
            RequestLog.record(new TableRequest(tableName, granularity));

            tablesApiRequest = new TablesApiRequestImpl(
                    tableName,
                    granularity,
                    null,
                    downloadFilename,
                    "",
                    "",
                    this,
                    dimensions,
                    metrics,
                    intervals,
                    filters,
                    null
            );

            if (requestMapper != null) {
                tablesApiRequest = (TablesApiRequestImpl) requestMapper.apply(
                        tablesApiRequest,
                        containerRequestContext
                );
            }

            Map<String, Object> result = getConstrainedLogicalTableDetailView(
                    tablesApiRequest,
                    containerRequestContext.getUriInfo()
            );
            String output = objectMappers.getMapper().writeValueAsString(result);
            LOG.trace("Tables Endpoint Response: {}", output);
            return Response.status(OK).entity(output).build();
        } catch (Throwable t) {
            return exceptionHandler.handleThrowable(
                    t,
                    Optional.ofNullable(tablesApiRequest),
                    containerRequestContext
            );
        } finally {
            RequestLog.stopTiming(this);
        }
    }

    /**
     * Get all the tables with the full view format.
     *
     * @param perPage  number of values to return per page
     * @param page  the page to start from
     * @param uriInfo  UriInfo of the request
     * @param containerRequestContext  The context of data provided by the Jersey container for this request
     *
     * @return full view of the tables
     */
    @Timed
    public Response getTablesRollupView(
            @DefaultValue("") @NotNull @QueryParam("perPage") String perPage,
            @DefaultValue("") @NotNull @QueryParam("page") String page,
            @Context UriInfo uriInfo,
            @Context final ContainerRequestContext containerRequestContext
    ) {
        TablesApiRequestImpl tablesApiRequestImpl = null;
        try {
            RequestLog.startTiming(this);
            RequestLog.record(new TableRequest("all", "all"));

            tablesApiRequestImpl = new TablesApiRequestImpl(
                    null,
                    null,
                    null,
                    perPage,
                    page,
                    this
            );

            if (requestMapper != null) {
                tablesApiRequestImpl = (TablesApiRequestImpl) requestMapper.apply(
                        tablesApiRequestImpl,
                        containerRequestContext
                );
            }
            TableFullViewFormatter fullViewFormatter = DefaultMetadataViewFormatters.rollupMetadataFormatter;

            Response response = paginateAndFormatResponse(
                    tablesApiRequestImpl,
                    containerRequestContext,
                    fullViewFormatter.formatTables(tablesApiRequestImpl.getTables(), uriInfo),
                    "tables",
                    null
            );

            LOG.trace("Tables Endpoint Response: {}", response.getEntity());
            return response;
        } catch (Throwable t) {
            return exceptionHandler.handleThrowable(
                    t,
                    Optional.ofNullable(tablesApiRequestImpl),
                    containerRequestContext
            );
        } finally {
            RequestLog.stopTiming(this);
        }
    }

    @Override
    public ResourceDictionaries getResourceDictionaries() {
        return resourceDictionaries;
    }

    @Override
    public GranularityParser getGranularityParser() {
        return granularityParser;
    }

    /**
     * Filter builder isn't used in TablesServlet but is part of the configuration interface, so this is an empty
     * implementation.
     *
     * @return null because TablesApiRequest doesn't require it
     */
    @Override
    public DruidFilterBuilder getFilterBuilder() {
        return null;
    }

    /**
     * Having Api generator isn't used in TablesServlet but is part of the configuration interface, so this is an empty
     * implementation.
     *
     * @return null because TablesApiRequest doesn't require it
     */
    @Override
    public HavingGenerator getHavingApiGenerator() {
        return null;
    }


    /**
     * SystemTimeZone isn't used in TablesServlet but is part of the configuration interface, so this is an empty
     * implementation.
     *
     * @return null because TablesApiRequest doesn't require it
     */
    @Override
    public DateTimeZone getSystemTimeZone() {
        return null;
    }

    public RequestMapper<TablesApiRequest> getRequestMapper() {
        return requestMapper;
    }

    @Override
    public LogicalTableDictionary getLogicalTableDictionary() {
        return resourceDictionaries.getLogicalDictionary();
    }

    @Override
    public DateTimeFormatter getDateTimeFormatter() {
        return dateTimeFormatter;
    }


    /**
     * Get the full view of the logical table and remap the availability based on the tablesApiRequest constraints.
     *
     * @param tablesApiRequest  Table API request that contains information about requested logical table and provides
     * components for constructing a {@link com.yahoo.bard.webservice.table.resolver.QueryPlanningConstraint}, which
     * will be used to filter and constrain table availabilities
     * @param uriInfo  UriInfo of the request
     *
     * @return Full view of the logical table
     */
    public MetadataObject getConstrainedLogicalTableDetailView(
            TablesApiRequestImpl tablesApiRequest,
            UriInfo uriInfo
    ) {
        LogicalTable logicalTable = tablesApiRequest.getTable();
        MetadataObject details = tableMetadataFormatter.formatTable(logicalTable, uriInfo);

        // replace availability with filtered availability
        SimplifiedIntervalList available = TableUtils.getConstrainedLogicalTableAvailability(
                logicalTable,
                new QueryPlanningConstraint(
                        tablesApiRequest.getDimensions(),
                        tablesApiRequest.getFilterDimensions(),
                        Collections.emptySet(),
                        Collections.emptySet(),
                        tablesApiRequest.getApiFilters(),
                        tablesApiRequest.getTable(),
                        Collections.unmodifiableList(tablesApiRequest.getIntervals()),
                        Collections.unmodifiableSet(tablesApiRequest.getLogicalMetrics()),
                        tablesApiRequest.getGranularity(),
                        tablesApiRequest.getGranularity()
                )
        );
        details.put("availableIntervals", available);
        return details;
    }
}
