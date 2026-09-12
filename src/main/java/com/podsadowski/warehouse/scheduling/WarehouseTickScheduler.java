package com.podsadowski.warehouse.scheduling;

import com.podsadowski.warehouse.config.MutableClock;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.performance.EmployeePerformanceService;
import com.podsadowski.warehouse.performance.IphCalculationService;
import com.podsadowski.warehouse.relocation.RelocationEngine;
import com.podsadowski.warehouse.relocation.RelocationFulfillmentService;
import com.podsadowski.warehouse.simulation.SimulationRuntimeState;
import com.podsadowski.warehouse.simulation.SimulationTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestrates one simulation tick: advances time, applies breaks, fulfills relocations,
 * recalculates IPH, and plans new workforce moves.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseTickScheduler {

    private final RelocationEngine relocationEngine;
    private final RelocationFulfillmentService relocationFulfillmentService;
    private final Clock clock;
    private final BreakManager breakManager;
    private final EmployeePerformanceService employeePerformanceService;
    private final IphCalculationService iphCalculationService;
    private final SimulationTimeService simulationTimeService;
    private final SimulationRuntimeState simulationRuntimeState;
    private final EmployeeRepository employeeRepository;

    private LocalDate lastProcessedDate;

    @Scheduled(fixedRateString = "${warehouse.scheduler.tick-interval-ms}")
    public void runTick() {
        if (simulationRuntimeState.isPaused()) {
            return;
        }

        advanceSimulatedClock();

        LocalDateTime currentTime = LocalDateTime.now(clock);
        log.debug("Tick at simulated time: {}", currentTime);

        breakManager.processBreaks(currentTime.toLocalTime());
        relocationFulfillmentService.processPendingRelocations(currentTime);
        employeePerformanceService.recalculateAllEmployeeIph(currentTime);
        iphCalculationService.recalculateDepartmentIph();
        relocationEngine.checkAndBalanceWorkforce();
        resetDailyCountersIfNewDay(currentTime.toLocalDate());
    }

    private void advanceSimulatedClock() {
        if (clock instanceof MutableClock mutableClock) {
            mutableClock.addSeconds(simulationTimeService.getSimulatedSecondsPerTick());
        }
    }

    private void resetDailyCountersIfNewDay(LocalDate currentDate) {
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
