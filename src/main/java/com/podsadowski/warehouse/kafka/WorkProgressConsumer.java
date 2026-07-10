package com.podsadowski.warehouse.kafka;

import com.podsadowski.warehouse.domain.dto.WorkProgressEvent;
import com.podsadowski.warehouse.domain.entity.WorkLog;
import com.podsadowski.warehouse.domain.repository.WorkLogRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists work-progress events from Kafka into {@code work_logs}.
 * IPH recalculation is handled by the scheduler tick, not here.
 */
@Component
@AllArgsConstructor
@Slf4j
public class WorkProgressConsumer {

    private final ObjectMapper objectMapper;
    private final WorkLogRepository workLogRepository;

    @KafkaListener(
            topics = "${warehouse.kafka.progress-topic}",
            groupId = "${warehouse.kafka.consumer-group-id}"
    )
    public void listenWarehouseProgress(String message) {
        try {
            WorkProgressEvent event = objectMapper.readValue(message, WorkProgressEvent.class);

            WorkLog workLog = new WorkLog();
            workLog.setEmployeeId(event.employeeId());
            workLog.setDepartmentName(event.departmentName());
            workLog.setItemsProcessed(event.itemsProcessed());
            workLog.setElapsedSeconds(event.elapsedSimulatedSeconds());
            workLog.setTimestamp(event.timestamp());

            workLogRepository.save(workLog);
        } catch (Exception e) {
            log.error("Error while deserializing message", e);
        }
    }
}
