package com.podsadowski.warehouse.relocation;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.entity.RelocationLog;
import com.podsadowski.warehouse.domain.entity.RelocationLogStatus;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.domain.repository.RelocationLogRepository;
import com.podsadowski.warehouse.performance.EmployeePerformanceService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executes scheduled relocations in three phases: finish current order, walk to target, arrive.
 * Called on every scheduler tick before IPH recalculation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelocationFulfillmentService {

    private final EmployeeRepository employeeRepository;
    private final RelocationLogRepository relocationLogRepository;
    private final EmployeePerformanceService employeePerformanceService;
    private final WarehouseProperties warehouseProperties;

    @Transactional
    public void processPendingRelocations(LocalDateTime now) {
        List<Employee> employees = employeeRepository.findAll();

        for (Employee employee : employees) {
            if (!employee.hasPendingRelocation()) {
                continue;
            }

            if (employee.hasActiveOrder()) {
                continue;
            }

            if (employee.getRelocatingUntil() == null) {
                startTransit(employee, now);
                continue;
            }

            if (!employee.getRelocatingUntil().isAfter(now)) {
                completeRelocation(employee, now);
            }
        }

        employeeRepository.saveAll(employees);
    }

    private void startTransit(Employee employee, LocalDateTime now) {
        long travelMinutes = warehouseProperties.getRelocation().getTravelMinutes();
        employee.setRelocatingUntil(now.plusMinutes(travelMinutes));
        markLogInTransit(employee.getId(), now);
        log.info("Employee {} walking to {} ({} min)",
                employee.getId(),
                employee.getTargetDepartment().getName(),
                travelMinutes);
    }

    private void completeRelocation(Employee employee, LocalDateTime now) {
        Department target = employee.getTargetDepartment();
        Department previous = employee.getCurrentDepartment();

        double predictedIph = employeePerformanceService.predictIphAtDepartment(
                employee, target.getName(), now);

        markLogCompleted(employee.getId(), now, predictedIph);

        employee.setCurrentDepartment(target);
        employee.setTargetDepartment(null);
        employee.setRelocatingUntil(null);
        employee.setLastRelocatedAt(now);
        employee.setRelocationCountToday(employee.getRelocationCountToday() + 1);
        employee.setCurrentIph(predictedIph);
        employee.setOrderItemsRemaining(randomOrderSize());

        log.info("Employee {} arrived at {} (from {}, expected {} IPH)",
                employee.getId(), target.getName(),
                previous != null ? previous.getName() : "none",
                String.format("%.1f", employee.getCurrentIph()));
    }

    private void markLogInTransit(Long employeeId, LocalDateTime now) {
        relocationLogRepository.findByEmployeeIdAndArrivedAtIsNull(employeeId).ifPresentOrElse(
                relocationLog -> {
                    relocationLog.setStatus(RelocationLogStatus.IN_TRANSIT);
                    relocationLog.setTransitStartedAt(now);
                    relocationLogRepository.save(relocationLog);
                },
                () -> log.warn("No open relocation log for employee {} when starting transit", employeeId)
        );
    }

    private void markLogCompleted(Long employeeId, LocalDateTime now, double iphOnArrival) {
        relocationLogRepository.findByEmployeeIdAndArrivedAtIsNull(employeeId).ifPresentOrElse(
                relocationLog -> {
                    relocationLog.setStatus(RelocationLogStatus.COMPLETED);
                    relocationLog.setArrivedAt(now);
                    relocationLog.setIphOnArrival(iphOnArrival);
                    relocationLogRepository.save(relocationLog);
                },
                () -> log.warn("No open relocation log for employee {} when completing relocation", employeeId)
        );
    }

    public int randomOrderSize() {
        WarehouseProperties.Relocation config = warehouseProperties.getRelocation();
        return ThreadLocalRandom.current().nextInt(config.getOrderItemsMin(), config.getOrderItemsMax() + 1);
    }
}
