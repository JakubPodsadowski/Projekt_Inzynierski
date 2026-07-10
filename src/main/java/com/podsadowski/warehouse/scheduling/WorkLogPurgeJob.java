package com.podsadowski.warehouse.scheduling;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.repository.WorkLogRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkLogPurgeJob {

    private final WorkLogRepository workLogRepository;
    private final Clock clock;
    private final WarehouseProperties warehouseProperties;

    @Scheduled(fixedRateString = "${warehouse.scheduler.work-log-purge-interval-ms}")
    @Transactional
    public void purgeOldWorkLogs() {
        int retentionDays = warehouseProperties.getPerformance().getObservationWindowDays();
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);

        log.info("Purging work logs older than {} days", retentionDays);

        workLogRepository.deleteByTimestampBefore(cutoff);
    }
}
