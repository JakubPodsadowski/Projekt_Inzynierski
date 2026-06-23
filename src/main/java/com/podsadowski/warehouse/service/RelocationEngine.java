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

@Service
@AllArgsConstructor
@Slf4j
public class RelocationEngine {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final Clock clock;
    private final EfficiencyService efficiencyService;

    public void checkAndBalanceWorkforce() {
        log.info("Checking for balance workforce at: {}", LocalDateTime.now(clock));

        List<Department> departments = departmentRepository.findAll();
        List<Department> sortedDepartments = departments.stream()
                .sorted(Comparator.comparingDouble(this::calculateDeficit).reversed())
                .toList();

        if (sortedDepartments.size() <= 1) {
            return;
        }

        sortedDepartments.forEach(department ->
                log.info("Department: {}, Deficit: {}", department.getName(), calculateDeficit(department))
        );

        Department recipient = sortedDepartments.getFirst();
        Department giver = sortedDepartments.getLast();

        double recipientDeficit = calculateDeficit(recipient);
        double giverDeficit = calculateDeficit(giver);

        if (recipientDeficit > 0 && recipientDeficit > giverDeficit) {
            log.info("Department {} gets workforce from department {}", recipient.getName(), giver.getName());
            relocateAvailableEmployee(giver, recipient);
        }
    }

    private void relocateAvailableEmployee(Department giver, Department recipient) {
        employeeRepository.findByCurrentDepartment(giver).stream()
                .filter(employee -> employee.isEligibleForRelocation(LocalDateTime.now(clock)))
                .filter(employee -> !employee.isOnBreak())
                .findFirst()
                .ifPresent(employee -> executeTransfer(employee, giver, recipient));
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
}
