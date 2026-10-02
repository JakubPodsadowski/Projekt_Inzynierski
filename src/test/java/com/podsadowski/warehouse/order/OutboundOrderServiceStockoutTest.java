package com.podsadowski.warehouse.order;

import com.podsadowski.warehouse.activity.WorkActivityService;
import com.podsadowski.warehouse.domain.entity.Employee;
import com.podsadowski.warehouse.domain.entity.OutboundOrder;
import com.podsadowski.warehouse.domain.entity.OutboundOrderLine;
import com.podsadowski.warehouse.domain.entity.OutboundOrderStatus;
import com.podsadowski.warehouse.domain.entity.Product;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import com.podsadowski.warehouse.domain.repository.OutboundOrderRepository;
import com.podsadowski.warehouse.domain.repository.ProductRepository;
import com.podsadowski.warehouse.domain.repository.StockLevelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboundOrderServiceStockoutTest {

    @Mock
    private OutboundOrderRepository outboundOrderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockLevelRepository stockLevelRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private WorkActivityService workActivityService;

    private OutboundOrderService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        service = new OutboundOrderService(
                outboundOrderRepository,
                productRepository,
                stockLevelRepository,
                employeeRepository,
                workActivityService,
                clock
        );
    }

    @Test
    void progressPick_releasesAssignmentWhenNoStockRemains() {
        Product product = new Product();
        product.setId(1L);
        product.setSku("SKU-1");

        OutboundOrderLine line = new OutboundOrderLine();
        line.setId(1L);
        line.setProduct(product);
        line.setQuantityOrdered(5);
        line.setQuantityPicked(2);

        OutboundOrder order = new OutboundOrder();
        order.setId(50L);
        order.setOrderNumber("OUT-1");
        order.setStatus(OutboundOrderStatus.PICKING);
        order.setAssignedEmployeeId(7L);
        order.setCreatedAt(LocalDateTime.now(clock));
        order.addLine(line);

        Employee employee = new Employee();
        employee.setId(7L);
        employee.setOrderItemsRemaining(3);

        when(outboundOrderRepository.findByAssignedEmployeeIdAndStatus(7L, OutboundOrderStatus.PICKING))
                .thenReturn(Optional.of(order));
        when(stockLevelRepository.findByProductId(1L)).thenReturn(List.of());
        when(outboundOrderRepository.save(any(OutboundOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        int processed = service.progressPick(7L, 5);

        assertThat(processed).isEqualTo(0);
        assertThat(order.getStatus()).isEqualTo(OutboundOrderStatus.READY_TO_PICK);
        assertThat(order.getAssignedEmployeeId()).isNull();
        assertThat(employee.getOrderItemsRemaining()).isEqualTo(0);
        verify(outboundOrderRepository).save(order);
    }
}
