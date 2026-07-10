package com.podsadowski.warehouse.performance;

import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.DepartmentRepository;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregates active employee IPH into each department's {@code currentIPH}.
 * Excludes employees on break or in transit.
 */
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
