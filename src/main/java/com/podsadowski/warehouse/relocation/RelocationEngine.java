package com.podsadowski.warehouse.relocation;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.DepartmentRepository;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.performance.EmployeePerformanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Plans workforce relocations from surplus to deficit departments.
 * Moves the weakest eligible performers based on how many top-performer equivalents
 * the deficit department needs. Only schedules moves; execution is handled by
 * {@link RelocationFulfillmentService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelocationEngine {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final Clock clock;
    private final EmployeePerformanceService employeePerformanceService;
    private final WarehouseProperties warehouseProperties;

    public void checkAndBalanceWorkforce() {
        WarehouseProperties.Relocation config = warehouseProperties.getRelocation();
        LocalDateTime now = LocalDateTime.now(clock);

        List<Department> departments = departmentRepository.findAll();
        logDeficits(departments);

        if (isBalanced(departments, config)) {
            log.debug("All departments at or above capacity, no relocation needed");
            return;
        }

        int scheduled = 0;
        while (scheduled < config.getMaxMovesPerTick()) {
            List<Department> currentDepartments = departmentRepository.findAll();
            if (isBalanced(currentDepartments, config)) {
                break;
            }

            Optional<RelocationCandidate> candidate = findBestRelocation(currentDepartments, now, config);
            if (candidate.isEmpty()) {
                logRelocationBlockers(currentDepartments, now, config);
                break;
            }

            scheduleRelocation(candidate.get());
            scheduled++;
        }

        if (scheduled > 0) {
            log.info("Scheduled {} relocation(s) to execute after order completion", scheduled);
        }
    }

    private boolean isBalanced(List<Department> departments, WarehouseProperties.Relocation config) {
        double tolerance = config.getBalanceToleranceIph();
        return departments.stream().noneMatch(department -> calculateDeficit(department) > tolerance);
    }

    private Optional<RelocationCandidate> findBestRelocation(
            List<Department> departments,
            LocalDateTime now,
            WarehouseProperties.Relocation config
    ) {
        double tolerance = config.getBalanceToleranceIph();

        List<Department> recipients = departments.stream()
                .filter(department -> calculateDeficit(department) > tolerance)
                .sorted(Comparator.comparingDouble(this::calculateDeficit).reversed())
                .toList();

        List<Department> givers = departments.stream()
                .filter(department -> calculateDeficit(department) < -tolerance)
                .sorted(Comparator.comparingDouble(this::calculateDeficit))
                .toList();

        for (Department recipient : recipients) {
            double recipientDeficit = calculateDeficit(recipient);
            double benchmarkIph = getTopPerformerBenchmark(recipient);
            double iphToFlipToSurplus = recipientDeficit + tolerance;
            int peopleNeeded = calculatePeopleNeeded(iphToFlipToSurplus, benchmarkIph);

            for (Department giver : givers) {
                if (giver.getId().equals(recipient.getId())) {
                    continue;
                }

                List<Employee> weakestMovable = findWeakestMovableEmployees(
                        giver, peopleNeeded, now, config);

                if (weakestMovable.isEmpty()) {
                    continue;
                }

                Employee employee = weakestMovable.getFirst();
                double lossAtGiver = employee.getCurrentIph();
                double gainAtRecipient = employeePerformanceService.predictIphAtDepartment(
                        employee, recipient.getName(), now);

                if (gainAtRecipient <= 0) {
                    continue;
                }

                return Optional.of(new RelocationCandidate(
                        employee,
                        giver,
                        recipient,
                        lossAtGiver,
                        gainAtRecipient,
                        peopleNeeded,
                        benchmarkIph,
                        recipientDeficit));
            }
        }

        return Optional.empty();
    }

    private double getTopPerformerBenchmark(Department recipient) {
        int sampleSize = warehouseProperties.getRelocation().getTopPerformerSampleSize();
        List<Employee> topPerformers = employeeRepository.findByCurrentDepartment(recipient).stream()
                .filter(employee -> !employee.isOnBreak())
                .filter(employee -> employee.getCurrentIph() > 0)
                .sorted(Comparator.comparingDouble(Employee::getCurrentIph).reversed())
                .limit(sampleSize)
                .toList();

        if (topPerformers.isEmpty()) {
            return employeePerformanceService.getDefaultIph(recipient.getName());
        }

        return topPerformers.stream()
                .mapToDouble(Employee::getCurrentIph)
                .average()
                .orElse(employeePerformanceService.getDefaultIph(recipient.getName()));
    }

    private int calculatePeopleNeeded(double iphGap, double benchmarkIph) {
        if (benchmarkIph <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(iphGap / benchmarkIph));
    }

    /**
     * Returns eligible employees who are among the N weakest on their current (surplus) department.
     */
    private List<Employee> findWeakestMovableEmployees(
            Department giver,
            int peopleNeeded,
            LocalDateTime now,
            WarehouseProperties.Relocation config
    ) {
        List<Employee> rankedByIph = employeeRepository.findByCurrentDepartment(giver).stream()
                .filter(employee -> employee.getCurrentIph() > 0)
                .sorted(Comparator.comparingDouble(Employee::getCurrentIph))
                .toList();

        List<Long> weakestIds = rankedByIph.stream()
                .limit(peopleNeeded)
                .map(Employee::getId)
                .toList();

        return rankedByIph.stream()
                .filter(employee -> weakestIds.contains(employee.getId()))
                .filter(employee -> employee.isEligibleForRelocation(
                        now, config.getCooldownMinutes(), config.getMaxRelocationsPerDay()))
                .toList();
    }

    private void logRelocationBlockers(
            List<Department> departments,
            LocalDateTime now,
            WarehouseProperties.Relocation config
    ) {
        double tolerance = config.getBalanceToleranceIph();

        long surplusDepartments = departments.stream()
                .filter(department -> calculateDeficit(department) < -tolerance)
                .count();
        long deficitDepartments = departments.stream()
                .filter(department -> calculateDeficit(department) > tolerance)
                .count();

        long eligibleWeakestInSurplus = departments.stream()
                .filter(department -> calculateDeficit(department) < -tolerance)
                .mapToLong(department -> {
                    Department worstRecipient = departments.stream()
                            .filter(d -> calculateDeficit(d) > tolerance)
                            .max(Comparator.comparingDouble(this::calculateDeficit))
                            .orElse(null);
                    if (worstRecipient == null) {
                        return 0L;
                    }
                    int peopleNeeded = calculatePeopleNeeded(
                            calculateDeficit(worstRecipient) + tolerance,
                            getTopPerformerBenchmark(worstRecipient));
                    return findWeakestMovableEmployees(department, peopleNeeded, now, config).size();
                })
                .sum();

        long pendingRelocations = employeeRepository.findAll().stream()
                .filter(Employee::hasPendingRelocation)
                .count();

        log.warn(
                "No relocation scheduled. Surplus depts: {}, deficit depts: {}, "
                        + "eligible weakest in surplus: {}, pending relocations: {}",
                surplusDepartments,
                deficitDepartments,
                eligibleWeakestInSurplus,
                pendingRelocations
        );
    }

    private void scheduleRelocation(RelocationCandidate candidate) {
        Employee employee = candidate.employee();
        Department recipient = candidate.recipient();

        employee.setTargetDepartment(recipient);
        employeeRepository.save(employee);

        log.info(
                "Scheduled weakest eligible employee {} from {} (deficit {}) to {} (deficit {}). "
                        + "Need ~{} people at ~{} IPH benchmark, moving {} IPH -> {} IPH ({} items left)",
                employee.getId(),
                candidate.giver().getName(),
                String.format("%.1f", calculateDeficit(candidate.giver())),
                recipient.getName(),
                String.format("%.1f", candidate.recipientDeficit()),
                candidate.peopleNeeded(),
                String.format("%.1f", candidate.benchmarkIph()),
                String.format("%.1f", candidate.lossAtGiver()),
                String.format("%.1f", candidate.gainAtRecipient()),
                employee.getOrderItemsRemaining());
    }

    private void logDeficits(List<Department> departments) {
        double imbalance = calculateImbalance(departments);
        departments.stream()
                .sorted(Comparator.comparingDouble(this::calculateDeficit).reversed())
                .forEach(department -> log.info("Department: {}, Deficit: {}",
                        department.getName(), calculateDeficit(department)));
        log.info("Deficit imbalance (max-min): {}", String.format("%.1f", imbalance));
    }

    private double calculateImbalance(List<Department> departments) {
        double min = departments.stream().mapToDouble(this::calculateDeficit).min().orElse(0);
        double max = departments.stream().mapToDouble(this::calculateDeficit).max().orElse(0);
        return max - min;
    }

    private double calculateDeficit(Department dept) {
        return dept.getRequiredIPH() - (dept.getCurrentIPH() * dept.getPerformanceMultiplier());
    }

    private record RelocationCandidate(
            Employee employee,
            Department giver,
            Department recipient,
            double lossAtGiver,
            double gainAtRecipient,
            int peopleNeeded,
            double benchmarkIph,
            double recipientDeficit
    ) {
    }
}
