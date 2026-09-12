package com.podsadowski.warehouse.simulation;

import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.DepartmentName;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationChaosServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    private SimulationChaosService chaosService;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-04T10:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        chaosService = new SimulationChaosService(employeeRepository, clock);
    }

    @Test
    void surgeOrders_addsItemsToEligibleEmployees() {
        Employee working = employee(1L, 10, null, null);
        Employee relocating = employee(2L, 5, department(DepartmentName.PUTAWAY), null);
        Employee inTransit = employee(3L, 5, department(DepartmentName.PICKING),
                LocalDateTime.of(2026, 9, 4, 12, 0));

        when(employeeRepository.findAll()).thenReturn(List.of(working, relocating, inTransit));

        int affected = chaosService.surgeOrders(500);

        assertThat(affected).isEqualTo(1);
        assertThat(working.getOrderItemsRemaining()).isEqualTo(510);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Employee>> captor = ArgumentCaptor.forClass(List.class);
        verify(employeeRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    private static Employee employee(Long id, int items, Department target, LocalDateTime relocatingUntil) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setOrderItemsRemaining(items);
        employee.setTargetDepartment(target);
        employee.setRelocatingUntil(relocatingUntil);
        return employee;
    }

    private static Department department(DepartmentName name) {
        Department department = new Department();
        department.setId(1L);
        department.setName(name);
        return department;
    }
}
