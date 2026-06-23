package com.podsadowski.warehouse.service;

import com.podsadowski.warehouse.entity.Department;
import com.podsadowski.warehouse.entity.Employee;
import com.podsadowski.warehouse.repository.DepartmentRepository;
import com.podsadowski.warehouse.repository.EmployeeRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class RelocationEngine {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final Clock clock;
    private final EfficiencyService efficiencyService;

    public void checkAndBalanceWorkforce() {

        boolean continuationPossible = true;
        int safetyCounter = 0;
        int maxAllowedMovesPerTick = 50;

        while(continuationPossible && safetyCounter < maxAllowedMovesPerTick) {
            List<Department> departments = departmentRepository.findAll();
            List<Department> sortedDepartments = departments.stream()
                    .sorted(Comparator.comparingDouble(this::calculateDeficit).reversed())
                    .toList();

            sortedDepartments.forEach(department -> log.info("Department: {}, Deficit: {}", department.getName(), calculateDeficit(department)));

            if (sortedDepartments.size() <= 1) {
                break;
            }

            Department recipient = sortedDepartments.getFirst();
            Department giver = sortedDepartments.getLast();

            double recipientDeficit = calculateDeficit(recipient);
            double giverDeficit = calculateDeficit(giver);

            if(recipientDeficit <= 0) {
                log.info("All departments have negative deficit");
                continuationPossible = false;
                continue;
            }

            if(recipientDeficit > giverDeficit) {
                boolean moved = relocateAvailableEmployee(giver, recipient);

                if(!moved) {
                    log.warn("Department {} needs more workforce, but {} can't provide more workers.", recipient.getName(), giver.getName());
                    continuationPossible = false;
                } else {
                    safetyCounter++;
                }
            } else {
                continuationPossible = false;
            }
        }

        log.info("Balancing has stopped in {} moves", safetyCounter);
    }

    private boolean relocateAvailableEmployee(Department giver, Department recipient) {
        Optional<Employee> employeeOptional = employeeRepository.findByCurrentDepartment(giver).stream()
                .filter(employee -> employee.isEligibleForRelocation(LocalDateTime.now(clock)))
                .filter(employee -> !employee.isOnBreak())
                .min((e1, e2) -> Double.compare(
                        calculateNetBenefit(e2, giver, recipient),
                        calculateNetBenefit(e1, giver, recipient)
                ));

        if(employeeOptional.isPresent()) {
            executeTransfer(employeeOptional.get(), giver, recipient);
            return true;
        }

        return false;
    }

    private void executeTransfer(Employee employee, Department giver, Department recipient) {
        int skillAtGiver = employee.getSkillForDepartment(giver.getName());
        int skillAtRecipient = employee.getSkillForDepartment(recipient.getName());

        double contributionFromGiver = efficiencyService.getIphContribution(giver.getName(), skillAtGiver);
        double contributionToRecipient = efficiencyService.getIphContribution(recipient.getName(), skillAtRecipient);

        giver.setCurrentIPH(giver.getCurrentIPH() - contributionFromGiver);
        recipient.setCurrentIPH(recipient.getCurrentIPH() + contributionToRecipient);

        employee.setCurrentDepartment(recipient);
        employee.setLastRelocatedAt(LocalDateTime.now(clock));
        employee.setRelocationCountToday(employee.getRelocationCountToday() + 1);

        employeeRepository.save(employee);
        departmentRepository.save(giver);
        departmentRepository.save(recipient);

        log.info("Relocation: {} from {} ({} IPH) to {} ({} IPH)",
                employee.getId(), giver.getName(), contributionFromGiver,
                recipient.getName(), contributionToRecipient);
    }

    private double calculateDeficit(Department dept) {
        return dept.getRequiredIPH() - (dept.getCurrentIPH() * dept.getPerformanceMultiplier());
    }

    private double calculateNetBenefit(Employee employee, Department giver, Department recipient) {
        int skillAtGiver = employee.getSkillForDepartment(giver.getName());
        int skillAtRecipient = employee.getSkillForDepartment(recipient.getName());

        double loss = efficiencyService.getIphContribution(giver.getName(), skillAtGiver);
        double gain = efficiencyService.getIphContribution(recipient.getName(), skillAtRecipient);

        return gain - loss;
    }
}
