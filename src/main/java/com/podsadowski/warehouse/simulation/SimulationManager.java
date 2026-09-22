package com.podsadowski.warehouse.simulation;

import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.inbound.InboundPutawayService;
import com.podsadowski.warehouse.order.OutboundOrderService;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Profile("demo")
@Slf4j
@RequiredArgsConstructor
public class SimulationManager implements SimulationControl {

    private final EmployeeRepository employeeRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SimulationTimeService simulationTimeService;
    private final EmployeePerformanceService employeePerformanceService;
    private final RelocationFulfillmentService relocationFulfillmentService;
    private final WarehouseProperties warehouseProperties;
    private final OutboundOrderService outboundOrderService;
    private final InboundPutawayService inboundPutawayService;

    private ExecutorService executor;
    private volatile boolean simulationRunning;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        startSimulation();
    }

    @Override
    public synchronized void startSimulation() {
        if (simulationRunning) {
            return;
        }

        executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Employee> employees = employeeRepository.findAll();

        for (Employee employee : employees) {
            submitTask(employee.getId());
        }

        simulationRunning = true;
        log.info("Started simulation for {} employees", employees.size());
    }

    @Override
    public synchronized void stopSimulation() {
        if (!simulationRunning) {
            return;
        }

        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Simulation executor did not terminate within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for simulation shutdown");
            }
            executor = null;
        }

        simulationRunning = false;
        log.info("Stopped employee simulation");
    }

    @Override
    public boolean isSimulationRunning() {
        return simulationRunning;
    }

    private void submitTask(Long employeeId) {
        EmployeeSimulationTask task = new EmployeeSimulationTask(
                employeeId,
                employeeRepository,
                kafkaTemplate,
                objectMapper,
                clock,
                simulationTimeService,
                employeePerformanceService,
                relocationFulfillmentService,
                warehouseProperties,
                outboundOrderService,
                inboundPutawayService
        );
        executor.submit(task);
    }
}
