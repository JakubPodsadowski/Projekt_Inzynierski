package com.podsadowski.warehouse.config;

import com.podsadowski.warehouse.api.dto.SystemMode;
import com.podsadowski.warehouse.domain.entity.BreakTier;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.entity.Product;
import com.podsadowski.warehouse.domain.entity.StockLevel;
import com.podsadowski.warehouse.domain.entity.StorageLocation;
import com.podsadowski.warehouse.domain.repository.DepartmentRepository;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.domain.repository.ProductRepository;
import com.podsadowski.warehouse.domain.repository.StockLevelRepository;
import com.podsadowski.warehouse.domain.repository.StorageLocationRepository;
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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Schema on both DBs; DEMO gets workforce + product catalog seed. REAL stays empty of ops data.
 */
@Component
@Order(1)
@Slf4j
public class DualDatabaseBootstrap implements ApplicationRunner {

    private static final String PREP = "PREP_UNPACKING";

    private final DataSourceModeHolder modeHolder;
    private final EntityManagerFactory entityManagerFactory;
    private final PlatformTransactionManager transactionManager;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final ProductRepository productRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final StockLevelRepository stockLevelRepository;
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
            ProductRepository productRepository,
            StorageLocationRepository storageLocationRepository,
            StockLevelRepository stockLevelRepository,
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
        this.productRepository = productRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.stockLevelRepository = stockLevelRepository;
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

        prepareDatabase(SystemMode.REAL, realDataSource, tx, false);
        prepareDatabase(SystemMode.DEMO, demoDataSource, tx, true);

        modeHolder.set(startupMode);
        log.info("Active datasource mode: {}", startupMode);
    }

    private void prepareDatabase(
            SystemMode mode,
            DataSource dataSource,
            TransactionTemplate tx,
            boolean seedDemoData
    ) {
        modeHolder.set(mode);
        ensureSchema(dataSource);
        removePrepUnpackingDepartment(dataSource);
        if (seedDemoData) {
            tx.executeWithoutResult(status -> {
                seedWorkforceIfEmpty();
                seedCatalogIfEmpty();
            });
        }
    }

    private void ensureSchema(DataSource dataSource) {
        try {
            boolean hasDepartments = hasTable(dataSource, "departments");
            boolean hasProducts = hasTable(dataSource, "products");
            if (!hasDepartments) {
                log.info("Creating full schema on {}", modeHolder.get());
                entityManagerFactory.unwrap(SessionFactory.class)
                        .getSchemaManager()
                        .exportMappedObjects(true);
                return;
            }
            if (!hasProducts) {
                log.info("Creating catalog tables on {}", modeHolder.get());
                createCatalogTables(dataSource);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare schema for " + modeHolder.get(), e);
        }
    }

    private void createCatalogTables(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS products (
                        id BIGSERIAL PRIMARY KEY,
                        sku VARCHAR(64) NOT NULL UNIQUE,
                        name VARCHAR(255) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS storage_locations (
                        id BIGSERIAL PRIMARY KEY,
                        code VARCHAR(64) NOT NULL UNIQUE,
                        aisle VARCHAR(16),
                        rack VARCHAR(16),
                        bin VARCHAR(16)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS stock_levels (
                        id BIGSERIAL PRIMARY KEY,
                        product_id BIGINT NOT NULL REFERENCES products(id),
                        location_id BIGINT NOT NULL REFERENCES storage_locations(id),
                        quantity INT NOT NULL,
                        CONSTRAINT uk_stock_product_location UNIQUE (product_id, location_id)
                    )
                    """);
        }
    }

    private void removePrepUnpackingDepartment(DataSource dataSource) {
        try {
            if (!hasTable(dataSource, "departments")) {
                return;
            }
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        UPDATE employees
                        SET department_id = (SELECT id FROM departments WHERE name = 'PUTAWAY')
                        WHERE department_id = (SELECT id FROM departments WHERE name = '%s')
                        """.formatted(PREP));
                statement.execute("""
                        UPDATE employees
                        SET target_department_id = (SELECT id FROM departments WHERE name = 'PUTAWAY')
                        WHERE target_department_id = (SELECT id FROM departments WHERE name = '%s')
                        """.formatted(PREP));
                if (hasTable(dataSource, "relocation_logs")) {
                    statement.execute("""
                            UPDATE relocation_logs SET from_department = 'PUTAWAY'
                            WHERE from_department = '%s'
                            """.formatted(PREP));
                    statement.execute("""
                            UPDATE relocation_logs SET to_department = 'PUTAWAY'
                            WHERE to_department = '%s'
                            """.formatted(PREP));
                }
                if (hasTable(dataSource, "work_logs")) {
                    statement.execute("""
                            UPDATE work_logs SET department_name = 'PUTAWAY'
                            WHERE department_name = '%s'
                            """.formatted(PREP));
                }
                if (hasTable(dataSource, "employee_notifications")) {
                    statement.execute("""
                            UPDATE employee_notifications SET from_department = 'PUTAWAY'
                            WHERE from_department = '%s'
                            """.formatted(PREP));
                    statement.execute("""
                            UPDATE employee_notifications SET to_department = 'PUTAWAY'
                            WHERE to_department = '%s'
                            """.formatted(PREP));
                }
                int deleted = statement.executeUpdate(
                        "DELETE FROM departments WHERE name = '%s'".formatted(PREP));
                if (deleted > 0) {
                    log.info("Removed {} department from {}", PREP, modeHolder.get());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to remove " + PREP + " from " + modeHolder.get(), e);
        }
    }

    private boolean hasTable(DataSource dataSource, String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData()
                     .getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (tables.next()) {
                return true;
            }
        }
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData()
                     .getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private void seedWorkforceIfEmpty() {
        if (departmentRepository.count() > 0) {
            log.info("{} workforce already seeded", modeHolder.get());
            return;
        }

        log.info("Seeding workforce into {}", modeHolder.get());
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

        log.info("Seeded {} employees into {}", seeding.getEmployeeCount(), modeHolder.get());
    }

    private void seedCatalogIfEmpty() {
        if (productRepository.count() > 0) {
            log.info("{} catalog already seeded", modeHolder.get());
            return;
        }

        log.info("Seeding product catalog into {}", modeHolder.get());
        List<ProductSeed> seeds = List.of(
                new ProductSeed("SKU-1001", "Kabel USB-C 1m"),
                new ProductSeed("SKU-1002", "Kabel HDMI 2m"),
                new ProductSeed("SKU-1003", "Mysz bezprzewodowa"),
                new ProductSeed("SKU-1004", "Klawiatura membranowa"),
                new ProductSeed("SKU-1005", "Podkladka pod mysz"),
                new ProductSeed("SKU-1006", "Hub USB 4-port"),
                new ProductSeed("SKU-1007", "Zasilacz 65W"),
                new ProductSeed("SKU-1008", "Sluchawki nauszne"),
                new ProductSeed("SKU-1009", "Etui na laptopa 15\""),
                new ProductSeed("SKU-1010", "Pendrive 64GB"),
                new ProductSeed("SKU-1011", "Karta SD 128GB"),
                new ProductSeed("SKU-1012", "Adapter USB-C/HDMI")
        );

        List<Product> products = new ArrayList<>();
        for (ProductSeed seed : seeds) {
            Product product = new Product();
            product.setSku(seed.sku());
            product.setName(seed.name());
            products.add(productRepository.save(product));
        }

        List<StorageLocation> locations = new ArrayList<>();
        String[] aisles = {"A", "B", "C"};
        for (String aisle : aisles) {
            for (int rack = 1; rack <= 4; rack++) {
                for (int bin = 1; bin <= 3; bin++) {
                    StorageLocation location = new StorageLocation();
                    location.setAisle(aisle);
                    location.setRack(String.format("%02d", rack));
                    location.setBin(String.format("%02d", bin));
                    location.setCode(aisle + "-" + location.getRack() + "-" + location.getBin());
                    locations.add(storageLocationRepository.save(location));
                }
            }
        }

        Random random = new Random(42);
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            StorageLocation primary = locations.get(i % locations.size());
            StockLevel primaryStock = new StockLevel();
            primaryStock.setProduct(product);
            primaryStock.setLocation(primary);
            primaryStock.setQuantity(80 + random.nextInt(120));
            stockLevelRepository.save(primaryStock);

            if (i % 3 == 0) {
                StorageLocation secondary = locations.get((i + 7) % locations.size());
                if (!secondary.getId().equals(primary.getId())) {
                    StockLevel secondaryStock = new StockLevel();
                    secondaryStock.setProduct(product);
                    secondaryStock.setLocation(secondary);
                    secondaryStock.setQuantity(20 + random.nextInt(40));
                    stockLevelRepository.save(secondaryStock);
                }
            }
        }

        log.info(
                "Seeded {} products, {} locations, {} stock rows into {}",
                products.size(),
                locations.size(),
                stockLevelRepository.count(),
                modeHolder.get()
        );
    }

    private record ProductSeed(String sku, String name) {
    }
}
