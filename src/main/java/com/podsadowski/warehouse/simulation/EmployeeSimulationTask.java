package com.podsadowski.warehouse.simulation;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.dto.WorkProgressEvent;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.order.OutboundOrderService;
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
 * Demo-profile per-employee work loop.
 * PICKING / PACKING consume the outbound order pipeline; PUTAWAY keeps a local item counter
 * until inbound putaway (stage 3) lands.
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
    private final OutboundOrderService outboundOrderService;

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

                if (employee.hasPendingRelocation() && !employee.hasActiveOrder()) {
                    Thread.sleep(simulation.getBreakPollMs());
                    continue;
                }

                DepartmentName departmentName = employee.getCurrentDepartment().getName();
                int processed = switch (departmentName) {
                    case PICKING -> processPicking(employee, simulatedIntervalSeconds, now);
                    case PACKING -> processPacking(employee, simulatedIntervalSeconds, now);
                    case PUTAWAY -> processLegacyPutaway(employee, simulatedIntervalSeconds, now, simulation);
                };

                if (processed <= 0) {
                    continue;
                }

                WorkProgressEvent event = new WorkProgressEvent(
                        employee.getId(),
                        departmentName,
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

    private int processPicking(Employee employee, long simulatedIntervalSeconds, LocalDateTime now) {
        if (employee.hasPendingRelocation() && !employee.hasActiveOrder()) {
            return 0;
        }
        if (outboundOrderService.ensurePickAssignment(employeeId).isEmpty()) {
            return 0;
        }
        int capacity = throughputCapacity(employee, simulatedIntervalSeconds, now);
        return outboundOrderService.progressPick(employeeId, capacity);
    }

    private int processPacking(Employee employee, long simulatedIntervalSeconds, LocalDateTime now) {
        if (employee.hasPendingRelocation() && !employee.hasActiveOrder()) {
            return 0;
        }
        if (outboundOrderService.ensurePackAssignment(employeeId).isEmpty()) {
            return 0;
        }
        int capacity = throughputCapacity(employee, simulatedIntervalSeconds, now);
        return outboundOrderService.progressPack(employeeId, capacity);
    }

    private int processLegacyPutaway(
            Employee employee,
            long simulatedIntervalSeconds,
            LocalDateTime now,
            WarehouseProperties.Simulation simulation
    ) {
        // Leave order empty while a relocation is pending so the tick can start transit.
        if (!employee.hasActiveOrder()) {
            if (employee.hasPendingRelocation()) {
                return 0;
            }
            employee.setOrderItemsRemaining(relocationFulfillmentService.randomOrderSize());
            employeeRepository.save(employee);
        }

        int processed = Math.min(
                throughputCapacity(employee, simulatedIntervalSeconds, now),
                employee.getOrderItemsRemaining()
        );
        if (processed <= 0) {
            return 0;
        }
        employee.setOrderItemsRemaining(employee.getOrderItemsRemaining() - processed);
        employeeRepository.save(employee);
        return processed;
    }

    private int throughputCapacity(Employee employee, long simulatedIntervalSeconds, LocalDateTime now) {
        WarehouseProperties.Simulation simulation = warehouseProperties.getSimulation();
        double targetIph = employeePerformanceService.getEffectiveWorkIph(
                employee, employee.getCurrentDepartment().getName(), now);
        double intervalHours = simulatedIntervalSeconds / 3600.0;
        double randomFactor = ThreadLocalRandom.current().nextDouble(
                simulation.getThroughputRandomMin(),
                simulation.getThroughputRandomMax());
        return probabilisticRound(targetIph * intervalHours * randomFactor);
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
