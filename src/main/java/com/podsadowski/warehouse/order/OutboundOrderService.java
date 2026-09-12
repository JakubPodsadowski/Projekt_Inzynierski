package com.podsadowski.warehouse.order;

import com.podsadowski.warehouse.domain.entity.OutboundOrder;
import com.podsadowski.warehouse.domain.entity.OutboundOrderLine;
import com.podsadowski.warehouse.domain.entity.OutboundOrderStatus;
import com.podsadowski.warehouse.domain.entity.Product;
import com.podsadowski.warehouse.domain.entity.StockLevel;
import com.podsadowski.warehouse.domain.repository.OutboundOrderRepository;
import com.podsadowski.warehouse.domain.repository.ProductRepository;
import com.podsadowski.warehouse.domain.repository.StockLevelRepository;
import com.podsadowski.warehouse.api.exception.InvalidRequestException;
import com.podsadowski.warehouse.api.exception.ResourceNotFoundException;
import com.podsadowski.warehouse.domain.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Creates outbound orders and drives pick → pack queue handoff for simulation/API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundOrderService {

    private static final AtomicLong ORDER_SEQ = new AtomicLong(1);

    private final OutboundOrderRepository outboundOrderRepository;
    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;
    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    public static int computePriorityScore(boolean vip, LocalDateTime dueAt, LocalDateTime createdAt, LocalDateTime now) {
        int score = vip ? 10_000 : 0;
        if (dueAt != null) {
            long minutesUntilDue = ChronoUnit.MINUTES.between(now, dueAt);
            // Closer / overdue → higher urgency (cap contribution)
            score += (int) Math.max(0, 5_000 - minutesUntilDue);
        }
        long ageMinutes = Math.max(0, ChronoUnit.MINUTES.between(createdAt, now));
        score += (int) Math.min(2_000, ageMinutes);
        return score;
    }

    @Transactional
    public OutboundOrder createOrder(List<OrderLineRequest> lines, LocalDateTime dueAt, boolean vip, String externalRef) {
        if (lines == null || lines.isEmpty()) {
            throw new InvalidRequestException("Order must contain at least one line");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        OutboundOrder order = new OutboundOrder();
        order.setOrderNumber(nextOrderNumber(now));
        order.setStatus(OutboundOrderStatus.READY_TO_PICK);
        order.setDueAt(dueAt);
        order.setVip(vip);
        order.setExternalRef(externalRef);
        order.setCreatedAt(now);
        order.setPriorityScore(computePriorityScore(vip, dueAt, now, now));

        for (OrderLineRequest lineRequest : lines) {
            Product product = productRepository.findById(lineRequest.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + lineRequest.productId()));
            if (lineRequest.quantity() <= 0) {
                throw new InvalidRequestException("Line quantity must be positive");
            }
            int available = availableStock(product.getId());
            if (available < lineRequest.quantity()) {
                throw new InvalidRequestException(
                        "Insufficient stock for " + product.getSku() + ": need "
                                + lineRequest.quantity() + ", available " + available);
            }

            OutboundOrderLine line = new OutboundOrderLine();
            line.setProduct(product);
            line.setQuantityOrdered(lineRequest.quantity());
            line.setQuantityPicked(0);
            line.setQuantityPacked(0);
            order.addLine(line);
        }

        OutboundOrder saved = outboundOrderRepository.save(order);
        log.debug("Created outbound order {} ({} items, priority {})",
                saved.getOrderNumber(), saved.totalOrdered(), saved.getPriorityScore());
        return saved;
    }

    @Transactional
    public Optional<OutboundOrder> ensurePickAssignment(Long employeeId) {
        Optional<OutboundOrder> current = outboundOrderRepository
                .findByAssignedEmployeeIdAndStatus(employeeId, OutboundOrderStatus.PICKING);
        if (current.isPresent()) {
            syncEmployeeRemaining(employeeId, current.get().remainingToPick());
            return current;
        }

        List<OutboundOrder> claimable = outboundOrderRepository.findClaimable(
                OutboundOrderStatus.READY_TO_PICK, PageRequest.of(0, 1));
        if (claimable.isEmpty()) {
            syncEmployeeRemaining(employeeId, 0);
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        OutboundOrder order = claimable.getFirst();
        order.setStatus(OutboundOrderStatus.PICKING);
        order.setAssignedEmployeeId(employeeId);
        order.setPickingStartedAt(now);
        order.setPriorityScore(computePriorityScore(order.isVip(), order.getDueAt(), order.getCreatedAt(), now));
        outboundOrderRepository.save(order);
        syncEmployeeRemaining(employeeId, order.remainingToPick());
        log.debug("Employee {} claimed pick order {}", employeeId, order.getOrderNumber());
        return Optional.of(order);
    }

    @Transactional
    public int progressPick(Long employeeId, int items) {
        if (items <= 0) {
            return 0;
        }
        OutboundOrder order = outboundOrderRepository
                .findByAssignedEmployeeIdAndStatus(employeeId, OutboundOrderStatus.PICKING)
                .orElse(null);
        if (order == null) {
            return 0;
        }

        int remainingBudget = items;
        int processed = 0;
        List<OutboundOrderLine> lines = order.getLines().stream()
                .sorted(Comparator.comparingLong(line -> line.getId() == null ? 0L : line.getId()))
                .toList();

        for (OutboundOrderLine line : lines) {
            if (remainingBudget <= 0) {
                break;
            }
            int need = line.remainingToPick();
            if (need <= 0) {
                continue;
            }
            int take = Math.min(need, remainingBudget);
            int deducted = deductStock(line.getProduct().getId(), take);
            if (deducted <= 0) {
                continue;
            }
            line.setQuantityPicked(line.getQuantityPicked() + deducted);
            processed += deducted;
            remainingBudget -= deducted;
        }

        if (order.remainingToPick() == 0) {
            completePicking(order);
            syncEmployeeRemaining(employeeId, 0);
        } else {
            outboundOrderRepository.save(order);
            syncEmployeeRemaining(employeeId, order.remainingToPick());
        }
        return processed;
    }

    @Transactional
    public Optional<OutboundOrder> ensurePackAssignment(Long employeeId) {
        Optional<OutboundOrder> current = outboundOrderRepository
                .findByAssignedEmployeeIdAndStatus(employeeId, OutboundOrderStatus.PACKING);
        if (current.isPresent()) {
            syncEmployeeRemaining(employeeId, current.get().remainingToPack());
            return current;
        }

        List<OutboundOrder> claimable = outboundOrderRepository.findClaimable(
                OutboundOrderStatus.READY_TO_PACK, PageRequest.of(0, 1));
        if (claimable.isEmpty()) {
            syncEmployeeRemaining(employeeId, 0);
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        OutboundOrder order = claimable.getFirst();
        order.setStatus(OutboundOrderStatus.PACKING);
        order.setAssignedEmployeeId(employeeId);
        order.setPackingStartedAt(now);
        outboundOrderRepository.save(order);
        syncEmployeeRemaining(employeeId, order.remainingToPack());
        log.debug("Employee {} claimed pack order {}", employeeId, order.getOrderNumber());
        return Optional.of(order);
    }

    @Transactional
    public int progressPack(Long employeeId, int items) {
        if (items <= 0) {
            return 0;
        }
        OutboundOrder order = outboundOrderRepository
                .findByAssignedEmployeeIdAndStatus(employeeId, OutboundOrderStatus.PACKING)
                .orElse(null);
        if (order == null) {
            return 0;
        }

        int remainingBudget = items;
        int processed = 0;
        for (OutboundOrderLine line : order.getLines()) {
            if (remainingBudget <= 0) {
                break;
            }
            int need = line.remainingToPack();
            if (need <= 0) {
                continue;
            }
            int take = Math.min(need, remainingBudget);
            line.setQuantityPacked(line.getQuantityPacked() + take);
            processed += take;
            remainingBudget -= take;
        }

        if (order.remainingToPack() == 0) {
            order.setStatus(OutboundOrderStatus.PACKED);
            order.setPackingCompletedAt(LocalDateTime.now(clock));
            order.setAssignedEmployeeId(null);
            outboundOrderRepository.save(order);
            syncEmployeeRemaining(employeeId, 0);
            log.debug("Order {} packed", order.getOrderNumber());
        } else {
            outboundOrderRepository.save(order);
            syncEmployeeRemaining(employeeId, order.remainingToPack());
        }
        return processed;
    }

    public long countReadyToPick() {
        return outboundOrderRepository.countByStatus(OutboundOrderStatus.READY_TO_PICK);
    }

    public long countReadyToPack() {
        return outboundOrderRepository.countByStatus(OutboundOrderStatus.READY_TO_PACK);
    }

    private void completePicking(OutboundOrder order) {
        order.setStatus(OutboundOrderStatus.READY_TO_PACK);
        order.setPickingCompletedAt(LocalDateTime.now(clock));
        order.setAssignedEmployeeId(null);
        outboundOrderRepository.save(order);
        log.debug("Order {} moved to pack queue", order.getOrderNumber());
    }

    private void syncEmployeeRemaining(Long employeeId, int remaining) {
        employeeRepository.findById(employeeId).ifPresent(employee -> {
            employee.setOrderItemsRemaining(Math.max(0, remaining));
            employeeRepository.save(employee);
        });
    }

    private int availableStock(Long productId) {
        return stockLevelRepository.findByProductId(productId).stream()
                .mapToInt(StockLevel::getQuantity)
                .sum();
    }

    private int deductStock(Long productId, int quantity) {
        List<StockLevel> levels = stockLevelRepository.findByProductId(productId).stream()
                .sorted(Comparator.comparingInt(StockLevel::getQuantity).reversed())
                .toList();
        int left = quantity;
        int deducted = 0;
        for (StockLevel level : levels) {
            if (left <= 0) {
                break;
            }
            int take = Math.min(level.getQuantity(), left);
            if (take <= 0) {
                continue;
            }
            level.setQuantity(level.getQuantity() - take);
            stockLevelRepository.save(level);
            left -= take;
            deducted += take;
        }
        return deducted;
    }

    private String nextOrderNumber(LocalDateTime now) {
        return "OUT-" + now.toLocalDate() + "-" + ORDER_SEQ.getAndIncrement();
    }

    public record OrderLineRequest(long productId, int quantity) {
    }
}
