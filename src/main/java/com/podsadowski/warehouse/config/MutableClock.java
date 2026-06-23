package com.podsadowski.warehouse.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.ChronoUnit;

@Component
@Profile("demo")
public class MutableClock extends Clock {

    private Instant currentInstant;
    private final ZoneId zoneId;

    public MutableClock() {
        this.zoneId = ZoneId.systemDefault();
        this.currentInstant = LocalDate.now().atTime(6, 0).atZone(this.zoneId).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return this.zoneId;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return this.currentInstant;
    }

    public void addMinutes(long minutes) {
        this.currentInstant = this.currentInstant.plus(minutes, ChronoUnit.MINUTES);
    }
}
