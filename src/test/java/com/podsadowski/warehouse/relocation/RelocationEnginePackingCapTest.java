package com.podsadowski.warehouse.relocation;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.DepartmentRepository;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.domain.repository.RelocationLogRepository;
import com.podsadowski.warehouse.notification.NotificationService;
import com.podsadowski.warehouse.performance.EmployeePerformanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelocationEnginePackingCapTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private RelocationLogRepository relocationLogRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmployeePerformanceService employeePerformanceService;

    private RelocationEngine relocationEngine;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneId.of("UTC"));

    private Department picking;
    private Department packing;
    private Department putaway;

    @BeforeEach
    void setUp() {
        WarehouseProperties props = new WarehouseProperties();
        props.getRelocation().setBalanceToleranceIph(10.0);
        props.getRelocation().setMaxMovesPerTick(2);
        props.getRelocation().setMaxMovesIntoDepartmentPerTick(1);
        props.getRelocation().setCooldownMinutes(60);
        props.getRelocation().setMaxRelocationsPerDay(3);
        props.getPacking().setMaxStations(2);

        relocationEngine = new RelocationEngine(
                employeeRepository,
                departmentRepository,
                relocationLogRepository,
                notificationService,
                clock,
                employeePerformanceService,
                props
        );

        picking = department(1L, DepartmentName.PICKING, 200.0, 40.0);
        packing = department(2L, DepartmentName.PACKING, 110.0, 440.0);
        putaway = department(3L, DepartmentName.PUTAWAY, 55.0, 40.0);
    }

    @Test
    void checkAndBalanceWorkforce_doesNotMoveIntoPackingWhenStationsFull() {
        List<Employee> packers = List.of(employee(20L, packing, 55.0), employee(21L, packing, 55.0));
        Employee picker = employee(10L, picking, 85.0);

        when(departmentRepository.findAll()).thenReturn(List.of(picking, packing, putaway));
        when(employeeRepository.findByCurrentDepartment(eq(packing))).thenReturn(packers);
        when(employeeRepository.findAll()).thenReturn(new ArrayList<>(packers));

        int scheduled = relocationEngine.checkAndBalanceWorkforce();

        assertThat(scheduled).isEqualTo(0);
        assertThat(picker.getTargetDepartment()).isNull();
    }

    @Test
    void checkAndBalanceWorkforce_fallbackPullsFromLeastDeficitWhenNoSurplus() {
        // No classic surplus (all deficits >= -tolerance), but picking is least-deficit vs putaway.
        picking.setCurrentIPH(50.0);
        picking.setRequiredIPH(55.0); // deficit 5
        packing.setCurrentIPH(55.0);
        packing.setRequiredIPH(55.0); // deficit 0
        putaway.setCurrentIPH(0.0);
        putaway.setRequiredIPH(110.0); // deficit 110

        Employee picker = employee(10L, picking, 50.0);

        when(departmentRepository.findAll()).thenReturn(List.of(picking, packing, putaway));
        when(employeeRepository.findByCurrentDepartment(eq(picking))).thenReturn(List.of(picker));
        when(employeeRepository.findByCurrentDepartment(eq(putaway))).thenReturn(List.of());
        when(employeeRepository.findByCurrentDepartment(eq(packing))).thenReturn(List.of());
        when(employeeRepository.findAll()).thenReturn(List.of(picker));
        when(employeePerformanceService.getDefaultIph(DepartmentName.PUTAWAY)).thenReturn(55.0);
        when(employeePerformanceService.predictIphAtDepartment(eq(picker), eq(DepartmentName.PUTAWAY), any()))
                .thenReturn(55.0);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        when(relocationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int scheduled = relocationEngine.checkAndBalanceWorkforce();

        assertThat(scheduled).isEqualTo(1);
        assertThat(picker.getTargetDepartment()).isEqualTo(putaway);
    }

    private static Department department(Long id, DepartmentName name, double currentIph, double requiredIph) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        department.setCurrentIPH(currentIph);
        department.setRequiredIPH(requiredIph);
        department.setPerformanceMultiplier(1.0);
        return department;
    }

    private static Employee employee(Long id, Department dept, double iph) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setCurrentDepartment(dept);
        employee.setCurrentIph(iph);
        employee.setOnBreak(false);
        employee.setRelocationCountToday(0);
        employee.setOrderItemsRemaining(0);
        return employee;
    }
}
