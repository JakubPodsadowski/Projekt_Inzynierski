package com.podsadowski.warehouse.simulation;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.performance.EmployeePerformanceService;
import com.podsadowski.warehouse.relocation.RelocationFulfillmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executors;

@Component
@Profile("demo")
@Slf4j
@RequiredArgsConstructor
public class SimulationManager {

    private final EmployeeRepository employeeRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SimulationTimeService simulationTimeService;
    private final EmployeePerformanceService employeePerformanceService;
    private final RelocationFulfillmentService relocationFulfillmentService;
    private final WarehouseProperties warehouseProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void startSimulation() {
        var executor = Executors.newVirtualThreadPerTaskExecutor();

        List<Employee> employees = employeeRepository.findAll();

        for (Employee employee : employees) {
            EmployeeSimulationTask task = new EmployeeSimulationTask(
                    employee.getId(),
                    employeeRepository,
                    kafkaTemplate,
                    objectMapper,
                    clock,
                    simulationTimeService,
                    employeePerformanceService,
                    relocationFulfillmentService,
                    warehouseProperties
            );

            executor.submit(task);
        }

        log.info("Started simulation for {} employees", employees.size());
    }
}
