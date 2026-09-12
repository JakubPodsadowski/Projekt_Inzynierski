package com.podsadowski.warehouse.config;

import com.podsadowski.warehouse.api.dto.SystemMode;
import com.podsadowski.warehouse.domain.entity.BreakTier;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.DepartmentRepository;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.performance.EmployeePerformanceService;
import com.podsadowski.warehouse.relocation.RelocationFulfillmentService;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Ensures schema exists on both databases; seeds DEMO only.
 * REAL stays empty so the UI toggle shows a clear difference.
 */
@Component
@Order(1)
@Slf4j
public class DualDatabaseBootstrap implements ApplicationRunner {

    private final DataSourceModeHolder modeHolder;
    private final EntityManagerFactory entityManagerFactory;
    private final PlatformTransactionManager transactionManager;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeePerformanceService employeePerformanceService;
    private final RelocationFulfillmentService relocationFulfillmentService;
    private final WarehouseProperties warehouseProperties;
    private final DataSource realDataSource;
    private final DataSource demoDataSource;

    public DualDatabaseBootstrap(
            DataSourceModeHolder modeHolder,
            EntityManagerFactory entityManagerFactory,
            PlatformTransactionManager transactionManager,
            DepartmentRepository departmentRepository,
            EmployeeRepository employeeRepository,
            EmployeePerformanceService employeePerformanceService,
            RelocationFulfillmentService relocationFulfillmentService,
            WarehouseProperties warehouseProperties,
            @Qualifier("realDataSource") DataSource realDataSource,
            @Qualifier("demoDataSource") DataSource demoDataSource
    ) {
        this.modeHolder = modeHolder;
        this.entityManagerFactory = entityManagerFactory;
        this.transactionManager = transactionManager;
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.employeePerformanceService = employeePerformanceService;
        this.relocationFulfillmentService = relocationFulfillmentService;
        this.warehouseProperties = warehouseProperties;
        this.realDataSource = realDataSource;
        this.demoDataSource = demoDataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        SystemMode startupMode = modeHolder.get();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        modeHolder.set(SystemMode.REAL);
        ensureSchema(realDataSource);

        modeHolder.set(SystemMode.DEMO);
        ensureSchema(demoDataSource);
        tx.executeWithoutResult(status -> seedIfEmpty(SystemMode.DEMO));

        modeHolder.set(startupMode);
        log.info("Active datasource mode: {}", startupMode);
    }

    private void ensureSchema(DataSource dataSource) {
        try {
            if (hasDepartmentsTable(dataSource)) {
                return;
            }
            log.info("Creating schema on {}", modeHolder.get());
            entityManagerFactory.unwrap(SessionFactory.class)
                    .getSchemaManager()
                    .exportMappedObjects(true);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare schema for " + modeHolder.get(), e);
        }
    }

    private boolean hasDepartmentsTable(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData()
                     .getTables(null, null, "departments", new String[]{"TABLE"})) {
            if (tables.next()) {
                return true;
            }
        }
        // PostgreSQL may report uppercase depending on metadata settings
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData()
                     .getTables(null, null, "DEPARTMENTS", new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private void seedIfEmpty(SystemMode mode) {
        if (departmentRepository.count() > 0) {
            log.info("{} database already seeded", mode);
            return;
        }

        log.info("Seeding empty {} database", mode);
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
        }

        log.info("Seeded {} employees into {} database", seeding.getEmployeeCount(), mode);
    }
}
