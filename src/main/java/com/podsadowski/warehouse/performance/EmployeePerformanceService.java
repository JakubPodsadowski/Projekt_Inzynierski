package com.podsadowski.warehouse.performance;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.domain.repository.WorkLogRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates per-employee IPH from {@code work_logs} and predicts IPH in new departments.
 * Invoked on every scheduler tick after work logs are persisted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeePerformanceService {

    private final WorkLogRepository workLogRepository;
    private final EmployeeRepository employeeRepository;
    private final WarehouseProperties warehouseProperties;

    private Map<String, Double> iphCache = Map.of();

    public double getDefaultIph(DepartmentName department) {
        return warehouseProperties.getPerformance()
                .getDefaultIph()
                .getOrDefault(department, 50.0);
    }

    public double getIph(Long employeeId, DepartmentName department, LocalDateTime now) {
        return iphCache.getOrDefault(cacheKey(employeeId, department), getDefaultIph(department));
    }

    public boolean hasObservedIph(Long employeeId, DepartmentName department) {
        return iphCache.containsKey(cacheKey(employeeId, department));
    }

    public double getEffectiveWorkIph(Employee employee, DepartmentName department, LocalDateTime now) {
        if (hasObservedIph(employee.getId(), department)) {
            return getIph(employee.getId(), department, now);
        }
        if (employee.getCurrentDepartment() != null
                && employee.getCurrentDepartment().getName() == department) {
            return getDefaultIph(department);
        }
        return predictIphAtDepartment(employee, department, now);
    }

    public double predictIphAtDepartment(Employee employee, DepartmentName targetDepartment, LocalDateTime now) {
        if (hasObservedIph(employee.getId(), targetDepartment)) {
            return getIph(employee.getId(), targetDepartment, now);
        }

        double targetNorm = getDefaultIph(targetDepartment);

        if (employee.getCurrentDepartment() == null || employee.getCurrentIph() <= 0) {
            return targetNorm;
        }

        DepartmentName currentDepartment = employee.getCurrentDepartment().getName();
        double currentNorm = getDefaultIph(currentDepartment);
        if (currentNorm <= 0) {
            return targetNorm;
        }

        double personalFactor = employee.getCurrentIph() / currentNorm;
        return personalFactor * targetNorm;
    }

    @Transactional
    public void recalculateAllEmployeeIph(LocalDateTime now) {
        rebuildCache(now);

        List<Employee> employees = employeeRepository.findAll();
        for (Employee employee : employees) {
            if (employee.getCurrentDepartment() == null || employee.isOnBreak()) {
                continue;
            }

            DepartmentName department = employee.getCurrentDepartment().getName();
            employee.setCurrentIph(getEffectiveWorkIph(employee, department, now));
        }

        employeeRepository.saveAll(employees);
    }

    private void rebuildCache(LocalDateTime now) {
        Map<String, Double> freshCache = new HashMap<>();

        int windowDays = warehouseProperties.getPerformance().getObservationWindowDays();
        double minWorkedHours = warehouseProperties.getPerformance().getMinWorkedHours();
        LocalDateTime since = now.minusDays(windowDays);

        List<Object[]> aggregates = workLogRepository.aggregateWorkSince(since);
        for (Object[] row : aggregates) {
            Long employeeId = (Long) row[0];
            DepartmentName department = (DepartmentName) row[1];
            long totalItems = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            long totalElapsedSeconds = row[3] != null ? ((Number) row[3]).longValue() : 0L;

            double workedHours = totalElapsedSeconds / 3600.0;
            if (workedHours < minWorkedHours) {
                continue;
            }

            freshCache.put(cacheKey(employeeId, department), totalItems / workedHours);
        }

        iphCache = freshCache;
        log.debug("Refreshed IPH cache with {} employee-department entries", iphCache.size());
    }

    private String cacheKey(Long employeeId, DepartmentName department) {
        return employeeId + ":" + department.name();
    }
}
