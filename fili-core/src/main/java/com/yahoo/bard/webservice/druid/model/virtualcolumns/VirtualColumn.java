// Copyright 2016 Yahoo Inc.
// Licensed under the terms of the Apache license. Please see LICENSE.md file distributed with this work for terms.
package com.yahoo.bard.webservice.druid.model.virtualcolumns;

/**
 * This marker interface combines Aggregations and PostAggregations so that they can be collectively referenced.
 * <p>
 * This may become a sub interface of a TBD Column interface to flag all dimensions and metrics.
 */
public interface VirtualColumns {

    /**
     * Get the name of the virtual column in the response.
     *
     * @return the name of the virtual column in the response
     */
    String getName();

    /**
     * Get the type of the virtual column in the response.
     *
     * @return the type of the virtual column in the response
     */
    String getType();

    /**
     * Get the expression of the virtual column in the response.
     *
     * @return the expression of the virtual column in the response
     */
    String getExpression();

    /**
     * Get the output type of the virtual column in the response.
     *
     * @return the output type of the virtual column in the response
     */
    String getOutputType();

    /**
     * Makes a copy of this virtual column with the current name replaced by the provided name.
     *
     * @param name The new output name for virtual column
     * @return the updated copy
     */
    VirtualColumns withName(String name);
}
