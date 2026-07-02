package com.podsadowski.warehouse.scheduler;

import com.podsadowski.warehouse.repository.WorkLogRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@AllArgsConstructor
@Slf4j
public class WorkLogPurgeJob {

    private final WorkLogRepository workLogRepository;
    private final Clock clock;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void purgeOldWorkLogs() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(7);

        log.info("Purging old work logs");

        workLogRepository.deleteByTimestampBefore(cutoff);
    }
}
