package com.podsadowski.warehouse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration
public class ClockConfig {

    @Bean
    @Profile("!demo")
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    @Profile("demo")
    public Clock mutableClock(WarehouseProperties warehouseProperties) {
        return new MutableClock(warehouseProperties);
    }
}
