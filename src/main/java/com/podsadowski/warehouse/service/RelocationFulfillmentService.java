package com.podsadowski.warehouse.service;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.entity.Department;
import com.podsadowski.warehouse.entity.Employee;
import com.podsadowski.warehouse.repository.EmployeeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class RelocationFulfillmentService {

    private final EmployeeRepository employeeRepository;
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
        log.info("Employee {} walking to {} ({} min)",
                employee.getId(),
                employee.getTargetDepartment().getName(),
                travelMinutes);
    }

    private void completeRelocation(Employee employee, LocalDateTime now) {
        Department target = employee.getTargetDepartment();
        Department previous = employee.getCurrentDepartment();

        employee.setCurrentDepartment(target);
        employee.setTargetDepartment(null);
        employee.setRelocatingUntil(null);
        employee.setLastRelocatedAt(now);
        employee.setRelocationCountToday(employee.getRelocationCountToday() + 1);
        employee.setCurrentIph(employeePerformanceService.predictIphAtDepartment(
                employee, target.getName(), now));
        employee.setOrderItemsRemaining(randomOrderSize());

        log.info("Employee {} arrived at {} (from {}, expected {} IPH)",
                employee.getId(), target.getName(),
                previous != null ? previous.getName() : "none",
                String.format("%.1f", employee.getCurrentIph()));
    }

    public int randomOrderSize() {
        WarehouseProperties.Relocation config = warehouseProperties.getRelocation();
        return ThreadLocalRandom.current().nextInt(config.getOrderItemsMin(), config.getOrderItemsMax() + 1);
    }
}
