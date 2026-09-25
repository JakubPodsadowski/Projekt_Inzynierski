package com.podsadowski.warehouse.relocation;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.entity.RelocationLog;
import com.podsadowski.warehouse.domain.entity.RelocationLogStatus;
import com.podsadowski.warehouse.domain.repository.DepartmentRepository;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.domain.repository.RelocationLogRepository;
import com.podsadowski.warehouse.notification.NotificationService;
import com.podsadowski.warehouse.performance.EmployeePerformanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * With pipeline-driven requiredIPH, an empty PACKING department that has pack backlog
 * (high requiredIPH) must pull people from a surplus department.
 */
@ExtendWith(MockitoExtension.class)
class RelocationEnginePackingDemandTest {

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

    private WarehouseProperties warehouseProperties;
    private RelocationEngine relocationEngine;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneId.of("UTC"));

    private Department picking;
    private Department packing;
    private Department putaway;
    private Employee picker;

    @BeforeEach
    void setUp() {
        warehouseProperties = new WarehouseProperties();
        warehouseProperties.getRelocation().setBalanceToleranceIph(10.0);
        warehouseProperties.getRelocation().setMaxMovesPerTick(1);
        warehouseProperties.getRelocation().setCooldownMinutes(60);
        warehouseProperties.getRelocation().setMaxRelocationsPerDay(3);
        warehouseProperties.getRelocation().setTopPerformerSampleSize(3);

        relocationEngine = new RelocationEngine(
                employeeRepository,
                departmentRepository,
                relocationLogRepository,
                notificationService,
                clock,
                employeePerformanceService,
                warehouseProperties
        );

        // Picking overstaffed relative to low pick demand; packing empty with pack backlog demand.
        picking = department(1L, DepartmentName.PICKING, 170.0, 40.0);
        packing = department(2L, DepartmentName.PACKING, 0.0, 120.0);
        putaway = department(3L, DepartmentName.PUTAWAY, 55.0, 40.0);

        picker = new Employee();
        picker.setId(10L);
        picker.setCurrentDepartment(picking);
        picker.setCurrentIph(85.0);
        picker.setOnBreak(false);
        picker.setRelocationCountToday(0);
        picker.setOrderItemsRemaining(0);
    }

    @Test
    void checkAndBalanceWorkforce_movesFromPickingSurplusToEmptyPackingWithBacklogDemand() {
        when(departmentRepository.findAll()).thenReturn(List.of(picking, packing, putaway));
        when(employeeRepository.findByCurrentDepartment(eq(picking))).thenReturn(List.of(picker));
        when(employeeRepository.findByCurrentDepartment(eq(packing))).thenReturn(List.of());
        when(employeePerformanceService.getDefaultIph(DepartmentName.PACKING)).thenReturn(55.0);
        when(employeePerformanceService.predictIphAtDepartment(eq(picker), eq(DepartmentName.PACKING), any()))
                .thenReturn(55.0);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
        when(relocationLogRepository.save(any(RelocationLog.class))).thenAnswer(inv -> {
            RelocationLog log = inv.getArgument(0);
            log.setId(99L);
            return log;
        });

        int scheduled = relocationEngine.checkAndBalanceWorkforce();

        assertThat(scheduled).isEqualTo(1);
        assertThat(picker.getTargetDepartment()).isEqualTo(packing);

        ArgumentCaptor<RelocationLog> logCaptor = ArgumentCaptor.forClass(RelocationLog.class);
        verify(relocationLogRepository).save(logCaptor.capture());
        RelocationLog saved = logCaptor.getValue();
        assertThat(saved.getFromDepartment()).isEqualTo(DepartmentName.PICKING);
        assertThat(saved.getToDepartment()).isEqualTo(DepartmentName.PACKING);
        assertThat(saved.getStatus()).isEqualTo(RelocationLogStatus.SCHEDULED);

        verify(notificationService).notifyRelocationScheduled(
                eq(10L), eq(99L), eq(DepartmentName.PICKING), eq(DepartmentName.PACKING));
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
}
