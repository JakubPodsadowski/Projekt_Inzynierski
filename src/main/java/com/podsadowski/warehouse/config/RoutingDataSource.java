package com.podsadowski.warehouse.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class RoutingDataSource extends AbstractRoutingDataSource {

    private final DataSourceModeHolder modeHolder;

    public RoutingDataSource(DataSourceModeHolder modeHolder) {
        this.modeHolder = modeHolder;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return modeHolder.get();
    }
}
