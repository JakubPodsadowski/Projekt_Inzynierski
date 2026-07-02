package com.podsadowski.warehouse.kafka;

import com.podsadowski.warehouse.dto.WorkProgressEvent;
import com.podsadowski.warehouse.entity.WorkLog;
import com.podsadowski.warehouse.repository.WorkLogRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@AllArgsConstructor
@Slf4j
public class WorkProgressConsumer {

    private final ObjectMapper objectMapper;
    private final WorkLogRepository workLogRepository;

    @KafkaListener(topics = "warehouse-progress", groupId = "warehouse-analytics-group")
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
            log.error("Error while processing work progress message", e);
        }
    }
}