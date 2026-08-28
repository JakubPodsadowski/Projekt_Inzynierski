package com.podsadowski.warehouse.simulation;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.performance.EmployeePerformanceService;
import com.podsadowski.warehouse.relocation.RelocationFulfillmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationManagerTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private SimulationTimeService simulationTimeService;
    @Mock
    private EmployeePerformanceService employeePerformanceService;
    @Mock
    private RelocationFulfillmentService relocationFulfillmentService;
    @Mock
    private WarehouseProperties warehouseProperties;

    private SimulationManager simulationManager;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-28T10:00:00Z"), ZoneId.of("UTC"));
        simulationManager = new SimulationManager(
                employeeRepository,
                kafkaTemplate,
                objectMapper,
                clock,
                simulationTimeService,
                employeePerformanceService,
                relocationFulfillmentService,
                warehouseProperties
        );

        Employee employee = new Employee();
        employee.setId(1L);
        when(employeeRepository.findAll()).thenReturn(List.of(employee));
        when(warehouseProperties.getSimulation()).thenReturn(new WarehouseProperties.Simulation());
        when(warehouseProperties.getKafka()).thenReturn(new WarehouseProperties.Kafka());
    }

    @Test
    void startAndStopSimulation_isIdempotent() {
        assertThat(simulationManager.isSimulationRunning()).isFalse();

        simulationManager.startSimulation();
        assertThat(simulationManager.isSimulationRunning()).isTrue();

        simulationManager.startSimulation();
        assertThat(simulationManager.isSimulationRunning()).isTrue();

        simulationManager.stopSimulation();
        assertThat(simulationManager.isSimulationRunning()).isFalse();

        simulationManager.stopSimulation();
        assertThat(simulationManager.isSimulationRunning()).isFalse();
    }
}
