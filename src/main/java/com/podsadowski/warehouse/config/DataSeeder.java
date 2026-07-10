package com.podsadowski.warehouse.config;

import com.podsadowski.warehouse.domain.entity.BreakTier;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.DepartmentRepository;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.performance.EmployeePerformanceService;
import com.podsadowski.warehouse.relocation.RelocationFulfillmentService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Component
@AllArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeePerformanceService employeePerformanceService;
    private final RelocationFulfillmentService relocationFulfillmentService;
    private final WarehouseProperties warehouseProperties;

    @Override
    public void run(String... args) {
        if (departmentRepository.count() == 0) {
            log.info("Database is empty");

            WarehouseProperties.Seeding seeding = warehouseProperties.getSeeding();
            Random random = new Random();

            for (DepartmentName name : DepartmentName.values()) {
                Department dept = new Department();
                dept.setName(name);
                dept.setTargetDailyItems(seeding.getTargetDailyItems());
                dept.setCurrentIPH(0.0);
                dept.setRequiredIPH(0.0);
                dept.setPerformanceMultiplier(seeding.getPerformanceMultiplier());
                departmentRepository.save(dept);
            }

            var departments = departmentRepository.findAll();
            Map<Long, Double> departmentIphTotals = new HashMap<>();

            for (int i = 1; i <= seeding.getEmployeeCount(); i++) {
                Employee emp = new Employee();
                emp.setFirstName("Pracownik");
                emp.setLastName(String.valueOf(i));
                emp.setOnBreak(false);
                emp.setBreakTier(i % 2 == 0 ? BreakTier.FIRST_SHIFT : BreakTier.SECOND_SHIFT);

                Department randomDept = departments.get(random.nextInt(departments.size()));
                emp.setCurrentDepartment(randomDept);

                double employeeIph = employeePerformanceService.getDefaultIph(randomDept.getName());
                emp.setCurrentIph(employeeIph);
                emp.setOrderItemsRemaining(relocationFulfillmentService.randomOrderSize());

                employeeRepository.save(emp);
                departmentIphTotals.merge(randomDept.getId(), employeeIph, Double::sum);
            }

            double minMultiplier = seeding.getRequiredIphMinMultiplier();
            double multiplierRange = seeding.getRequiredIphMaxMultiplier() - minMultiplier;

            for (Department department : departments) {
                double capacity = departmentIphTotals.getOrDefault(department.getId(), 0.0);
                department.setCurrentIPH(capacity);
                department.setRequiredIPH(capacity * (minMultiplier + random.nextDouble() * multiplierRange));
                departmentRepository.save(department);
                log.info("Seeded {} capacity={} requiredIPH={}", department.getName(), capacity, department.getRequiredIPH());
            }

            log.info("Successfully added {} employees", seeding.getEmployeeCount());
        }
    }
}
