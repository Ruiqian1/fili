// Copyright 2018 Yahoo Inc.
// Licensed under the terms of the Apache license. Please see LICENSE.md file distributed with this work for terms.
package com.yahoo.luthier.webservice.data.config.dimension;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * External Dimension Config Template.
 */
public interface ExternalDimensionConfigTemplate {

    /**
     * Get field configuration info.
     *
     * @return a map from fieldset name to fieldset
     */
    Map<String, List<DimensionFieldInfoTemplate>> getFieldSets();

    /**
     * Get dimensions configuration info.
     *
     * @return a set of dimensions
     */
    Set<DimensionTemplate> getDimensions();
}