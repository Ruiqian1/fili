// Copyright 2019 Oath Inc.
// Licensed under the terms of the Apache license. Please see LICENSE.md file distributed with this work for terms.
package com.yahoo.bard.webservice.data.config.luthier.factories.metricmaker;

import com.yahoo.bard.webservice.application.luthier.LuthierConfigNode;
import com.yahoo.bard.webservice.data.config.luthier.Factory;
import com.yahoo.bard.webservice.data.config.luthier.LuthierIndustrialPark;
import com.yahoo.bard.webservice.data.config.metric.makers.CountMaker;
import com.yahoo.bard.webservice.data.config.metric.makers.MetricMaker;

/**
 * Factory that supports CountMaker.
 */
public class CountMakerFactory implements Factory<MetricMaker> {
    @Override
    public MetricMaker build(String name, LuthierConfigNode configTable, LuthierIndustrialPark resourceFactories) {
        return new CountMaker(resourceFactories.getMetricDictionary());
    }
}
