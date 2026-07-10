package com.podsadowski.warehouse.scheduling;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.BreakTier;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

/**
 * Applies configured shift break windows to all employees on each scheduler tick.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BreakManager {

    private final EmployeeRepository employeeRepository;
    private final WarehouseProperties warehouseProperties;

    @Transactional
    public void processBreaks(LocalTime time) {
        List<Employee> allEmployees = employeeRepository.findAll();
        boolean anyChanged = false;

        for (Employee employee : allEmployees) {
            boolean shouldBeOnBreak = determineIfShouldBeOnBreak(employee.getBreakTier(), time);

            if (shouldBeOnBreak && !employee.isOnBreak()) {
                employee.setOnBreak(true);
                log.info("Employee {} has started his break.", employee.getId());
                anyChanged = true;
            } else if (!shouldBeOnBreak && employee.isOnBreak()) {
                employee.setOnBreak(false);
                log.info("Employee {} has ended his break.", employee.getId());
                anyChanged = true;
            }
        }

        if (anyChanged) {
            employeeRepository.saveAll(allEmployees);
        }
    }

    private boolean determineIfShouldBeOnBreak(BreakTier breakTier, LocalTime time) {
        WarehouseProperties.ShiftBreakSchedule schedule = switch (breakTier) {
            case FIRST_SHIFT -> warehouseProperties.getBreaks().getFirstShift();
            case SECOND_SHIFT -> warehouseProperties.getBreaks().getSecondShift();
        };

        return isWithinBreak(time, schedule.getFirstBreakStart(), schedule.getFirstBreakEnd())
                || isWithinBreak(time, schedule.getSecondBreakStart(), schedule.getSecondBreakEnd());
    }

    private boolean isWithinBreak(LocalTime time, LocalTime start, LocalTime end) {
        return !time.isBefore(start) && time.isBefore(end);
    }
}
