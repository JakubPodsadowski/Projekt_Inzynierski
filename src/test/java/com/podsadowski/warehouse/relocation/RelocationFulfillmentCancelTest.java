package com.podsadowski.warehouse.relocation;

import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.entity.RelocationLog;
import com.podsadowski.warehouse.domain.entity.RelocationLogStatus;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.domain.repository.RelocationLogRepository;
import com.podsadowski.warehouse.notification.NotificationService;
import com.podsadowski.warehouse.performance.EmployeePerformanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelocationFulfillmentCancelTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private RelocationLogRepository relocationLogRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EmployeePerformanceService employeePerformanceService;
    @Mock
    private WarehouseProperties warehouseProperties;

    private RelocationFulfillmentService fulfillmentService;

    private Employee employee;
    private RelocationLog openLog;

    @BeforeEach
    void setUp() {
        fulfillmentService = new RelocationFulfillmentService(
                employeeRepository,
                relocationLogRepository,
                notificationService,
                employeePerformanceService,
                warehouseProperties
        );

        Department putaway = new Department();
        putaway.setId(2L);
        putaway.setName(DepartmentName.PUTAWAY);

        employee = new Employee();
        employee.setId(5L);
        employee.setTargetDepartment(putaway);
        employee.setRelocatingUntil(LocalDateTime.of(2026, 7, 26, 12, 0));

        openLog = new RelocationLog();
        openLog.setId(10L);
        openLog.setEmployeeId(5L);
        openLog.setFromDepartment(DepartmentName.PICKING);
        openLog.setToDepartment(DepartmentName.PUTAWAY);
        openLog.setStatus(RelocationLogStatus.IN_TRANSIT);
        openLog.setScheduledAt(LocalDateTime.of(2026, 7, 26, 11, 0));
        openLog.setIphAtSource(80);
        openLog.setPredictedIphAtTarget(40);
    }

    @Test
    void cancelOpenRelocation_clearsEmployeeAndNotifies() {
        when(relocationLogRepository.findById(10L)).thenReturn(Optional.of(openLog));
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(relocationLogRepository.save(any(RelocationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RelocationLog result = fulfillmentService.cancelOpenRelocation(10L);

        assertThat(result.getStatus()).isEqualTo(RelocationLogStatus.CANCELLED);
        assertThat(employee.getTargetDepartment()).isNull();
        assertThat(employee.getRelocatingUntil()).isNull();
        verify(notificationService).notifyRelocationCancelled(
                5L, 10L, DepartmentName.PICKING, DepartmentName.PUTAWAY);
    }

    @Test
    void cancelCompletedRelocation_throws() {
        openLog.setStatus(RelocationLogStatus.COMPLETED);
        when(relocationLogRepository.findById(10L)).thenReturn(Optional.of(openLog));

        assertThatThrownBy(() -> fulfillmentService.cancelOpenRelocation(10L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("SCHEDULED or IN_TRANSIT");
    }
}
