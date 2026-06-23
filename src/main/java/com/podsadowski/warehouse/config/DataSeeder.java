package com.podsadowski.warehouse.config;

import com.podsadowski.warehouse.entity.BreakTier;
import com.podsadowski.warehouse.entity.Department;
import com.podsadowski.warehouse.entity.DepartmentName;
import com.podsadowski.warehouse.entity.Employee;
import com.podsadowski.warehouse.repository.DepartmentRepository;
import com.podsadowski.warehouse.repository.EmployeeRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

@Component
@AllArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    @Override
    public void run(String... args) {
        if (departmentRepository.count() == 0) {
            log.info("Database is empty");

            Random random = new Random();

            for (DepartmentName name : DepartmentName.values()) {
                Department dept = new Department();
                dept.setName(name);
                dept.setTargetDailyItems(1000);

                dept.setCurrentIPH(20.0 + (50.0 * random.nextDouble()));

                dept.setRequiredIPH(40.0 + (20.0 * random.nextDouble()));

                dept.setPerformanceMultiplier(1.0);
                departmentRepository.save(dept);
            }

            var departments = departmentRepository.findAll();

            for (int i = 1; i <= 40; i++) {
                Employee emp = new Employee();
                emp.setFirstName("Pracownik");
                emp.setLastName(String.valueOf(i));
                emp.setOnBreak(false);

                emp.setBreakTier(i % 2 == 0 ? BreakTier.FIRST_SHIFT : BreakTier.SECOND_SHIFT);

                Map<DepartmentName, Integer> skills = new EnumMap<>(DepartmentName.class);
                for (DepartmentName name : DepartmentName.values()) {
                    skills.put(name, random.nextInt(3) + 1);
                }
                emp.setSkills(skills);

                Department randomDept = departments.get(random.nextInt(departments.size()));
                emp.setCurrentDepartment(randomDept);

                employeeRepository.save(emp);
            }
            log.info("Successfully added 40 employees");
        }
    }
}
