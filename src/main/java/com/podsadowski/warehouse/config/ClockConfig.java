package com.podsadowski.warehouse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration
public class ClockConfig {
    @Bean
    @Profile("!demo")
    public Clock getClock() {
        return Clock.systemDefaultZone();
    }
}
