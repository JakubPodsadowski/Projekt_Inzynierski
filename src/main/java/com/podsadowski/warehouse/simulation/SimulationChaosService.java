package com.podsadowski.warehouse.simulation;

import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Demo chaos helpers that deliberately unbalance the warehouse for presentations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationChaosService {

    public static final int MIN_SURGE_ITEMS = 1;
    public static final int MAX_SURGE_ITEMS = 2000;
    public static final int DEFAULT_SURGE_ITEMS = 500;

    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    @Transactional
    public int surgeOrders(Integer itemsPerEmployee) {
        int items = itemsPerEmployee != null ? itemsPerEmployee : DEFAULT_SURGE_ITEMS;
        if (items < MIN_SURGE_ITEMS || items > MAX_SURGE_ITEMS) {
            throw new InvalidRequestException(
                    "itemsPerEmployee must be between " + MIN_SURGE_ITEMS + " and " + MAX_SURGE_ITEMS);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<Employee> employees = employeeRepository.findAll();
        int affected = 0;

        for (Employee employee : employees) {
            if (employee.hasPendingRelocation() || employee.isInTransit(now)) {
                continue;
            }
            employee.setOrderItemsRemaining(employee.getOrderItemsRemaining() + items);
            affected++;
        }

        employeeRepository.saveAll(employees);
        log.info("Order surge: +{} items for {} employees", items, affected);
        return affected;
    }
}
