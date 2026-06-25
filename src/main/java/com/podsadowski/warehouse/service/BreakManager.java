package com.podsadowski.warehouse.service;

import com.podsadowski.warehouse.entity.BreakTier;
import com.podsadowski.warehouse.entity.Employee;
import com.podsadowski.warehouse.repository.EmployeeRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;


@Service
@AllArgsConstructor
@Slf4j
public class BreakManager {

    //1st shift
    private final LocalTime FIRST_BREAK_START_FS = LocalTime.of(9, 0);
    private final LocalTime FIRST_BREAK_END_FS = LocalTime.of(9, 30);

    private final LocalTime SECOND_BREAK_START_FS = LocalTime.of(12, 0);
    private final LocalTime SECOND_BREAK_END_FS = LocalTime.of(12, 30);

    //2nd shift
    private final LocalTime FIRST_BREAK_START_SS = LocalTime.of(9, 30);
    private final LocalTime FIRST_BREAK_END_SS = LocalTime.of(10, 0);

    private final LocalTime SECOND_BREAK_START_SS = LocalTime.of(12, 30);
    private final LocalTime SECOND_BREAK_END_SS = LocalTime.of(13, 0);

    private final EmployeeRepository employeeRepository;

    @Transactional
    public void processBreaks(LocalTime time) {
        List<Employee> allEmployees = employeeRepository.findAll();

        for(Employee employee : allEmployees) {
            boolean shouldBeOnBreak = determineIfShouldBeOnBreak(employee.getBreakTier(), time);

            if(shouldBeOnBreak && !employee.isOnBreak()) {
                startBreak(employee);
            } else if (!shouldBeOnBreak && employee.isOnBreak()) {
                endBreak(employee);
            }
        }
    }

    private boolean determineIfShouldBeOnBreak(BreakTier breakTier, LocalTime time) {
        if (breakTier == BreakTier.FIRST_SHIFT) {
            return (!time.isBefore(FIRST_BREAK_START_FS) && time.isBefore(FIRST_BREAK_END_FS)) ||
                    (!time.isBefore(SECOND_BREAK_START_FS) && time.isBefore(SECOND_BREAK_END_FS));

        } else if (breakTier == BreakTier.SECOND_SHIFT) {
            return (!time.isBefore(FIRST_BREAK_START_SS) && time.isBefore(FIRST_BREAK_END_SS)) ||
                    (!time.isBefore(SECOND_BREAK_START_SS) && time.isBefore(SECOND_BREAK_END_SS));
        }

        return false;
    }

    private void startBreak(Employee employee) {
        employee.setOnBreak(true);

        employeeRepository.save(employee);

        log.info("Employee {} has started his break.", employee.getId());
    }

    private void endBreak(Employee employee) {
        employee.setOnBreak(false);

        employeeRepository.save(employee);

        log.info("Employee {} has ended his break.", employee.getId());
    }
}
