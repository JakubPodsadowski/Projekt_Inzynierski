package com.podsadowski.warehouse.config;

import java.time.*;
import java.time.temporal.ChronoUnit;

public class MutableClock extends Clock {
    private final TimeState state;
    private final ZoneId zoneId;

    public MutableClock(WarehouseProperties warehouseProperties) {
        this.zoneId = ZoneId.systemDefault();
        LocalTime startTime = warehouseProperties.getSimulation().getStartTime();
        Instant initialInstant = LocalDate.now()
                .atTime(startTime)
                .atZone(this.zoneId)
                .toInstant();
        this.state = new TimeState(initialInstant);
    }

    private MutableClock(TimeState state, ZoneId zoneId) {
        this.state = state;
        this.zoneId = zoneId;
    }

    @Override
    public ZoneId getZone() {
        return this.zoneId;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (zone.equals(this.zoneId)) {
            return this;
        }
        return new MutableClock(this.state, zone);
    }

    @Override
    public Instant instant() {
        return this.state.getInstant();
    }

    public void addMinutes(long minutes) {
        this.state.setInstant(this.state.getInstant().plus(minutes, ChronoUnit.MINUTES));
    }

    public void addSeconds(long seconds) {
        this.state.setInstant(this.state.getInstant().plus(seconds, ChronoUnit.SECONDS));
    }

    private static class TimeState {
        private Instant instant;

        TimeState(Instant instant) {
            this.instant = instant;
        }

        Instant getInstant() {
            return instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }
    }
}
