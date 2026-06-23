package com.podsadowski.warehouse.scheduler;

import com.podsadowski.warehouse.config.MutableClock;
import com.podsadowski.warehouse.service.RelocationEngine;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@AllArgsConstructor
@Slf4j
public class RelocationScheduler {

    private final RelocationEngine relocationEngine;
    private final MutableClock mutableClock;

    @Scheduled(fixedRate = 10000)
    public void triggerRelocation() {
        mutableClock.addMinutes(10);

        log.info("Time: {}", LocalDateTime.now(mutableClock));

        relocationEngine.checkAndBalanceWorkforce();
    }
}
