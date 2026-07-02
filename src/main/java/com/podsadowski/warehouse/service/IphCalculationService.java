package com.podsadowski.warehouse.service;

import com.podsadowski.warehouse.entity.Department;
import com.podsadowski.warehouse.entity.Employee;
import com.podsadowski.warehouse.repository.DepartmentRepository;
import com.podsadowski.warehouse.repository.EmployeeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IphCalculationService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final Clock clock;

    @Transactional
    public void recalculateDepartmentIph() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Employee> employees = employeeRepository.findAll();
        List<Department> departments = departmentRepository.findAll();

        Map<Long, Double> iphByDepartmentId = employees.stream()
                .filter(employee -> employee.getCurrentDepartment() != null)
                .filter(employee -> !employee.isOnBreak())
                .filter(employee -> !employee.isInTransit(now))
                .collect(Collectors.groupingBy(
                        employee -> employee.getCurrentDepartment().getId(),
                        Collectors.summingDouble(Employee::getCurrentIph)
                ));

        for (Department department : departments) {
            double totalIph = iphByDepartmentId.getOrDefault(department.getId(), 0.0);
            department.setCurrentIPH(totalIph);
        }

        departmentRepository.saveAll(departments);
    }
}
