package com.podsadowski.warehouse.relocation;

import com.podsadowski.warehouse.api.exception.InvalidRequestException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelocationEngineForceScheduleTest {

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
    @Mock
    private WarehouseProperties warehouseProperties;

    private RelocationEngine relocationEngine;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneId.of("UTC"));

    private Department picking;
    private Department putaway;
    private Employee employee;

    @BeforeEach
    void setUp() {
        relocationEngine = new RelocationEngine(
                employeeRepository,
                departmentRepository,
                relocationLogRepository,
                notificationService,
                clock,
                employeePerformanceService,
                warehouseProperties
        );

        picking = department(1L, DepartmentName.PICKING, 200, 100);
        putaway = department(2L, DepartmentName.PUTAWAY, 50, 150);

        employee = new Employee();
        employee.setId(5L);
        employee.setCurrentDepartment(picking);
        employee.setCurrentIph(80);
        employee.setOnBreak(true);
        employee.setRelocationCountToday(99);
        employee.setLastRelocatedAt(LocalDateTime.now(clock));
        employee.setOrderItemsRemaining(5);
    }

    @Test
    void forceSchedule_skipsCooldownDailyLimitAndBreak() {
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(putaway));
        when(employeePerformanceService.predictIphAtDepartment(eq(employee), eq(DepartmentName.PUTAWAY), any()))
                .thenReturn(40.0);
        when(relocationLogRepository.save(any(RelocationLog.class))).thenAnswer(invocation -> {
            RelocationLog log = invocation.getArgument(0);
            log.setId(10L);
            return log;
        });

        RelocationLog result = relocationEngine.forceSchedule(5L, 2L);

        assertThat(result.getStatus()).isEqualTo(RelocationLogStatus.SCHEDULED);
        assertThat(result.getFromDepartment()).isEqualTo(DepartmentName.PICKING);
        assertThat(result.getToDepartment()).isEqualTo(DepartmentName.PUTAWAY);
        assertThat(employee.getTargetDepartment()).isEqualTo(putaway);

        verify(notificationService).notifyRelocationScheduled(
                5L, 10L, DepartmentName.PICKING, DepartmentName.PUTAWAY);
    }

    @Test
    void forceSchedule_sameDepartment_throws() {
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(picking));

        assertThatThrownBy(() -> relocationEngine.forceSchedule(5L, 1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already in the target department");
    }

    @Test
    void forceSchedule_openRelocation_throws() {
        employee.setTargetDepartment(putaway);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(putaway));

        assertThatThrownBy(() -> relocationEngine.forceSchedule(5L, 2L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("open relocation");
    }

    private static Department department(Long id, DepartmentName name, double current, double required) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        department.setCurrentIPH(current);
        department.setRequiredIPH(required);
        department.setPerformanceMultiplier(1.0);
        return department;
    }
}
