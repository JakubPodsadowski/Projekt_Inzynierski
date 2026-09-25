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
import com.podsadowski.warehouse.inbound.InboundPutawayService;
import com.podsadowski.warehouse.inbound.InboundReceiptGenerator;
import com.podsadowski.warehouse.order.OutboundOrderGenerator;
import com.podsadowski.warehouse.order.OutboundOrderService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
    private final OutboundOrderGenerator outboundOrderGenerator;
    private final OutboundOrderService outboundOrderService;
    private final InboundReceiptGenerator inboundReceiptGenerator;
    private final InboundPutawayService inboundPutawayService;
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
            OutboundOrderGenerator outboundOrderGenerator,
            OutboundOrderService outboundOrderService,
            InboundReceiptGenerator inboundReceiptGenerator,
            InboundPutawayService inboundPutawayService,
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
        this.outboundOrderGenerator = outboundOrderGenerator;
        this.outboundOrderService = outboundOrderService;
        this.inboundReceiptGenerator = inboundReceiptGenerator;
        this.inboundPutawayService = inboundPutawayService;
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
                clampStockToBinCapacity();
                seedOutboundOrdersIfEmpty();
                seedInboundReceiptsIfEmpty();
            });
        }
    }

    private void ensureSchema(DataSource dataSource) {
        try {
            boolean hasDepartments = hasTable(dataSource, "departments");
            boolean hasProducts = hasTable(dataSource, "products");
            boolean hasOutbound = hasTable(dataSource, "outbound_orders");
            boolean hasInbound = hasTable(dataSource, "inbound_receipts");
            boolean hasActivities = hasTable(dataSource, "work_activities");
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
            } else {
                ensureProductUnitsPerBinColumn(dataSource);
            }
            if (!hasOutbound) {
                log.info("Creating outbound order tables on {}", modeHolder.get());
                createOutboundOrderTables(dataSource);
            }
            if (!hasInbound) {
                log.info("Creating inbound receipt tables on {}", modeHolder.get());
                createInboundReceiptTables(dataSource);
            }
            if (!hasActivities) {
                log.info("Creating work activity table on {}", modeHolder.get());
                createWorkActivityTable(dataSource);
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
                        name VARCHAR(255) NOT NULL,
                        units_per_bin INT NOT NULL DEFAULT 6
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

    private void createOutboundOrderTables(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS outbound_orders (
                        id BIGSERIAL PRIMARY KEY,
                        order_number VARCHAR(64) NOT NULL UNIQUE,
                        status VARCHAR(32) NOT NULL,
                        priority_score INT NOT NULL,
                        due_at TIMESTAMP,
                        vip BOOLEAN NOT NULL DEFAULT FALSE,
                        external_ref VARCHAR(128),
                        assigned_employee_id BIGINT,
                        created_at TIMESTAMP NOT NULL,
                        picking_started_at TIMESTAMP,
                        picking_completed_at TIMESTAMP,
                        packing_started_at TIMESTAMP,
                        packing_completed_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS outbound_order_lines (
                        id BIGSERIAL PRIMARY KEY,
                        order_id BIGINT NOT NULL REFERENCES outbound_orders(id) ON DELETE CASCADE,
                        product_id BIGINT NOT NULL REFERENCES products(id),
                        quantity_ordered INT NOT NULL,
                        quantity_picked INT NOT NULL DEFAULT 0,
                        quantity_packed INT NOT NULL DEFAULT 0
                    )
                    """);
        }
    }

    private void createInboundReceiptTables(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS inbound_receipts (
                        id BIGSERIAL PRIMARY KEY,
                        receipt_number VARCHAR(64) NOT NULL UNIQUE,
                        status VARCHAR(32) NOT NULL,
                        external_ref VARCHAR(128),
                        assigned_employee_id BIGINT,
                        created_at TIMESTAMP NOT NULL,
                        started_at TIMESTAMP,
                        completed_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS inbound_receipt_lines (
                        id BIGSERIAL PRIMARY KEY,
                        receipt_id BIGINT NOT NULL REFERENCES inbound_receipts(id) ON DELETE CASCADE,
                        product_id BIGINT NOT NULL REFERENCES products(id),
                        preferred_location_id BIGINT REFERENCES storage_locations(id),
                        quantity_expected INT NOT NULL,
                        quantity_put_away INT NOT NULL DEFAULT 0
                    )
                    """);
        }
    }

    private void createWorkActivityTable(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS work_activities (
                        id BIGSERIAL PRIMARY KEY,
                        employee_id BIGINT NOT NULL,
                        type VARCHAR(32) NOT NULL,
                        department_name VARCHAR(32) NOT NULL,
                        occurred_at TIMESTAMP NOT NULL,
                        quantity INT NOT NULL DEFAULT 0,
                        product_id BIGINT,
                        product_sku VARCHAR(64),
                        location_id BIGINT,
                        location_code VARCHAR(64),
                        outbound_order_id BIGINT,
                        order_number VARCHAR(64),
                        inbound_receipt_id BIGINT,
                        receipt_number VARCHAR(64),
                        message VARCHAR(512)
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_work_activities_employee_ts
                    ON work_activities (employee_id, occurred_at)
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

    private void ensureProductUnitsPerBinColumn(DataSource dataSource) throws Exception {
        if (hasColumn(dataSource, "products", "units_per_bin")) {
            return;
        }
        log.info("Adding products.units_per_bin column on {}", modeHolder.get());
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE products
                    ADD COLUMN IF NOT EXISTS units_per_bin INT NOT NULL DEFAULT 6
                    """);
        }
    }

    private boolean hasColumn(DataSource dataSource, String tableName, String columnName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (columns.next()) {
                return true;
            }
        }
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData()
                     .getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
            return columns.next();
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
        if (departments.isEmpty()) {
            throw new IllegalStateException("No departments available for workforce seeding");
        }
        if (seeding.getEmployeeCount() < departments.size()) {
            throw new IllegalStateException(
                    "employeeCount must be at least the number of departments (" + departments.size() + ")");
        }

        Map<Long, Double> departmentIphTotals = new HashMap<>();
        // Round-robin guarantees at least one employee per department.
        for (int i = 1; i <= seeding.getEmployeeCount(); i++) {
            Employee emp = new Employee();
            emp.setFirstName("Pracownik");
            emp.setLastName(String.valueOf(i));
            emp.setOnBreak(false);
            emp.setBreakTier(i % 2 == 0 ? BreakTier.FIRST_SHIFT : BreakTier.SECOND_SHIFT);

            Department assignedDept = departments.get((i - 1) % departments.size());
            emp.setCurrentDepartment(assignedDept);

            double employeeIph = employeePerformanceService.getDefaultIph(assignedDept.getName());
            emp.setCurrentIph(employeeIph);
            emp.setOrderItemsRemaining(relocationFulfillmentService.randomOrderSize());

            employeeRepository.save(emp);
            departmentIphTotals.merge(assignedDept.getId(), employeeIph, Double::sum);
        }

        double minMultiplier = seeding.getRequiredIphMinMultiplier();
        double multiplierRange = seeding.getRequiredIphMaxMultiplier() - minMultiplier;

        for (Department department : departments) {
            double capacity = departmentIphTotals.getOrDefault(department.getId(), 0.0);
            department.setCurrentIPH(capacity);
            department.setRequiredIPH(capacity * (minMultiplier + random.nextDouble() * multiplierRange));
            departmentRepository.save(department);
        }

        log.info(
                "Seeded {} employees across {} departments into {}",
                seeding.getEmployeeCount(),
                departments.size(),
                modeHolder.get()
        );
    }

    private void seedCatalogIfEmpty() {
        if (productRepository.count() > 0) {
            log.info("{} catalog already seeded", modeHolder.get());
            return;
        }

        log.info("Seeding product catalog into {}", modeHolder.get());
        List<ProductSeed> seeds = List.of(
                new ProductSeed("SKU-1001", "Kabel USB-C 1m", 12),
                new ProductSeed("SKU-1002", "Kabel HDMI 2m", 12),
                new ProductSeed("SKU-1003", "Mysz bezprzewodowa", 6),
                new ProductSeed("SKU-1004", "Klawiatura membranowa", 2),
                new ProductSeed("SKU-1005", "Podkladka pod mysz", 12),
                new ProductSeed("SKU-1006", "Hub USB 4-port", 6),
                new ProductSeed("SKU-1007", "Zasilacz 65W", 1),
                new ProductSeed("SKU-1008", "Sluchawki nauszne", 2),
                new ProductSeed("SKU-1009", "Etui na laptopa 15\"", 1),
                new ProductSeed("SKU-1010", "Pendrive 64GB", 24),
                new ProductSeed("SKU-1011", "Karta SD 128GB", 24),
                new ProductSeed("SKU-1012", "Adapter USB-C/HDMI", 6)
        );

        List<Product> products = new ArrayList<>();
        for (ProductSeed seed : seeds) {
            Product product = new Product();
            product.setSku(seed.sku());
            product.setName(seed.name());
            product.setUnitsPerBin(seed.unitsPerBin());
            products.add(productRepository.save(product));
        }

        List<StorageLocation> locations = new ArrayList<>();
        String[] aisles = {"A", "B", "C", "D"};
        for (String aisle : aisles) {
            for (int rack = 1; rack <= 6; rack++) {
                for (int bin = 1; bin <= 4; bin++) {
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
        Set<Long> occupiedLocationIds = new HashSet<>();
        int locationCursor = 0;
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            StorageLocation primary = nextFreeLocation(locations, occupiedLocationIds, locationCursor);
            if (primary == null) {
                log.warn("Ran out of free locations while seeding stock for {}", product.getSku());
                break;
            }
            locationCursor = (locations.indexOf(primary) + 1) % locations.size();
            occupiedLocationIds.add(primary.getId());

            StockLevel primaryStock = new StockLevel();
            primaryStock.setProduct(product);
            primaryStock.setLocation(primary);
            primaryStock.setQuantity(1 + random.nextInt(Math.max(1, product.getUnitsPerBin())));
            stockLevelRepository.save(primaryStock);

            if (i % 3 == 0) {
                StorageLocation secondary = nextFreeLocation(locations, occupiedLocationIds, locationCursor);
                if (secondary != null) {
                    locationCursor = (locations.indexOf(secondary) + 1) % locations.size();
                    occupiedLocationIds.add(secondary.getId());
                    StockLevel secondaryStock = new StockLevel();
                    secondaryStock.setProduct(product);
                    secondaryStock.setLocation(secondary);
                    secondaryStock.setQuantity(1 + random.nextInt(Math.max(1, product.getUnitsPerBin())));
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

    private static StorageLocation nextFreeLocation(
            List<StorageLocation> locations,
            Set<Long> occupiedLocationIds,
            int startIndex
    ) {
        if (locations.isEmpty()) {
            return null;
        }
        for (int offset = 0; offset < locations.size(); offset++) {
            StorageLocation candidate = locations.get((startIndex + offset) % locations.size());
            if (!occupiedLocationIds.contains(candidate.getId())) {
                return candidate;
            }
        }
        return null;
    }

    private void clampStockToBinCapacity() {
        int fixed = 0;
        for (StockLevel level : stockLevelRepository.findAll()) {
            Product product = level.getProduct();
            if (product == null) {
                continue;
            }
            int cap = Math.max(1, product.getUnitsPerBin());
            if (level.getQuantity() > cap) {
                level.setQuantity(cap);
                stockLevelRepository.save(level);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("Clamped {} stock rows to units_per_bin on {}", fixed, modeHolder.get());
        }
    }

    private void seedOutboundOrdersIfEmpty() {
        if (productRepository.count() == 0) {
            return;
        }
        if (outboundOrderService.countReadyToPick() > 0
                || outboundOrderService.countReadyToPack() > 0) {
            log.info("{} outbound orders already present", modeHolder.get());
            return;
        }
        int created = outboundOrderGenerator.createRandomOrders(10);
        log.info("Seeded {} outbound orders into {}", created, modeHolder.get());
    }

    private void seedInboundReceiptsIfEmpty() {
        if (productRepository.count() == 0 || storageLocationRepository.count() == 0) {
            return;
        }
        if (inboundPutawayService.countReadyToPutAway() > 0
                || inboundPutawayService.countInProgress() > 0) {
            log.info("{} inbound receipts already present", modeHolder.get());
            return;
        }
        int created = inboundReceiptGenerator.createRandomReceipts(6);
        log.info("Seeded {} inbound receipts into {}", created, modeHolder.get());
    }

    private record ProductSeed(String sku, String name, int unitsPerBin) {
    }
}
