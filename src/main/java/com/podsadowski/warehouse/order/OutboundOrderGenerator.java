package com.podsadowski.warehouse.order;

import com.podsadowski.warehouse.config.DataSourceModeHolder;
import com.podsadowski.warehouse.config.WarehouseProperties;
import com.podsadowski.warehouse.domain.entity.Product;
import com.podsadowski.warehouse.domain.entity.StockLevel;
import com.podsadowski.warehouse.domain.repository.ProductRepository;
import com.podsadowski.warehouse.domain.repository.StockLevelRepository;
import com.podsadowski.warehouse.simulation.SimulationRuntimeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Demo generator: periodically creates outbound orders from products that have stock.
 * Safe outside demo profile — ticks no-op when mode is not DEMO.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboundOrderGenerator {

    private final OutboundOrderService outboundOrderService;
    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;
    private final DataSourceModeHolder modeHolder;
    private final SimulationRuntimeState simulationRuntimeState;
    private final WarehouseProperties warehouseProperties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${warehouse.simulation.order-generate-interval-ms:4000}")
    public void generateTick() {
        if (!modeHolder.isDemo() || simulationRuntimeState.isPaused()) {
            return;
        }
        int maxOpen = warehouseProperties.getSimulation().getMaxOpenPickOrders();
        if (outboundOrderService.countReadyToPick() >= maxOpen) {
            return;
        }
        try {
            int created = createRandomOrders(1);
            if (created > 0) {
                log.debug("Generated {} outbound order(s)", created);
            }
        } catch (Exception e) {
            log.warn("Outbound order generation skipped: {}", e.getMessage());
        }
    }

    /**
     * Creates up to {@code count} random orders. Used by chaos surge and bootstrap seed.
     *
     * @return number of orders actually created
     */
    public int createRandomOrders(int count) {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return 0;
        }

        int created = 0;
        LocalDateTime now = LocalDateTime.now(clock);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            List<OutboundOrderService.OrderLineRequest> lines = buildLines(products, random);
            if (lines.isEmpty()) {
                break;
            }
            boolean vip = random.nextDouble() < 0.15;
            LocalDateTime dueAt = now.plusMinutes(30 + random.nextInt(180));
            try {
                outboundOrderService.createOrder(lines, dueAt, vip, "demo-gen");
                created++;
            } catch (Exception e) {
                log.debug("Could not create demo order: {}", e.getMessage());
                break;
            }
        }
        return created;
    }

    /**
     * Chaos helper: keep creating orders until roughly {@code targetItems} line units are queued
     * or stock runs out.
     */
    public SurgeResult surgeItems(int targetItems) {
        int remaining = targetItems;
        int totalItems = 0;
        int ordersCreated = 0;
        int guard = 0;
        while (remaining > 0 && guard++ < 200) {
            List<Product> products = productRepository.findAll();
            List<OutboundOrderService.OrderLineRequest> lines = buildLinesForBudget(products, remaining);
            if (lines.isEmpty()) {
                break;
            }
            int orderItems = lines.stream().mapToInt(OutboundOrderService.OrderLineRequest::quantity).sum();
            boolean vip = ThreadLocalRandom.current().nextDouble() < 0.2;
            LocalDateTime dueAt = LocalDateTime.now(clock).plusMinutes(15 + ThreadLocalRandom.current().nextInt(60));
            try {
                outboundOrderService.createOrder(lines, dueAt, vip, "chaos-surge");
                totalItems += orderItems;
                ordersCreated++;
                remaining -= orderItems;
            } catch (Exception e) {
                log.debug("Chaos order create failed: {}", e.getMessage());
                break;
            }
        }
        return new SurgeResult(totalItems, ordersCreated);
    }

    public record SurgeResult(int itemsCreated, int ordersCreated) {
    }

    private List<OutboundOrderService.OrderLineRequest> buildLines(List<Product> products, ThreadLocalRandom random) {
        int lineCount = 1 + random.nextInt(3);
        List<OutboundOrderService.OrderLineRequest> lines = new ArrayList<>();
        List<Product> shuffled = new ArrayList<>(products);
        java.util.Collections.shuffle(shuffled, random);

        for (Product product : shuffled) {
            if (lines.size() >= lineCount) {
                break;
            }
            int available = stockLevelRepository.findByProductId(product.getId()).stream()
                    .mapToInt(StockLevel::getQuantity)
                    .sum();
            if (available < 1) {
                continue;
            }
            int qty = Math.min(available, 1 + random.nextInt(5));
            lines.add(new OutboundOrderService.OrderLineRequest(product.getId(), qty));
        }
        return lines;
    }

    private List<OutboundOrderService.OrderLineRequest> buildLinesForBudget(List<Product> products, int budget) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<OutboundOrderService.OrderLineRequest> lines = new ArrayList<>();
        List<Product> shuffled = new ArrayList<>(products);
        java.util.Collections.shuffle(shuffled, random);
        int left = budget;

        for (Product product : shuffled) {
            if (left <= 0 || lines.size() >= 3) {
                break;
            }
            int available = stockLevelRepository.findByProductId(product.getId()).stream()
                    .mapToInt(StockLevel::getQuantity)
                    .sum();
            if (available < 1) {
                continue;
            }
            int qty = Math.min(left, Math.min(available, 1 + random.nextInt(8)));
            lines.add(new OutboundOrderService.OrderLineRequest(product.getId(), qty));
            left -= qty;
        }
        return lines;
    }
}
