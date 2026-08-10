package com.podsadowski.warehouse.simulation;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.dto.WorkProgressEvent;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.performance.EmployeePerformanceService;
import com.podsadowski.warehouse.relocation.RelocationFulfillmentService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Demo-profile per-employee work loop. Simulates item processing at accelerated time
 * and publishes {@link WorkProgressEvent} records to Kafka.
 */
@Slf4j
@AllArgsConstructor
public class EmployeeSimulationTask implements Runnable {

    private final Long employeeId;
    private final EmployeeRepository employeeRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SimulationTimeService simulationTimeService;
    private final EmployeePerformanceService employeePerformanceService;
    private final RelocationFulfillmentService relocationFulfillmentService;
    private final WarehouseProperties warehouseProperties;

    @Override
    public void run() {
        WarehouseProperties.Simulation simulation = warehouseProperties.getSimulation();
        String progressTopic = warehouseProperties.getKafka().getProgressTopic();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                long simulatedIntervalSeconds = simulationTimeService.randomWorkIntervalSeconds();
                long realSleepMs = simulationTimeService.toRealSleepMs(simulatedIntervalSeconds);
                Thread.sleep(realSleepMs);

                Employee employee = employeeRepository.findById(employeeId).orElse(null);

                if (employee == null) {
                    continue;
                }

                LocalDateTime now = LocalDateTime.now(clock);

                if (employee.isOnBreak() || employee.isInTransit(now)) {
                    Thread.sleep(simulation.getBreakPollMs());
                    continue;
                }

                if (employee.getCurrentDepartment() == null) {
                    continue;
                }

                // Leave order empty while a relocation is pending so the tick can start transit.
                if (!employee.hasActiveOrder()) {
                    if (employee.hasPendingRelocation()) {
                        Thread.sleep(simulation.getBreakPollMs());
                        continue;
                    }
                    employee.setOrderItemsRemaining(relocationFulfillmentService.randomOrderSize());
                    employeeRepository.save(employee);
                }

                var department = employee.getCurrentDepartment();
                double targetIph = employeePerformanceService.getEffectiveWorkIph(
                        employee, department.getName(), now);

                double intervalHours = simulatedIntervalSeconds / 3600.0;
                double randomFactor = ThreadLocalRandom.current().nextDouble(
                        simulation.getThroughputRandomMin(),
                        simulation.getThroughputRandomMax());
                int processed = probabilisticRound(targetIph * intervalHours * randomFactor);
                processed = Math.min(processed, employee.getOrderItemsRemaining());

                if (processed <= 0) {
                    continue;
                }

                employee.setOrderItemsRemaining(employee.getOrderItemsRemaining() - processed);
                employeeRepository.save(employee);

                WorkProgressEvent event = new WorkProgressEvent(
                        employee.getId(),
                        department.getName(),
                        processed,
                        now,
                        simulatedIntervalSeconds
                );

                String jsonEvent = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(progressTopic, employee.getId().toString(), jsonEvent);
            } catch (InterruptedException e) {
                log.info("Employee thread interrupted, ID: {}", employeeId);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error while simulating employees work ID: {}", employeeId, e);
            }
        }
    }

    private int probabilisticRound(double value) {
        if (value <= 0) {
            return 0;
        }
        int whole = (int) Math.floor(value);
        double fraction = value - whole;
        if (ThreadLocalRandom.current().nextDouble() < fraction) {
            return whole + 1;
        }
        return whole;
    }
}
