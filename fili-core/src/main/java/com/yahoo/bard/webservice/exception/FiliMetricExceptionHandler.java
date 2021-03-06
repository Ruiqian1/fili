// Copyright 2018 Oath Inc.
// Licensed under the terms of the Apache license. Please see LICENSE.md file distributed with this work for terms.
package com.yahoo.bard.webservice.exception;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import com.yahoo.bard.webservice.web.ErrorMessageFormat;
import com.yahoo.bard.webservice.web.RequestValidationException;
import com.yahoo.bard.webservice.web.apirequest.ApiRequest;
import com.yahoo.bard.webservice.web.apirequest.exceptions.MissingResourceApiRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;

/**
 * Default handler for exceptions in the MetricServlet.
 */
public class FiliMetricExceptionHandler implements MetadataExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(FiliMetricExceptionHandler.class);

    @Override
    public Response handleThrowable(
            Throwable e,
            Optional<? extends ApiRequest> request,
            ContainerRequestContext requestContext
    ) {
        if (e instanceof RequestValidationException) {
            LOG.debug(e.getMessage(), e);
            RequestValidationException rve = (RequestValidationException) e;
            return Response.status(rve.getStatus()).entity(rve.getErrorHttpMsg()).build();
        } else if (e instanceof IOException) {
            String msg = String.format("Internal server error. IOException : %s", e.getMessage());
            LOG.error(msg, e);
            return Response.status(INTERNAL_SERVER_ERROR).entity(msg).build();
        } else {
            Response.Status status;
            if (e instanceof MissingResourceApiRequestException) {
                status = Response.Status.NOT_FOUND;
            } else {
                status = Response.Status.BAD_REQUEST;
            }
            String msg = ErrorMessageFormat.REQUEST_PROCESSING_EXCEPTION.format(e.getMessage());
            LOG.debug(msg, e);
            return Response.status(status).entity(msg).build();
        }
    }
}
