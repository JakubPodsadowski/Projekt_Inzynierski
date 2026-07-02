package com.podsadowski.warehouse.scheduler;

import com.podsadowski.warehouse.config.MutableClock;
import com.podsadowski.warehouse.entity.Employee;
import com.podsadowski.warehouse.repository.EmployeeRepository;
import com.podsadowski.warehouse.service.BreakManager;
import com.podsadowski.warehouse.service.EmployeePerformanceService;
import com.podsadowski.warehouse.service.IphCalculationService;
import com.podsadowski.warehouse.service.RelocationEngine;
import com.podsadowski.warehouse.service.RelocationFulfillmentService;
import com.podsadowski.warehouse.simulation.SimulationTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RelocationScheduler {

    private final RelocationEngine relocationEngine;
    private final RelocationFulfillmentService relocationFulfillmentService;
    private final Clock clock;
    private final BreakManager breakManager;
    private final EmployeePerformanceService employeePerformanceService;
    private final IphCalculationService iphCalculationService;
    private final SimulationTimeService simulationTimeService;
    private final EmployeeRepository employeeRepository;

    private LocalDate lastProcessedDate;

    @Scheduled(fixedRateString = "${warehouse.scheduler.tick-interval-ms:10000}")
    public void triggerRelocation() {
        if (clock instanceof MutableClock mutableClock) {
            mutableClock.addMinutes(simulationTimeService.getSimulatedMinutesPerTick());
        }

        LocalDateTime currentTime = LocalDateTime.now(clock);
        log.debug("Time: {}", currentTime);

        breakManager.processBreaks(currentTime.toLocalTime());
        relocationFulfillmentService.processPendingRelocations(currentTime);
        employeePerformanceService.recalculateAllEmployeeIph(currentTime);
        iphCalculationService.recalculateDepartmentIph();
        relocationEngine.checkAndBalanceWorkforce();
        iphCalculationService.recalculateDepartmentIph();
        resetDailyCountersIfMidnight(currentTime.toLocalDate());
    }

    private void resetDailyCountersIfMidnight(LocalDate currentDate) {
        if (lastProcessedDate != null && currentDate.isAfter(lastProcessedDate)) {
            List<Employee> employees = employeeRepository.findAll();
            for (Employee employee : employees) {
                employee.setRelocationCountToday(0);
            }
            employeeRepository.saveAll(employees);
            log.info("Reset relocation counters for new simulated day: {}", currentDate);
        }
        lastProcessedDate = currentDate;
    }
}
