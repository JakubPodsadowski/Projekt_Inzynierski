package com.podsadowski.warehouse.config;

import com.podsadowski.warehouse.api.dto.SystemMode;

import java.util.concurrent.atomic.AtomicReference;

/**
 * App-wide DEMO/REAL database mode. Not thread-local — schedulers and simulation
 * workers must see the same active datasource as HTTP requests.
 */
public class DataSourceModeHolder {

    private final AtomicReference<SystemMode> mode;

    public DataSourceModeHolder(SystemMode initialMode) {
        this.mode = new AtomicReference<>(initialMode);
    }

    public SystemMode get() {
        return mode.get();
    }

    public void set(SystemMode mode) {
        this.mode.set(mode);
    }

    public boolean isDemo() {
        return get() == SystemMode.DEMO;
    }
}
