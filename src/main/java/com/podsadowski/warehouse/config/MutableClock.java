package com.podsadowski.warehouse.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.ChronoUnit;

@Component
@Profile("demo")
public class MutableClock extends Clock {

    private final TimeState state;
    private final ZoneId zoneId;

    public MutableClock() {
        this.zoneId = ZoneId.systemDefault();
        Instant initialInstant = LocalDate.now()
                .atTime(6, 0)
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

    @Setter
    @Getter
    private static class TimeState {
        private Instant instant;

        public TimeState(Instant instant) {
            this.instant = instant;
        }

    }
}