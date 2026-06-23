package com.podsadowski.warehouse.scheduler;

import com.podsadowski.warehouse.service.RelocationEngine;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Slf4j
public class RelocationScheduler {

    private final RelocationEngine relocationEngine;

    @Scheduled(fixedRate = 5000)
    public void triggerRelocation() {
        log.info("Trigger relocation");
        relocationEngine.checkAndBalanceWorkforce();
    }
}
